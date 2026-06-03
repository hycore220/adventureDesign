package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.LinkData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/recommendation-weights")
public class RecommendationWeightController {

    private final RecommendationWeightService recommendationWeightService;
    private final ReminderCandidateService reminderCandidateService;

    public RecommendationWeightController(
            RecommendationWeightService recommendationWeightService,
            ReminderCandidateService reminderCandidateService
    ) {
        this.recommendationWeightService = recommendationWeightService;
        this.reminderCandidateService = reminderCandidateService;
    }

    // ============================================================
    //  CRUD
    // ============================================================

    @PostMapping
    public ResponseEntity<RecommendationWeight> create(@RequestBody RecommendationCreateRequest request) {
        return ResponseEntity.ok(recommendationWeightService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<RecommendationWeight>> getAll(
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String paraStatus,
            @RequestParam(required = false) Integer bookmarkId
    ) {
        if (userId != null) return ResponseEntity.ok(recommendationWeightService.getByUserId(userId));
        if (userName != null) return ResponseEntity.ok(recommendationWeightService.getByUserName(userName));
        if (paraStatus != null) return ResponseEntity.ok(recommendationWeightService.getByParaStatus(paraStatus));
        if (bookmarkId != null) return ResponseEntity.ok(recommendationWeightService.getByBookmarkId(bookmarkId));
        return ResponseEntity.ok(recommendationWeightService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecommendationWeight> getById(@PathVariable int id) {
        return ResponseEntity.ok(recommendationWeightService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecommendationWeight> update(
            @PathVariable int id,
            @RequestBody RecommendationUpdateRequest request
    ) {
        return ResponseEntity.ok(recommendationWeightService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable int id) {
        recommendationWeightService.delete(id);
        return ResponseEntity.ok("삭제 완료");
    }

    // ============================================================
    //  사용자 노출용 — 리마인드 후보 / 상위 북마크
    //  (임베딩/유사도/관심사 벡터 엔드포인트는 OpenAI 의존성 제거에 따라 폐기)
    // ============================================================

    @GetMapping("/users/{userId}/top-bookmarks")
    public ResponseEntity<List<LinkData>> getTop3BookmarksByUserId(@PathVariable int userId) {
        return ResponseEntity.ok(recommendationWeightService.getTop3BookmarksByUserId(userId));
    }

    /**
     * "오늘 다시 볼 링크" — REMIND_STRATEGY 기반 스코어링.
     * mode: today (기본) | priority | resurface | unread | youtube_ctx | domain_ctx
     * host: domain_ctx 모드 필수 (같은 도메인 비교). youtube_ctx 는 content_type(YOUTUBE)
     *       기준으로 매칭하므로 host 선택 — 익스텐션이 활성 탭 호스트를 전달 (예: youtube.com)
     */
    @GetMapping("/users/{userName}/today")
    public ResponseEntity<List<RemindCandidateResponse>> getRemindCandidates(
            @PathVariable String userName,
            @RequestParam(required = false, defaultValue = "today") String mode,
            @RequestParam(required = false) String host,
            @RequestParam(required = false, defaultValue = "5") int limit
    ) {
        return ResponseEntity.ok(reminderCandidateService.getCandidates(userName, mode, host, limit));
    }
}
