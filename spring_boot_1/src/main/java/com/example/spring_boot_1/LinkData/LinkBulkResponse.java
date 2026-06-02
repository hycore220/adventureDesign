package com.example.spring_boot_1.LinkData;

/**
 * 일괄 작업 결과.
 *  - requested: 요청에 담긴 ID 수
 *  - affected : 실제로 처리된 수 (본인 소유 + 존재하는 것만)
 */
public record LinkBulkResponse(int requested, int affected) {
}
