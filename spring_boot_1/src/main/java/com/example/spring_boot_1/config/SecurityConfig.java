package com.example.spring_boot_1.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 쉼표로 구분된 허용 출처 (예: "http://localhost:5173,https://app.example.com") */
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    private final ApiTokenAuthFilter apiTokenAuthFilter;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(ApiTokenAuthFilter apiTokenAuthFilter, JwtAuthFilter jwtAuthFilter) {
        this.apiTokenAuthFilter = apiTokenAuthFilter;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Stateless JWT 기반 — CSRF 무관 (헤더 인증은 브라우저 자동 첨부 안 됨).
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 세션 생성 안 함 — 모든 요청은 매번 헤더로 인증.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 필터 우선순위: JWT(짧은 access) → ApiToken(익스텐션 영구 토큰)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        // 인증이 필요 없는 공개 경로 (헬스체크 포함 — Fly 등이 인증 없이 도달)
                        .requestMatchers(
                                "/auth/signup",
                                "/auth/login",
                                "/auth/logout",
                                "/auth/refresh",
                                "/health",
                                "/error"
                        ).permitAll()
                        // 그 외 모든 요청은 인증 필요
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        // 미인증 → 401 JSON
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write(
                                    "{\"error\":\"unauthorized\",\"message\":\"로그인이 필요합니다.\"}"
                            );
                        })
                        // 권한 없음 → 403 JSON
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            res.getWriter().write(
                                    "{\"error\":\"forbidden\",\"message\":\"권한이 없습니다.\"}"
                            );
                        })
                )
                // 폼 로그인/HTTP Basic 비활성 — REST 흐름 사용
                .formLogin(fl -> fl.disable())
                .httpBasic(hb -> hb.disable())
                .logout(lo -> lo.disable()); // /auth/logout 컨트롤러가 처리

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        cfg.setAllowedHeaders(List.of("*"));
        // 세션 쿠키를 같이 보내려면 필수
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
