package com.example.spring_boot_1.UserData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Refresh 토큰 — JWT access 토큰 만료 후 재발급용 stateful 토큰.
 *
 * 정책:
 *   - 평문 토큰은 응답으로 1회 노출, DB엔 해시만 저장
 *   - 로그아웃 / 비밀번호 변경 시 행 삭제로 즉시 폐기
 *   - 사용자별 다중 행 허용 (여러 디바이스 동시 로그인)
 *   - rotate: refresh 사용 시 새 토큰 발급 + 기존 토큰 즉시 폐기
 */
@Entity
@Getter
@Setter
@Table(
        name = "refresh_token",
        indexes = {
                @Index(name = "idx_refresh_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_refresh_token_user", columnList = "user_id")
        }
)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData userData;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
