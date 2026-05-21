package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.ReminderData.LinkReminderRepository;
import com.example.spring_boot_1.config.SecurityUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "오늘 다시 볼 링크" 후보 산출 — REMIND_STRATEGY §2 ~ §5.
 *
 * 점수 계산은 {@link ScoringEngine}에 위임하고, 이 서비스는 DB 조회·필터·정렬·
 * 묶음 카운트(N+1 회피) 흐름만 책임진다.
 */
@Service
@Transactional(readOnly = true)
public class ReminderCandidateService {

    private final RecommendationWeightRepository recommendationWeightRepository;
    private final LinkReminderRepository linkReminderRepository;
    private final ScoringEngine scoringEngine;

    public ReminderCandidateService(
            RecommendationWeightRepository recommendationWeightRepository,
            LinkReminderRepository linkReminderRepository,
            ScoringEngine scoringEngine
    ) {
        this.recommendationWeightRepository = recommendationWeightRepository;
        this.linkReminderRepository = linkReminderRepository;
        this.scoringEngine = scoringEngine;
    }

    /** 호스트 파라미터가 없는 기본 호출. 기존 호출부 호환. */
    public List<RemindCandidateResponse> getCandidates(String userName, String mode, int limit) {
        return getCandidates(userName, mode, null, limit);
    }

    /**
     * 사용자별 리마인드 후보를 모드에 따라 산출.
     *
     * @param userName 대상 사용자 (인증된 본인과 일치해야 함)
     * @param mode     "today" | "priority" | "resurface" | "unread" | "youtube_ctx" | "domain_ctx"
     * @param host     컨텍스트 매칭 모드일 때 사용자가 보고 있는 호스트 (예: youtube.com).
     *                 일반 모드면 null.
     * @param limit    상위 N개
     */
    public List<RemindCandidateResponse> getCandidates(String userName, String mode, String host, int limit) {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("userName이 필요합니다.");
        }
        SecurityUtil.requireOwnerByName(userName);

        String normalizedMode = normalizeMode(mode);
        String normalizedHost = normalizeHost(normalizedMode, host);
        int n = limit <= 0 ? 5 : limit;
        LocalDateTime now = LocalDateTime.now();
        int userId = SecurityUtil.currentUserId();

        List<RecommendationWeight> weights =
                recommendationWeightRepository.findByBookmarkUserDataUserName(userName);

        // N+1 회피: 최근 7일 리마인드 카운트를 한 번에 조회해 in-memory lookup
        Map<Integer, Integer> recentRemindCounts = loadRecentRemindCounts(userId, now);

        List<RemindCandidateResponse> candidates = new ArrayList<>();
        for (RecommendationWeight rw : weights) {
            LinkData link = rw.getBookmark();
            if (link == null) continue;
            if (rw.isSnoozed()) continue;

            // 컨텍스트 매칭 (REMIND_STRATEGY §3.3) — 호스트 일치만 통과
            if (normalizedHost != null && !normalizedHost.equals(link.getHost())) continue;

            ScoringEngine.ParaPolicy policy = scoringEngine.paraPolicyOf(link.getPARAStatus());
            if (!policy.eligible()) continue;
            if (!scoringEngine.modeMatches(normalizedMode, link, policy, now)) continue;
            // 컨텍스트 매칭 모드는 paraEligibility 의 시간 조건을 건너뜀
            // (호스트 일치 + 미열람 만으로 후보 자격)
            if (!isContextMode(normalizedMode) && !scoringEngine.paraEligibility(link, policy, now)) continue;

            int recentCount = recentRemindCounts.getOrDefault(link.getId(), 0);
            ScoringEngine.ScoringResult scoring = scoringEngine.scoreRemind(rw, link, policy, now, recentCount);
            if (scoring.score() < ScoringEngine.REMIND_SCORE_CUTOFF) continue;

            String reason = scoringEngine.buildReason(link, scoring, policy);
            candidates.add(RemindCandidateResponse.of(
                    link,
                    normalizedMode,
                    reason,
                    scoring.score(),
                    scoring.breakdown()
            ));
        }

        candidates.sort(Comparator.comparingDouble(RemindCandidateResponse::remindScore).reversed());
        return candidates.size() > n ? candidates.subList(0, n) : candidates;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return "today";
        String value = mode.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "today", "priority", "resurface", "unread", "youtube_ctx", "domain_ctx" -> value;
            default -> "today";
        };
    }

    /**
     * 컨텍스트 모드일 때 호스트 매칭. 두 모드 모두 host 파라미터 필수.
     * 일반 모드(today/priority/...)는 null 반환 → 호스트 필터 비활성.
     */
    private String normalizeHost(String mode, String host) {
        if (!isContextMode(mode)) return null;
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(mode + " 모드는 host 파라미터가 필수입니다.");
        }
        return host.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isContextMode(String mode) {
        return "youtube_ctx".equals(mode) || "domain_ctx".equals(mode);
    }

    private Map<Integer, Integer> loadRecentRemindCounts(int userId, LocalDateTime now) {
        LocalDateTime since = now.minusDays(ScoringEngine.FATIGUE_WINDOW_DAYS);
        List<Object[]> rows = linkReminderRepository.countRecentByUserGroupedByLink(userId, since);
        Map<Integer, Integer> result = new HashMap<>(rows.size() * 2);
        for (Object[] row : rows) {
            int linkId = ((Number) row[0]).intValue();
            int count = ((Number) row[1]).intValue();
            result.put(linkId, count);
        }
        return result;
    }
}
