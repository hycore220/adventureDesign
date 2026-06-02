package com.example.spring_boot_1.PushData;

import com.example.spring_boot_1.UserData.UserData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자 디바이스의 push 구독 정보.
 *
 * 한 사용자가 여러 디바이스 (폰 + 데스크탑) 를 동시에 구독할 수 있으므로
 * (user_id, endpoint) 페어가 unique. 같은 endpoint 는 디바이스 1대를 식별.
 *
 * - endpoint: FCM/APNs/Mozilla 의 relay URL
 * - p256dh : 페이로드 암호화용 클라이언트 공개키 (base64url)
 * - auth   : 인증 비밀값 (base64url, 16 bytes)
 *
 * 410/404 응답 → 만료된 endpoint 로 보고 row 삭제.
 */
@Entity
@Table(
        name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "endpoint"})
)
@Getter
@Setter
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData userData;

    @Column(name = "endpoint", nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(name = "p256dh", nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth", nullable = false, length = 64)
    private String auth;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
