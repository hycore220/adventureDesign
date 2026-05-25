package com.example.spring_boot_1.config;

import com.example.spring_boot_1.UserData.ApiToken;
import com.example.spring_boot_1.UserData.ApiTokenService;
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Authorization: Bearer <token> 헤더 기반 인증 필터.
 *
 * 세션이 이미 있으면 (UsernamePasswordAuthenticationToken 가 SecurityContext 에 있으면)
 * 건너뜀 — 세션 + 토큰 둘 다 지원하지만 세션이 우선.
 *
 * 토큰이 유효하면 UserPrincipal 을 SecurityContext 에 채워서 이후 컨트롤러/서비스가
 * 세션 기반 인증과 동일하게 동작하게 만든다.
 *
 * 실패해도 401 을 즉시 던지지 않고 그냥 SecurityContext 를 비워서 후속 인증 필터/
 * Spring 의 기본 미인증 처리에 맡긴다 — 잘못된 토큰은 결국 401 로 떨어짐.
 */
@Component
@RequiredArgsConstructor
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ApiTokenService apiTokenService;
    private final UserDataRepository userDataRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 이미 인증된 컨텍스트면 토큰 검사 건너뜀
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                && !(SecurityContextHolder.getContext().getAuthentication() instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String plainToken = header.substring(BEARER_PREFIX.length()).trim();
        Optional<ApiToken> tokenOpt = apiTokenService.findActiveByPlainToken(plainToken);
        if (tokenOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        ApiToken token = tokenOpt.get();
        Optional<UserData> userOpt = userDataRepository.findById(token.getUserId());
        if (userOpt.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        UserData user = userOpt.get();

        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUserName());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        // lastUsedAt 갱신 — 별도 트랜잭션
        try {
            apiTokenService.touchLastUsed(token.getId());
        } catch (Exception ignored) {
            // 인증 자체엔 영향 없도록 무시
        }

        filterChain.doFilter(request, response);
    }
}
