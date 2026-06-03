package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.ParaStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 추천/리마인드 스코어링의 순수 함수 모음.
 * REMIND_STRATEGY §2.2 의 가중치 합산, PARA 정책, 종 모양 감쇠, 자격 검증을 담당.
 *
 * 외부 의존(DB, OpenAI 등)이 없으므로 단위 테스트하기 좋다.
 *
 * 정리 이력:
 *   - frequency / lastUpdate / legacyRecency / weightValue 캐시 폐기
 *   - today 모드에서 isRead=true 제외 (문서 §2.3 "다시 볼 시간 = 미열람")
 *   - bell 기준점 항상 createdAt 로 단일화 (문서 §2.2 "저장 후 경과일")
 *   - similarity 항 완전 제거 (문서 범위 밖, OpenAI 의존 무효화)
 *   - 최종 가중치: unread 0.45 / importance 0.30 / bell 0.25 (총합 1.00)
 */
@Component
public class ScoringEngine {

    // === REMIND_STRATEGY §2.2 가중치 (paraMultiplier가 따로 곱해짐) ===
    // similarity 항은 문서 범위 밖이라 제거 (2026-05-21). 0.15 → unread 강화로 재분배.
    // 가중치 합 = 1.00 유지.
    public static final double W_UNREAD = 0.45;
    public static final double W_IMPORTANCE = 0.30;
    public static final double W_BELL_RECENCY = 0.25;

    /** 최소 노출 점수 — 이 미만은 후보에서 제외 (Archives + 너무 동떨어진 것 컷) */
    public static final double REMIND_SCORE_CUTOFF = 0.05;

    /** 피로도 (REMIND §5.5 빈도 상한): 최근 7일간 발송 횟수당 -0.25, 4회면 0 */
    public static final int FATIGUE_WINDOW_DAYS = 7;
    public static final double FATIGUE_PENALTY_PER_HIT = 0.25;

    // ============================================================
    //  Public API
    // ============================================================

    /** PARA 상태에 대응하는 정책(곱셈/종모양 peak·sigma/자격) */
    public ParaPolicy paraPolicyOf(ParaStatus paraStatus) {
        ParaStatus value = paraStatus == null ? ParaStatus.UNSPECIFIED : paraStatus;
        return switch (value) {
            case PROJECT ->
                    new ParaPolicy(ParaCategory.PROJECT, true, 1.0, 2, 2, "Projects");
            case AREA ->
                    new ParaPolicy(ParaCategory.AREA, true, 0.85, 7, 5, "Areas");
            case RESOURCE ->
                    new ParaPolicy(ParaCategory.RESOURCE, true, 0.65, 21, 10, "Resources");
            case ARCHIVE ->
                    new ParaPolicy(ParaCategory.ARCHIVE, false, 0.35, 60, 30, "Archives");
            // REMIND §2.1: 미지정은 Resources 와 동일 정책. multiplier/peak/sigma 모두 통일.
            case UNSPECIFIED ->
                    new ParaPolicy(ParaCategory.UNSPECIFIED, true, 0.65, 21, 10, "미지정");
        };
    }

    /** PARA 카테고리별 후보 자격 (REMIND §2.1) */
    public boolean paraEligibility(LinkData link, ParaPolicy policy, LocalDateTime now) {
        long ageSinceLastTouch = ageDays(latestTouch(link), now);
        long ageSinceCreated = ageDays(link.getCreatedAt(), now);
        return switch (policy.category()) {
            case PROJECT -> !link.isRead() || ageSinceLastTouch >= 7;
            // REMIND §2.1 "Areas — 주 1회 랜덤 샘플링" → 임계값 7일로 정정
            case AREA -> !link.isRead() || ageSinceLastTouch >= 7;
            case RESOURCE, UNSPECIFIED -> !link.isRead() && ageSinceCreated >= 14;
            case ARCHIVE -> false;
        };
    }

