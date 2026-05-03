package com.example.spring_boot_1.LinkData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkDataRepository extends JpaRepository<LinkData, Integer> {
    List<LinkData> findByUserName(String userName);

}
