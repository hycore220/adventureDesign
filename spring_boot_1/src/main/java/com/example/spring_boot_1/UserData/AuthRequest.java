package com.example.spring_boot_1.UserData;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthRequest {
    private String userName;
    private String email;
    private String password;

    public String resolvedUserName() {
        if (userName != null && !userName.isBlank()) {
            return userName.trim();
        }
        if (email != null && !email.isBlank()) {
            return email.trim();
        }
        return null;
    }
}
