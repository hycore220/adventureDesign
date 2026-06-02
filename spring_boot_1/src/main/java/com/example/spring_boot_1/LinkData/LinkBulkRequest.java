package com.example.spring_boot_1.LinkData;

import java.util.List;

/**
 * 일괄 작업 요청 — REMIND_STRATEGY §5 UX 원칙 4("이번 묶음 모두 Archive").
 *
 * action:
 *   - "DELETE" : ids 를 한번에 삭제
 *   - "MOVE"   : ids 를 folderId 로 이동 (Archive 폴더로 옮기면 "모두 Archive")
 *
 * 본인 소유 + 존재하는 ID 만 처리하고 나머지는 조용히 건너뛴다.
 */
public record LinkBulkRequest(List<Integer> ids, String action, Integer folderId) {
}
