package com.example.spring_boot_1.RecommendationData;

import java.time.LocalDateTime;

/**
 * PUT /recommendation-weights/{id} 의 입력 DTO.
 *
 * RecommendationWeight 엔티티를 통째로 받으면 클라이언트가 id/bookmark/embeddingVector
 * 같은 내부 필드까지 덮어쓸 수 있는 Mass Assignment 위험이 있어, 클라이언트가
 * 조정 가능한 필드만 화이트리스트로 받는다.
 *
 * 모든 필드는 nullable — 보낸 필드만 갱신한다.
 *
 * 단, snoozedUntil은 "보내지 않음"과 "음소거 해제(=null로 명시)"를 구분해야 하므로
 * 서비스 레이어에서 'clearSnooze' 플래그(snoozeClear)로 명시적으로 해제 의도를 받는다.
 *   - snoozedUntil != null: 그 시각까지 음소거
 *   - snoozedUntil == null && snoozeClear == true: 음소거 해제
 *   - snoozedUntil == null && snoozeClear != true: 기존 값 유지
 *
 * 2026-05-21: frequency 필드 제거.
 */
public record RecommendationUpdateRequest(
        String name,
        Double importance,
        LocalDateTime snoozedUntil,
        Boolean snoozeClear
) {
}
