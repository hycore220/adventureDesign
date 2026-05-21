package com.example.spring_boot_1.config;

/**
 * CORS 설정은 SecurityConfig#corsConfigurationSource() 로 이전됨.
 * Spring Security 필터 체인 내부에서 처리해야 인증/세션 쿠키와 호흡이 맞기 때문.
 *
 * 이 파일은 호환성을 위해 남겨두며 빈을 등록하지 않는다.
 */
public final class CorsConfig {
    private CorsConfig() {
    }
}
