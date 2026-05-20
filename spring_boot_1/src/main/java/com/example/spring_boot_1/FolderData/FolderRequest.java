package com.example.spring_boot_1.FolderData;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderRequest {
    private String name;       // 생성할 폴더 이름 
    private String userName;   // 폴더를 소유할 유저 이름
    private Integer parentId;  // 부모 폴더 ID 
}