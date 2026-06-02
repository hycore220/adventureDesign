package com.example.spring_boot_1.TagData;

import com.example.spring_boot_1.UserData.UserData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자 자유 태그 — ERD §4.3 `tags` (v1).
 *
 * PARA 폴더와는 다른 축이다 (ERD 설계 노트 #3):
 *   - 폴더 = 재노출 정책 (PARA)
 *   - 태그 = 자유 검색/필터 라벨, 사용자가 직접 부착
 *
 * 사용자별로 이름이 유일해야 한다 (대소문자 무시는 애플리케이션 레이어에서 보장).
 * ERD 의 uuid PK 는 우리 스택 일관성(나머지 엔티티가 int IDENTITY)에 맞춰 int 로 대체.
 */
@Entity
@Getter
@Setter
@Table(
        name = "tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tags_user_name",
                columnNames = {"user_id", "name"}
        )
)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData userData;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
