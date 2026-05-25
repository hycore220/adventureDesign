package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.LinkData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 추천 가중치 — 한 LinkData 에 1:1.
 *
 * 정리 이력:
 *   - weightValue 캐시 컬럼 폐기 — /top3 도 ReminderCandidateService 로 일원화
 *   - lastUpdate / frequency 컬럼 폐기 (영구 사망 신호)
 *   - similarity / embedding* 컬럼 폐기 (OpenAI 의존성 제거, 문서 범위 밖)
 *
 * 남은 필드는 사용자가 명시적으로 다루는 신호 + 음소거 토글 + 표시 이름뿐.
 */
@Entity
@Getter
@Setter
@Table(
        name = "recommendation_weight",
        indexes = {
                @Index(name = "idx_recommendation_weight_bookmark", columnList = "link_data_id")
        }
)
public class RecommendationWeight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    // 표시용 이름 — 사용자가 직접 라벨링하지 않는 경우가 많아 nullable.
    @Column
    private String name;

    /**
     * 추천에서 음소거할 만료 시점.
     * - null: 음소거 아님 (정상 후보)
     * - 미래 시각: 그 시각이 지나기 전까지는 후보 제외
     * - 과거 시각: 자동 만료 (isSnoozed() 가 false 반환)
     * - 9999-12-31: "영구 음소거" sentinel
     */
    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

    @Column
    private double importance;

    @OneToOne
    @JoinColumn(name = "link_data_id", nullable = false, unique = true)
    private LinkData bookmark;

    /** 현재 시점 기준 음소거 활성 상태인지 여부. */
    public boolean isSnoozed() {
        return snoozedUntil != null && snoozedUntil.isAfter(LocalDateTime.now());
    }
}
