package com.example.spring_boot_1.UserData;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인 / 가입 / refresh 응답.
 *
 * accessToken 은 짧은 만료의 JWT, refreshToken 은 1회용 회전 토큰.
 * 프론트는 access 를 메모리에, refresh 는 안전한 저장소에 보관 권장
 * (XSS 표적 줄이려면 refresh 도 HttpOnly 쿠키 검토).
 */
@Getter
@AllArgsConstructor
public class AuthResponse {
    private int id;
    private String userName;
    private String accessToken;
    private String refreshToken;

    public static AuthResponse of(UserData userData, String accessToken, String refreshToken) {
        return new AuthResponse(userData.getId(), userData.getUserName(), accessToken, refreshToken);
    }

    /** /auth/me 같이 토큰 없이 사용자 정보만 줄 때. */
    public static AuthResponse profileOnly(UserData userData) {
        return new AuthResponse(userData.getId(), userData.getUserName(), null, null);
    }
}
