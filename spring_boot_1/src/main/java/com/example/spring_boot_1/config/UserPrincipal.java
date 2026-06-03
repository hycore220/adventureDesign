package com.example.spring_boot_1.config;

import java.io.Serializable;

/**
 * SecurityContext에 저장되는 인증된 사용자의 principal.
 * 세션 저장을 위해 Serializable 필요 — record는 컴포넌트가 모두 직렬화
 * 가능하면 자동으로 Serializable이다.
 */
public record UserPrincipal(int id, String userName) implements Serializable {
}
