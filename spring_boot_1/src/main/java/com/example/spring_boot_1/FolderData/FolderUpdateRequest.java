package com.example.spring_boot_1.FolderData;

/**
 * PUT /folder/{id} 입력 DTO.
 *
 * 모든 필드 nullable — 보낸 필드만 갱신.
 * paraCategory 변경 시 소속 링크들의 PARAStatus 가 cascade 동기화된다 (ERD §1.1).
 */
public record FolderUpdateRequest(
        String name,
        String paraCategory  // "PROJECT" | "AREA" | "RESOURCE" | "ARCHIVE" | "UNSPECIFIED" | null
) {
}
