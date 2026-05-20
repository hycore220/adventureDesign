package com.example.spring_boot_1.UserData;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserDataRepository extends JpaRepository<UserData, Integer> {
    Optional<UserData> findByUserName(String userName);
}
