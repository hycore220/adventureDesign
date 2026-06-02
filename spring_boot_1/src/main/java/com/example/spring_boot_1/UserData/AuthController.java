package com.example.spring_boot_1.UserData;

import com.example.spring_boot_1.config.JwtService;
import com.example.spring_boot_1.config.RateLimiterService;
import com.example.spring_boot_1.config.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JWT 기반 인증 (save-it 가정한 Supabase Auth 모델 정합).
 *
 * 흐름:
 *   POST /auth/signup → 가입 + access + refresh 발급
 *   POST /auth/login  → access + refresh 발급
 *   POST /auth/refresh → refresh 사용 + rotate (새 토큰 쌍)
 *   POST /auth/logout → refresh 폐기 (access 는 짧은 만료에 의존)
 *   GET  /auth/me     → 현재 사용자 정보 (Bearer access 필요)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final UserDataService userDataService;
    private final JwtService jwtService;
    private final RateLimiterService rateLimiter;

    /** 가입 kill-switch — 어뷰징 시 APP_SIGNUP_ENABLED=false 로 즉시 차단 (재배포 불필요). */
    @Value("${app.signup.enabled:true}")
    private boolean signupEnabled;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody AuthRequest request, HttpServletRequest http) {
        if (!signupEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "signup_disabled",
                    "message", "현재 신규 가입이 일시 중단되었습니다."));
        }
        rateLimiter.acquireByIp(RateLimiterService.OP_SIGNUP, clientIp(http));

        String userName = request.resolvedUserName();
        String password = request.getPassword();

        if (userName == null || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("사용자 이름과 비밀번호를 입력해주세요.");
        }

        try {
            UserData user = userDataService.create(userName, password);
            return ResponseEntity.ok(issuePair(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request, HttpServletRequest http) {
        rateLimiter.acquireByIp(RateLimiterService.OP_LOGIN, clientIp(http));

        String userName = request.resolvedUserName();
        String password = request.getPassword();

        if (userName == null || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("사용자 이름과 비밀번호를 입력해주세요.");
        }

        try {
            UserData user = userDataService.authenticate(userName, password);
            return ResponseEntity.ok(issuePair(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    /**
     * 클라이언트 실제 IP — Fly proxy 뒤라 X-Forwarded-For 첫 항목을 우선 사용.
     * 없으면 Fly-Client-IP, 그래도 없으면 remoteAddr.
     */
    private static String clientIp(HttpServletRequest http) {
        String xff = http.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String fly = http.getHeader("Fly-Client-IP");
        if (fly != null && !fly.isBlank()) return fly.trim();
        return http.getRemoteAddr();
    }

    /**
     * refresh 토큰 사용 → 새 토큰 쌍 발급 + 기존 refresh 즉시 폐기 (rotate).
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refresh = body == null ? null : body.get("refreshToken");
        if (refresh == null || refresh.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "bad_request",
                    "message", "refreshToken 이 필요합니다."));
        }
        try {
            JwtService.RefreshResult result = jwtService.rotateRefreshToken(refresh);
            return ResponseEntity.ok(AuthResponse.of(
                    result.user(),
                    result.accessToken(),
                    result.refreshToken()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "unauthorized",
                    "message", e.getMessage()));
        }
    }

    /**
     * 로그아웃 — refresh 토큰 폐기 (body 로 전달되면 그 토큰 행 삭제,
     * 없으면 무동작 = 클라이언트가 access 만 버리는 케이스).
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body) {
        if (body != null) {
            String refresh = body.get("refreshToken");
            if (refresh != null && !refresh.isBlank()) {
                jwtService.revokeRefreshToken(refresh);
            }
        }
        return ResponseEntity.ok(Map.of("message", "로그아웃 완료"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        String userName = SecurityUtil.currentUserName();
        UserData user = userDataService.getByUserName(userName);
        return ResponseEntity.ok(AuthResponse.profileOnly(user));
    }

    /** 가입/로그인 시 사용 — access + refresh 쌍 발급. */
    private AuthResponse issuePair(UserData user) {
        String access = jwtService.issueAccessToken(user);
        String refresh = jwtService.issueRefreshToken(user);
        return AuthResponse.of(user, access, refresh);
    }
}
