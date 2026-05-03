package com.example.spring_boot_1.UserData;

import org.springframework.data.jpa.repository.JpaRepository;


public interface UserDataRepository extends JpaRepository<UserData, Integer> {

}
