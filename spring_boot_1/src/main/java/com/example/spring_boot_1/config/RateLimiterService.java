package com.example.spring_boot_1.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 사용자별 / 작업별 / 일자별 인메모리 호출 카운터.
 *
 * 의도: 베타에서 어뷰즈 또는 버그로 인한 DB 부하 폭주를 방지.
 * 운영 단위로 가는 게 깔끔해서, 어떤 컨트롤러가 호출하든 일관되게 적용된다.
 *
 * 한계:
 *  - 서버 재시작 시 카운터 초기화 (베타엔 OK)
 *  - 단일 인스턴스에 한정 — 멀티 인스턴스 운영 시 Redis 등으로 교체 필요
 *
 * 2026-05-21 OpenAI 의존성 제거에 따라 OP_REC_* 상수 폐기.
 */
@Service
public class RateLimiterService {

    /** 알려진 작업 키 — 코드 곳곳에 문자열 박지 않도록 상수화 */
    public static final String OP_LINK_CREATE = "link-create";
    /** 인증 전 엔드포인트 — IP 단위로 제한 (어뷰징/브루트포스 완화) */
    public static final String OP_SIGNUP = "signup";
    public static final String OP_LOGIN = "login";

    private final Map<String, Integer> limits = new HashMap<>();
    private final ConcurrentHashMap<Key, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IpKey, AtomicInteger> ipCounters = new ConcurrentHashMap<>();

    public RateLimiterService(
            @Value("${app.ratelimit.link-create:200}") int linkCreate,
            @Value("${app.ratelimit.signup:20}") int signup,
            @Value("${app.ratelimit.login:50}") int login
    ) {
        limits.put(OP_LINK_CREATE, linkCreate);
        limits.put(OP_SIGNUP, signup);
        limits.put(OP_LOGIN, login);
    }

    /**
     * 현재 인증 사용자에 대해 해당 operation의 카운터를 1 증가시키고,
     * 일일 한도를 넘으면 {@link RateLimitExceededException} 을 던진다.
     */
    public void acquire(String operation) {
        int userId = SecurityUtil.currentUserId();
        Integer limit = limits.get(operation);
        if (limit == null) return; // 등록되지 않은 작업은 제한 없음

        Key key = new Key(userId, operation, LocalDate.now());
        int after = counters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (after > limit) {
            // 초과 시 카운터를 다시 감소 — 사용자가 실패 호출까지 손해보지 않도록
            counters.get(key).decrementAndGet();
            throw new RateLimitExceededException(operation, limit);
        }
    }

    /** 현재 카운트 조회 — 디버깅/모니터링용 */
    public int currentCount(String operation) {
        int userId = SecurityUtil.currentUserId();
        AtomicInteger c = counters.get(new Key(userId, operation, LocalDate.now()));
        return c == null ? 0 : c.get();
    }

    public int limitFor(String operation) {
        return limits.getOrDefault(operation, Integer.MAX_VALUE);
    }

    /**
     * 인증 전 엔드포인트(signup/login)용 — 클라이언트 IP 단위 일일 제한.
     * ip 가 null/blank 면 제한 생략(프록시 헤더 못 읽는 환경 안전망).
     */
    public void acquireByIp(String operation, String ip) {
        Integer limit = limits.get(operation);
        if (limit == null) return;
        if (ip == null || ip.isBlank()) return;

        IpKey key = new IpKey(ip, operation, LocalDate.now());
        int after = ipCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (after > limit) {
            ipCounters.get(key).decrementAndGet();
            throw new RateLimitExceededException(operation, limit);
        }
    }

    private record Key(int userId, String operation, LocalDate day) {
    }

    private record IpKey(String ip, String operation, LocalDate day) {
    }
}
