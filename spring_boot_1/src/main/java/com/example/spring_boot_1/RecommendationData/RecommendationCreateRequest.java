package com.example.spring_boot_1.RecommendationData;

import java.time.LocalDateTime;

/**
 * POST /recommendation-weights 의 입력 DTO.
 *
 * 클라이언트는 bookmark.id 와 사용자 조정 가능한 필드만 보낸다.
 * id/embeddingVector/similarity 등 내부 필드는 노출하지 않는다.
 *
 * snoozedUntil: 추천 음소거 만료 시각 (nullable).
 *   - null/미전송: 음소거 해제
 *   - 미래 시각: 그 시각까지 추천 후보에서 제외
 *   - 9999-12-31: 영구 음소거 (UI sentinel)
 *
 * 2026-05-21: frequency 필드 제거 (자동 갱신 없어 점수 기여가 영구 0이었음).
 */
public record RecommendationCreateRequest(
        Integer bookmarkId,
        String name,
        Double importance,
        LocalDateTime snoozedUntil
) {
}
