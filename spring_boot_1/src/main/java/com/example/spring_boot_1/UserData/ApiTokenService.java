package com.example.spring_boot_1.UserData;

import com.example.spring_boot_1.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * API 토큰 발급/조회/폐기.
 *
 * 발급 토큰 형식: 32바이트 랜덤 → Base16(hex) = 64자 영숫자.
 * DB 에는 SHA-256 해시만 저장 (정확히 64자 hex).
 * 평문 토큰은 발급 시점 응답에만 노출 — 이후 절대 복구 불가.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApiTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final ApiTokenRepository repository;

    public IssueResult issue(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("토큰 이름이 필요합니다.");
        }
        int userId = SecurityUtil.currentUserId();

        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String plainToken = HEX.formatHex(raw);
        String hash = sha256Hex(plainToken);

        ApiToken token = new ApiToken();
        token.setUserId(userId);
        token.setName(name.trim());
        token.setTokenHash(hash);
        token.setCreatedAt(LocalDateTime.now());
        repository.save(token);

        return new IssueResult(token.getId(), token.getName(), token.getCreatedAt(), plainToken);
    }

    @Transactional(readOnly = true)
    public List<ApiTokenSummary> listMine() {
        int userId = SecurityUtil.currentUserId();
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(t -> new ApiTokenSummary(
                        t.getId(),
                        t.getName(),
                        t.getCreatedAt(),
                        t.getLastUsedAt(),
                        t.getRevokedAt(),
                        t.isActive()
                ))
                .toList();
    }

    public void revoke(int tokenId) {
        ApiToken token = repository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 토큰 id입니다."));
        if (token.getUserId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 토큰의 소유자가 아닙니다.");
        }
        if (token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now());
            repository.save(token);
        }
    }

    /** 인증 필터에서 호출 — 평문 토큰을 받아 활성 ApiToken 을 찾는다. */
    @Transactional(readOnly = true)
    public Optional<ApiToken> findActiveByPlainToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) return Optional.empty();
        String hash = sha256Hex(plainToken.trim());
        return repository.findByTokenHash(hash).filter(ApiToken::isActive);
    }

    /** 인증 성공 시 lastUsedAt 업데이트 (별도 트랜잭션). */
    public void touchLastUsed(int tokenId) {
        repository.findById(tokenId).ifPresent(t -> {
            t.setLastUsedAt(LocalDateTime.now());
            repository.save(t);
        });
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 환경", e);
        }
    }

    /** 발급 응답 — plainToken 은 이 시점 외엔 안 보임. */
    public record IssueResult(int id, String name, LocalDateTime createdAt, String plainToken) {}

    /** 목록 응답 — plainToken 없음. */
    public record ApiTokenSummary(
            int id,
            String name,
            LocalDateTime createdAt,
            LocalDateTime lastUsedAt,
            LocalDateTime revokedAt,
            boolean active
    ) {}
}