    /**
     * 모드별 추가 필터 — eligibility 다음 단계.
     *
     * today 는 문서 §2.3 "다시 볼 시간 = 미열람 + 경과일 최적"이므로 isRead=true 를 명시적으로 컷한다.
     * youtube_ctx / domain_ctx 는 호스트 매칭을 호출 측(ReminderCandidateService)에서 처리하고,
     * 여기서는 미열람 여부만 본다. 같은 컨텍스트의 "내가 저장한 영상" 을 재노출하는 게 목적.
     */
    public boolean modeMatches(String mode, LinkData link, ParaPolicy policy, LocalDateTime now) {
        return switch (mode) {
            case "unread" -> !link.isRead();
            case "priority" -> !link.isRead();
            case "resurface" -> {
                if (link.isRead()) yield false;
                if (policy.category() != ParaCategory.RESOURCE
                        && policy.category() != ParaCategory.UNSPECIFIED) {
                    yield false;
                }
                long ageDays = ageDays(link.getCreatedAt(), now);
                yield ageDays >= 30;
            }
            case "youtube_ctx", "domain_ctx" -> !link.isRead();
            default -> !link.isRead(); // "today" — 미열람만
        };
    }

    /**
     * REMIND_STRATEGY §2.2 스코어링. recentReminds 는 외부에서 묶음 쿼리로 미리 계산.
     *
     * score = paraMult × (
     *           W_UNREAD       · unread (0|1)
     *         + W_IMPORTANCE   · importance (0~1)
     *         + W_BELL_RECENCY · bellRecency (0~1, 저장 후 경과일 기반 종형)
     *       ) × fatigueFactor (0~1)
     *
     * 가중치 합 = 0.45 + 0.30 + 0.25 = 1.00
     */
    public ScoringResult scoreRemind(
            RecommendationWeight rw,
            LinkData link,
            ParaPolicy policy,
            LocalDateTime now,
            int recentReminds
    ) {
        double importance = clamp(rw.getImportance(), 0, 1);
        double unread = link.isRead() ? 0.0 : 1.0;

        // 문서 §2.2 "저장 후 경과일" — 항상 createdAt 기준
        long ageDays = ageDays(link.getCreatedAt(), now);
        double bell = bellRecency(ageDays, policy);

        double fatigue = Math.max(0.0, 1.0 - FATIGUE_PENALTY_PER_HIT * recentReminds);

        double raw =
                W_UNREAD * unread
                        + W_IMPORTANCE * importance
                        + W_BELL_RECENCY * bell;

        double scored = policy.multiplier() * raw * fatigue;
        double rounded = Math.round(scored * 10000.0) / 10000.0;

        RemindCandidateResponse.ScoreBreakdown breakdown = new RemindCandidateResponse.ScoreBreakdown(
                policy.multiplier(),
                unread,
                importance,
                Math.round(bell * 10000.0) / 10000.0,
                fatigue,
                Math.round(raw * 10000.0) / 10000.0,
                ageDays,
                recentReminds
        );
        return new ScoringResult(rounded, breakdown);
    }

    /** "왜 보여주는가" 라벨 (REMIND §5.2) */
    public String buildReason(LinkData link, ScoringResult s, ParaPolicy policy) {
        String paraLabel = policy.label();
        String readState = link.isRead() ? "재방문 추천" : "미열람";
        long ageDays = s.breakdown().ageDays();
        String ageText = ageDays == 0 ? "오늘 저장" : ageDays + "일 전 저장";
        if (link.isRead() && link.getReadAt() != null) {
            long sinceRead = ageDays(link.getReadAt(), LocalDateTime.now());
            ageText = sinceRead == 0 ? "오늘 읽음" : sinceRead + "일 전 읽음";
        }
        return paraLabel + " · " + readState + " · " + ageText;
    }

    // ============================================================
    //  Helpers (public for testability)
    // ============================================================

    public LocalDateTime latestTouch(LinkData link) {
        LocalDateTime t = link.getReadAt();
        if (t == null) t = link.getCreatedAt();
        if (t == null) t = link.getLastUpdate();
        return t;
    }

    public double bellRecency(long ageDays, ParaPolicy policy) {
        double diff = ageDays - policy.peakDays();
        return Math.exp(-(diff * diff) / (2.0 * policy.sigmaDays() * policy.sigmaDays()));
    }

    public long ageDays(LocalDateTime from, LocalDateTime to) {
        if (from == null) return 0;
        long secs = Duration.between(from, to).getSeconds();
        return Math.max(0, secs / 86400L);
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // ============================================================
    //  Public records (서비스/응답에서 공유)
    // ============================================================

    public enum ParaCategory {
        PROJECT, AREA, RESOURCE, ARCHIVE, UNSPECIFIED
    }

    public record ParaPolicy(
            ParaCategory category,
            boolean eligible,
            double multiplier,
            double peakDays,
            double sigmaDays,
            String label
    ) {
    }

    public record ScoringResult(double score, RemindCandidateResponse.ScoreBreakdown breakdown) {
    }
}
