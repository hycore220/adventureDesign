package com.example.spring_boot_1.TagData;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Integer> {

    /** 내 태그 목록 (이름 오름차순). */
    List<Tag> findByUserData_IdOrderByNameAsc(int userId);

    /** 사용자별 이름 중복(대소문자 무시) 검사 — ERD unique(user_id, lower(name)). */
    boolean existsByUserData_IdAndNameIgnoreCase(int userId, String name);

    /** 소유권까지 함께 확인하는 단건 조회. */
    Optional<Tag> findByIdAndUserData_Id(int id, int userId);
}
