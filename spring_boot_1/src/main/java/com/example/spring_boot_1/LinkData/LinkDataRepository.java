package com.example.spring_boot_1.LinkData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LinkDataRepository extends JpaRepository<LinkData, Integer> {
    // findByUserName -> findByUserDataUserName 으로 변경!
    List<LinkData> findByUserDataUserName(String userName); 
    List<LinkData> findByFolderId(int folderId);
}