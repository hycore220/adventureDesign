package com.example.spring_boot_1.PushData;

import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import com.example.spring_boot_1.RecommendationData.ReminderCandidateService;
import com.example.spring_boot_1.config.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Push 알림 API.
 *
 * 권장 흐름:
 *   1) 클라가 Service Worker 등록 + pushManager.subscribe(VAPID_PUBLIC)
 *   2) POST /push/subscribe   (endpoint + p256dh + auth)
 *   3) POST /push/test         (즉시 검증)
 *   4) DELETE /push/subscribe (해지)
 *
 * GET /push/vapid-public-key — 클라가 VAPID public 만 받아서 subscribe 에 사용.
 */
@RestController
@RequestMapping("/push")
public class PushController {

    private final PushService pushService;
    private final PushSubscriptionRepository repository;
    private final UserDataRepository userDataRepository;
    private final ReminderCandidateService reminderCandidateService;
    private final com.example.spring_boot_1.LinkData.LinkDataRepository linkDataRepository;

    public PushController(
            PushService pushService,
            PushSubscriptionRepository repository,
            UserDataRepository userDataRepository,
            ReminderCandidateService reminderCandidateService,
            com.example.spring_boot_1.LinkData.LinkDataRepository linkDataRepository
    ) {
        this.pushService = pushService;
        this.repository = repository;
        this.userDataRepository = userDataRepository;
        this.reminderCandidateService = reminderCandidateService;
        this.linkDataRepository = linkDataRepository;
    }

    /** 클라이언트가 VAPID public 만 가져가서 subscribe 호출에 사용. permitAll. */
    @GetMapping("/vapid-public-key")
    public ResponseEntity<?> vapid() {
        String pk = pushService.getPublicKey();
        if (pk == null || pk.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "disabled",
                    "message", "Push 미설정 (VAPID 키 없음)"
            ));
        }
        return ResponseEntity.ok(Map.of("publicKey", pk));
    }

    public record SubscribeRequest(String endpoint, Keys keys) {
        public record Keys(String p256dh, String auth) {}
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody SubscribeRequest req) {
        if (req == null || req.endpoint() == null || req.keys() == null
                || req.keys().p256dh() == null || req.keys().auth() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", "endpoint, keys.p256dh, keys.auth 가 모두 필요합니다."
            ));
        }
        int userId = SecurityUtil.currentUserId();
        UserData user = userDataRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("user not found"));

        PushSubscription row = repository.findByUserDataIdAndEndpoint(userId, req.endpoint())
                .orElseGet(PushSubscription::new);
        row.setUserData(user);
        row.setEndpoint(req.endpoint());
        row.setP256dh(req.keys().p256dh());
        row.setAuth(req.keys().auth());
        repository.save(row);

        return ResponseEntity.ok(Map.of("ok", true, "id", row.getId()));
    }

    @DeleteMapping("/subscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, String> body) {
        if (body == null || body.get("endpoint") == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", "endpoint 가 필요합니다."
            ));
        }
        int userId = SecurityUtil.currentUserId();
        long removed = repository.deleteByUserDataIdAndEndpoint(userId, body.get("endpoint"));
        return ResponseEntity.ok(Map.of("ok", true, "removed", removed));
    }

    /** 즉시 테스트 — 본인의 모든 디바이스에 today 추천 미리보기 발사. */
    @PostMapping("/test")
    public ResponseEntity<?> test() {
        int userId = SecurityUtil.currentUserId();
        UserData user = userDataRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("user not found"));
        var candidates = reminderCandidateService.getCandidates(user.getUserName(), "today", 5);
        String preview = candidates.isEmpty() ? null : candidates.get(0).title();
        PushService.PushResult r = pushService.sendToUser(
                userId,
                PushService.Payload.todayDigest(Math.max(candidates.size(), 1), preview)
        );
        return ResponseEntity.ok(Map.of(
                "sent", r.sent(),
                "removed", r.removed(),
                "failed", r.failed(),
                "candidates", candidates.size()
        ));
    }

    /** 즉시 테스트 — 주간 회고 요약 발사 (이번 주 저장/미열람 통계). */
    @PostMapping("/test-weekly")
    public ResponseEntity<?> testWeekly() {
        int userId = SecurityUtil.currentUserId();
        long savedThisWeek = linkDataRepository.countSavedSince(
                userId, java.time.LocalDateTime.now().minusDays(7));
        long totalUnread = linkDataRepository.countUnread(userId);
        PushService.PushResult r = pushService.sendToUser(
                userId,
                PushService.Payload.weeklySummary(savedThisWeek, totalUnread)
        );
        return ResponseEntity.ok(Map.of(
                "sent", r.sent(),
                "removed", r.removed(),
                "failed", r.failed(),
                "savedThisWeek", savedThisWeek,
                "totalUnread", totalUnread
        ));
    }
}
