package com.example.spring_boot_1.FolderData;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Integer> {
    
    // 특정 유저의 최상위 폴더(부모가 없는 폴더) 찾기
    // UserData 엔티티 안의 userName 필드를 검색하도록 메서드명 변경
    List<Folder> findByUserDataUserNameAndParentFolderIsNull(String userName);
    
    // 특정 부모 폴더 아래에 있는 하위 폴더들 찾기
    List<Folder> findByParentFolderId(int parentId);
}