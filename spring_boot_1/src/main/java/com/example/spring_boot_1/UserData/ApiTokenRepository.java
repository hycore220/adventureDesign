package com.example.spring_boot_1.UserData;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Integer> {

    /** Bearer 인증 시 해시로 토큰 조회. */
    Optional<ApiToken> findByTokenHash(String tokenHash);

    /** 사용자의 활성 + 폐기된 모든 토큰 목록 (라벨/생성일/마지막사용 표시용). */
    List<ApiToken> findByUserIdOrderByCreatedAtDesc(int userId);
}
