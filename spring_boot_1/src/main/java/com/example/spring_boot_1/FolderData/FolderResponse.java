package com.example.spring_boot_1.FolderData;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FolderResponse {
    private int id;
    private String name;
    private Integer parentId; // 부모 폴더의 ID만 안전하게 포함 (최상위면 null)
}