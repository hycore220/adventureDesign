package com.example.spring_boot_1.config;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext 에서 현재 인증된 사용자를 꺼내는 헬퍼.
 * SecurityFilterChain이 이미 /auth/* 외 경로를 막아주므로 정상 흐름에선
 * IllegalStateException이 발생하지 않는다.
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /** 현재 요청의 인증된 사용자. */
    public static UserPrincipal currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            throw new IllegalStateException("인증되지 않은 요청입니다.");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up;
        }
        throw new IllegalStateException("지원하지 않는 principal 타입입니다: " + principal.getClass());
    }

    public static String currentUserName() {
        return currentUser().userName();
    }

    public static int currentUserId() {
        return currentUser().id();
    }

    /**
     * path/body의 userName이 현재 인증 사용자와 같은지 검증. 다르면 403.
     */
    public static void requireOwnerByName(String userName) {
        if (userName == null || !userName.equals(currentUserName())) {
            throw new AccessDeniedException("해당 사용자의 리소스가 아닙니다.");
        }
    }

    /**
     * path/body의 userId가 현재 인증 사용자와 같은지 검증. 다르면 403.
     */
    public static void requireOwnerById(int userId) {
        if (currentUserId() != userId) {
            throw new AccessDeniedException("해당 사용자의 리소스가 아닙니다.");
        }
    }
}
