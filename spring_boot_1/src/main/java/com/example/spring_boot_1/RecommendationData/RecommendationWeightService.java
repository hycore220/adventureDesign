package com.example.spring_boot_1.RecommendationData;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.LinkDataRepository;
import com.example.spring_boot_1.LinkData.ParaStatus;
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import com.example.spring_boot_1.config.SecurityUtil;

/**
 * RecommendationWeight 의 CRUD.
 *
 * 점수 계산은 {@link ScoringEngine} 에,
 * "오늘 다시 볼 링크" / top3 후보는 {@link ReminderCandidateService} 에 위임.
 *
 * 정리 이력:
 *   - weightValue / frequency / lastUpdate / similarity / embedding* 모두 폐기
 *   - getTop3BookmarksByUserId 는 ReminderCandidateService 로 위임
 *   - dirty 플래그 폐기 (OpenAI 의존성 제거에 따라 무효화)
 */
@Service
@Transactional
public class RecommendationWeightService {

    private final RecommendationWeightRepository recommendationWeightRepository;
    private final UserDataRepository userDataRepository;
    private final LinkDataRepository linkDataRepository;
    private final ReminderCandidateService reminderCandidateService;

    public RecommendationWeightService(
            RecommendationWeightRepository recommendationWeightRepository,
            UserDataRepository userDataRepository,
            LinkDataRepository linkDataRepository,
            ReminderCandidateService reminderCandidateService
    ) {
        this.recommendationWeightRepository = recommendationWeightRepository;
        this.userDataRepository = userDataRepository;
        this.linkDataRepository = linkDataRepository;
        this.reminderCandidateService = reminderCandidateService;
    }

    // ============================================================
    //  Create / Read / Update / Delete
    // ============================================================

    /** 화이트리스트 DTO 기반 생성/upsert. 같은 bookmark에 이미 존재하면 갱신. */
    public RecommendationWeight create(RecommendationCreateRequest request) {
        if (request.bookmarkId() == null) {
            throw new IllegalArgumentException("추천 가중치는 bookmarkId가 필요합니다.");
        }
        LinkData bookmark = linkDataRepository.findById(request.bookmarkId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 bookmark.id입니다."));
        requireBookmarkOwner(bookmark);

        RecommendationWeight saved = recommendationWeightRepository
                .findFirstByBookmarkId(bookmark.getId())
                .map(existing -> applyCreateFields(existing, request))
                .orElseGet(() -> {
                    RecommendationWeight fresh = new RecommendationWeight();
                    fresh.setBookmark(bookmark);
                    return applyCreateFields(fresh, request);
                });
        return recommendationWeightRepository.save(saved);
    }

    private RecommendationWeight applyCreateFields(RecommendationWeight target, RecommendationCreateRequest req) {
        target.setName(req.name() == null ? "" : req.name());
        if (req.importance() != null) target.setImportance(req.importance());
        // create는 항상 명시적 — 안 보내면 음소거 해제 상태로 시작
        target.setSnoozedUntil(req.snoozedUntil());
        return target;
    }

    /**
     * 링크 저장 직후 호출 — 이미 가중치가 있으면 그대로 두고, 없으면 default 행을 만든다.
     * REMIND_STRATEGY 후보 산출 진입점이 가중치 행 존재를 전제로 하기 때문에
     * 모든 링크가 저장과 동시에 후보 자격을 갖도록 보장한다.
     * 호출 컨텍스트: 인증된 사용자가 자기 링크를 막 만든 직후 (소유권 검증 생략).
     */
    public RecommendationWeight ensureForLink(LinkData link) {
        return recommendationWeightRepository.findFirstByBookmarkId(link.getId())
                .orElseGet(() -> {
                    RecommendationWeight fresh = new RecommendationWeight();
                    fresh.setBookmark(link);
                    fresh.setName("");
                    fresh.setImportance(0.5); // 균등 시작점 — 사용자가 명시적으로 끌어올리면 가산
                    return recommendationWeightRepository.save(fresh);
                });
    }

    /** 현재 사용자의 가중치만 반환. */
    public List<RecommendationWeight> getAll() {
        return recommendationWeightRepository.findByBookmarkUserDataId(SecurityUtil.currentUserId());
    }

    public RecommendationWeight getById(int id) {
        RecommendationWeight rw = recommendationWeightRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("없는 추천 가중치 id입니다."));
        requireWeightOwner(rw);
        return rw;
    }

