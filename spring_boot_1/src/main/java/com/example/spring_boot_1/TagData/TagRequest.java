package com.example.spring_boot_1.TagData;

/**
 * POST /tags 입력 DTO. name 은 서버에서 trim + 길이/중복 검증.
 * userId 는 인증된 사용자로 결정 (요청 본문 무시).
 */
public record TagRequest(String name) {
}
