package com.example.spring_boot_1.config;

import com.example.spring_boot_1.UserData.RefreshToken;
import com.example.spring_boot_1.UserData.RefreshTokenRepository;
import com.example.spring_boot_1.UserData.UserData;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 발급 / 검증 / refresh 회전.
 *
 * 정책:
 *   - access: HS256, 짧은 만료 (default 15분). stateless.
 *   - refresh: 랜덤 32바이트 토큰, 응답으로 1회 노출, DB엔 SHA-256 해시만 저장. 7일.
 *   - rotate: refresh 사용 시 즉시 기존 행 삭제 + 새 토큰 발급 (token theft mitigation)
 *   - 모든 시간은 서버 로컬 LocalDateTime (운영 시 UTC + tz 표준화 검토)
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-ttl-minutes:15}")
    private long accessTtlMinutes;

    @Value("${app.jwt.refresh-ttl-days:7}")
    private long refreshTtlDays;

    @Value("${app.jwt.issuer:saveit}")
    private String issuer;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom random = new SecureRandom();
    private SecretKey signingKey;

    public JwtService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret 은 최소 32바이트(256bit) 이상이어야 합니다. " +
                            "환경변수 APP_JWT_SECRET 를 길게 설정해주세요. 현재 길이: " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    // ============================================================
    //  Access token
    // ============================================================

    public String issueAccessToken(UserData user) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("userName", user.getUserName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey)
                .compact();
    }

    /** 토큰 검증 후 claims 반환. 만료/위변조 시 JwtException 던짐. */
    public Claims verifyAccessToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ============================================================
    //  Refresh token (stateful)
    // ============================================================

    /**
     * 새 refresh 토큰 발급 — 평문은 1회만 반환되고 DB엔 해시만 저장된다.
     */
    @Transactional
    public String issueRefreshToken(UserData user) {
        String plain = generatePlainRefresh();
        RefreshToken row = new RefreshToken();
        row.setUserData(user);
        row.setTokenHash(sha256(plain));
        row.setExpiresAt(LocalDateTime.now().plusDays(refreshTtlDays));
        row.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.save(row);
        return plain;
    }

    /**
     * refresh 토큰 사용 → 기존 행 폐기 + 새 토큰 발급 (rotate).
     * 유효하지 않으면 IllegalArgumentException.
     */
    @Transactional
    public RefreshResult rotateRefreshToken(String plainRefresh) {
        String hash = sha256(plainRefresh);
        RefreshToken row = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 refresh 토큰입니다."));
        if (row.isExpired()) {
            refreshTokenRepository.delete(row);
            throw new IllegalArgumentException("만료된 refresh 토큰입니다.");
        }
        UserData user = row.getUserData();
        // rotate: 기존 행 즉시 폐기
        refreshTokenRepository.delete(row);
        String newAccess = issueAccessToken(user);
        String newRefresh = issueRefreshToken(user);
        return new RefreshResult(user, newAccess, newRefresh);
    }

    @Transactional
    public void revokeRefreshToken(String plainRefresh) {
        if (plainRefresh == null) return;
        refreshTokenRepository.deleteByTokenHash(sha256(plainRefresh));
    }

    @Transactional
    public void revokeAllForUser(int userId) {
        refreshTokenRepository.deleteByUserDataId(userId);
    }

    // ============================================================
    //  내부 헬퍼
    // ============================================================

    private String generatePlainRefresh() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record RefreshResult(UserData user, String accessToken, String refreshToken) {}
}
