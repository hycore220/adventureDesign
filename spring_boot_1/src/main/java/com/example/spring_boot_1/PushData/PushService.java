package com.example.spring_boot_1.PushData;

import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Web Push 발사 서비스.
 *
 * - VAPID 키는 환경변수 (`APP_VAPID_PUBLIC` / `APP_VAPID_PRIVATE`) 로 주입.
 * - 페이로드는 JSON {title, body, url} — sw.js 가 그대로 showNotification 호출.
 * - 410/404 응답 시 endpoint 가 만료된 것으로 보고 DB 에서 row 삭제.
 *
 * VAPID 키 생성: `nl.martijndwars.webpush.cli.Main keygen` 또는 openssl + base64.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    static {
        // web-push-java 가 BouncyCastle JCE provider 를 요구
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Value("${app.vapid.public:}")
    private String vapidPublic;

    @Value("${app.vapid.private:}")
    private String vapidPrivate;

    @Value("${app.vapid.subject:mailto:owner@example.com}")
    private String vapidSubject;

    private final PushSubscriptionRepository pushSubscriptionRepository;

    private nl.martijndwars.webpush.PushService webPush;

    public PushService(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    @PostConstruct
    public void init() throws GeneralSecurityException {
        if (vapidPublic.isBlank() || vapidPrivate.isBlank()) {
            log.warn("VAPID 키 미설정 — push 발사 비활성화");
            return;
        }
        webPush = new nl.martijndwars.webpush.PushService(vapidPublic, vapidPrivate, vapidSubject);
        log.info("PushService 활성화 — VAPID public={}..", vapidPublic.substring(0, Math.min(8, vapidPublic.length())));
    }

    public boolean isEnabled() {
        return webPush != null;
    }

    public String getPublicKey() {
        return vapidPublic;
    }

    /** 특정 사용자의 모든 디바이스에 push 발사. (sent, removed, failed) 카운트 반환. */
    public PushResult sendToUser(int userId, Payload payload) {
        if (!isEnabled()) {
            log.debug("push 비활성화 상태 — userId={} 건너뜀", userId);
            return new PushResult(0, 0, 0);
        }
        List<PushSubscription> subs = pushSubscriptionRepository.findByUserDataId(userId);
        int sent = 0, removed = 0, failed = 0;
        for (PushSubscription sub : subs) {
            SendOutcome r = sendToOne(sub, payload);
            if (r == SendOutcome.SENT) sent++;
            else if (r == SendOutcome.REMOVED) removed++;
            else failed++;
        }
        return new PushResult(sent, removed, failed);
    }

    /** 단일 endpoint 에 발사. */
    private SendOutcome sendToOne(PushSubscription sub, Payload payload) {
        try {
            Subscription.Keys keys = new Subscription.Keys(sub.getP256dh(), sub.getAuth());
            Subscription subscription = new Subscription(sub.getEndpoint(), keys);
            String body = jsonPayload(payload);
            Notification notification = new Notification(subscription, body);

            // Chrome 은 AES128GCM (RFC 8291) 만 지원. 라이브러리 default 가 AESGCM 이면
            // 페이로드는 전달되지만 SW push event 가 발화되지 않고 silently drop 됨.
            org.apache.http.HttpResponse res = webPush.send(notification, nl.martijndwars.webpush.Encoding.AES128GCM);
            int code = res.getStatusLine().getStatusCode();
            if (code >= 200 && code < 300) {
                sub.setLastSuccessAt(LocalDateTime.now());
                pushSubscriptionRepository.save(sub);
                return SendOutcome.SENT;
            }
            if (code == 404 || code == 410) {
                pushSubscriptionRepository.delete(sub);
                log.info("expired push endpoint 제거 — id={}", sub.getId());
                return SendOutcome.REMOVED;
            }
            log.warn("push 발사 실패 status={} endpoint={}", code, truncEndpoint(sub.getEndpoint()));
            return SendOutcome.FAILED;
        } catch (GeneralSecurityException | InterruptedException |
                 ExecutionException | JoseException | java.io.IOException e) {
            log.warn("push 발사 예외 endpoint={} : {}", truncEndpoint(sub.getEndpoint()), e.getMessage());
            return SendOutcome.FAILED;
        }
    }

    private String truncEndpoint(String e) {
        if (e == null) return "";
        return e.length() > 60 ? e.substring(0, 60) + "…" : e;
    }

    /** 의존성 추가 없이 손으로 빌드하는 JSON (필드 3개라 안전). */
    private String jsonPayload(Payload p) {
        return "{\"title\":\"" + escape(p.title())
                + "\",\"body\":\"" + escape(p.body())
                + "\",\"url\":\"" + escape(p.url()) + "\"}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    public enum SendOutcome { SENT, REMOVED, FAILED }

    public record PushResult(int sent, int removed, int failed) {
        public boolean anyDelivered() { return sent > 0; }
    }

    /** sw.js 가 표시할 알림 페이로드. */
    public record Payload(String title, String body, String url) {
        public static Payload todayDigest(int count, String previewTitle) {
            String body = previewTitle == null || previewTitle.isBlank()
                    ? count + "개가 있어요"
                    : count == 1 ? previewTitle : previewTitle + " 외 " + (count - 1) + "개";
            return new Payload("오늘 다시 볼 링크", body, "/today");
        }

        public static Payload weeklyDigest(int count, String previewTitle) {
            String body = previewTitle == null || previewTitle.isBlank()
                    ? "이번 주 돌아볼 링크 " + count + "개"
                    : previewTitle + " 외 " + Math.max(count - 1, 0) + "개 — 이번 주 정리해요";
            return new Payload("이번 주 Save It", body, "/today");
        }

        /**
         * 주간 회고 = 개별 링크가 아니라 "이번 주 통계 요약" (REMIND_STRATEGY §3.1).
         * 매일 알림(개별 링크 N개)과 성격이 다름 — 활동/누적 현황을 보여줌.
         */
        public static Payload weeklySummary(long savedThisWeek, long totalUnread) {
            String body;
            if (savedThisWeek == 0 && totalUnread == 0) {
                body = "이번 주는 조용했어요. 새 링크를 저장해보세요";
            } else if (totalUnread == 0) {
                body = "이번 주 " + savedThisWeek + "개 저장 · 미열람 0개 (깔끔!)";
            } else {
                body = "이번 주 " + savedThisWeek + "개 저장 · 미열람 "
                        + totalUnread + "개 쌓였어요";
            }
            return new Payload("이번 주 Save It 회고", body, "/today");
        }
    }
}