    public List<RecommendationWeight> getByUserName(String userName) {
        SecurityUtil.requireOwnerByName(userName);
        return recommendationWeightRepository.findByBookmarkUserDataUserName(userName);
    }

    public List<RecommendationWeight> getByUserId(int userId) {
        SecurityUtil.requireOwnerById(userId);
        return recommendationWeightRepository.findByBookmarkUserDataId(userId);
    }

    /** paraStatus 필터를 현재 사용자 데이터로 한정. */
    public List<RecommendationWeight> getByParaStatus(String paraStatus) {
        int userId = SecurityUtil.currentUserId();
        ParaStatus para = ParaStatus.fromString(paraStatus);
        return recommendationWeightRepository.findByBookmarkParaStatus(para).stream()
                .filter(rw -> rw.getBookmark() != null
                        && rw.getBookmark().getUserData() != null
                        && rw.getBookmark().getUserData().getId() == userId)
                .toList();
    }

    public List<RecommendationWeight> getByBookmarkId(int bookmarkId) {
        List<RecommendationWeight> weights = recommendationWeightRepository.findByBookmarkId(bookmarkId);
        weights.forEach(this::requireWeightOwner);
        return weights;
    }

    /**
     * "사용자 상위 북마크 3개" — REMIND_STRATEGY 와 동일 알고리즘(today 모드)으로 일원화.
     * scoreRemind 가 매 호출 live 계산.
     */
    public List<LinkData> getTop3BookmarksByUserId(int userId) {
        SecurityUtil.requireOwnerById(userId);
        UserData userData = userDataRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저 id입니다."));
        return reminderCandidateService.getCandidates(userData.getUserName(), "today", 3).stream()
                .map(c -> linkDataRepository.findById(c.linkId()).orElse(null))
                .filter(l -> l != null)
                .toList();
    }

    /**
     * 화이트리스트 DTO 기반 업데이트. 보낸 필드만 갱신.
     *
     * snooze 처리 규칙:
     *   - snoozedUntil != null: 그 값으로 세팅
     *   - snoozedUntil == null && snoozeClear == true: 음소거 해제 (null로 명시)
     *   - 둘 다 null: 기존 값 유지
     */
    public RecommendationWeight update(int id, RecommendationUpdateRequest request) {
        RecommendationWeight rw = getById(id);
        if (request.name() != null) rw.setName(request.name());
        if (request.importance() != null) rw.setImportance(request.importance());
        if (request.snoozedUntil() != null) {
            rw.setSnoozedUntil(request.snoozedUntil());
        } else if (Boolean.TRUE.equals(request.snoozeClear())) {
            rw.setSnoozedUntil(null);
        }
        return recommendationWeightRepository.save(rw);
    }

    public void delete(int id) {
        RecommendationWeight rw = getById(id);
        recommendationWeightRepository.delete(rw);
    }

    /** 내부 호출용 — LinkDataService.delete가 호출. 소유자 검증은 호출 측 책임. */
    public void deleteByBookmarkId(int bookmarkId) {
        recommendationWeightRepository.findFirstByBookmarkId(bookmarkId)
                .ifPresent(recommendationWeightRepository::delete);
    }

    // ============================================================
    //  소유권 검증 헬퍼
    // ============================================================

    private void requireWeightOwner(RecommendationWeight rw) {
        if (rw.getBookmark() == null
                || rw.getBookmark().getUserData() == null
                || rw.getBookmark().getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 추천 가중치의 소유자가 아닙니다.");
        }
    }

    private void requireBookmarkOwner(LinkData bookmark) {
        if (bookmark.getUserData() == null
                || bookmark.getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 링크의 소유자가 아닙니다.");
        }
    }
}
