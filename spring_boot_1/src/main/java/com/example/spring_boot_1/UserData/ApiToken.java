package com.example.spring_boot_1.UserData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * API 토큰 — 익스텐션/모바일 등 비-브라우저 클라이언트용.
 *
 * 발급 시점에만 평문을 반환하고, DB 에는 해시(SHA-256)만 저장.
 * Authorization: Bearer <token> 헤더로 인증.
 *
 * 보안:
 *   - 토큰 해시는 unique index → 해시 충돌 시 거부
 *   - revokedAt 가 set 되면 인증 실패
 *   - lastUsedAt 으로 의심스러운 사용 패턴 추적 가능
 */
@Entity
@Getter
@Setter
@Table(
        name = "api_tokens",
        indexes = {
                @Index(name = "idx_api_tokens_user", columnList = "user_id"),
                @Index(name = "idx_api_tokens_hash", columnList = "token_hash", unique = true)
        }
)
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "user_id", nullable = false)
    private int userId;

    /** 사용자가 식별할 수 있는 라벨 — "내 익스텐션", "모바일 앱" 등. */
    @Column(nullable = false)
    private String name;

    /** SHA-256 hex (64자). 평문은 발급 시점에만 클라이언트에 반환, DB 엔 안 저장. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }
}
