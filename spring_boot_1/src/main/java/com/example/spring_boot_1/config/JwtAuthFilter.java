package com.example.spring_boot_1.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer <JWT> 헤더가 있으면 검증 후 SecurityContext 에 주입.
 *
 * 우선순위 — ApiTokenAuthFilter 와 둘 다 등록되어 있고, JWT 가 먼저 동작한다:
 *   - 헤더가 JWT 패턴(구두점 2개) 이면 이 필터가 처리
 *   - 그 외 영구 ApiToken 패턴(64자 hex 등) 이면 ApiTokenAuthFilter 가 처리
 *
 * 토큰 없거나 잘못된 토큰 → SecurityContext 안 건드림 (entry point 가 401 처리)
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain
    ) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        // JWT 는 .으로 구분된 3 segment. 그 외 패턴은 ApiTokenAuthFilter 가 처리하도록 패스.
        if (token.chars().filter(c -> c == '.').count() != 2) {
            chain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.verifyAccessToken(token);
            int userId = Integer.parseInt(claims.getSubject());
            String userName = claims.get("userName", String.class);

            UserPrincipal principal = new UserPrincipal(userId, userName);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
            );

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);
        } catch (JwtException | IllegalArgumentException ex) {
            // 위변조/만료 → 인증 실패. SecurityContext 안 건드리고 다음 필터로.
            // entry point 가 401 응답 처리.
        }

        chain.doFilter(request, response);
    }
}
