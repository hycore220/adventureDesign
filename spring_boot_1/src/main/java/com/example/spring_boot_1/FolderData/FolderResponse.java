package com.example.spring_boot_1.FolderData;

import com.example.spring_boot_1.LinkData.ParaStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FolderResponse {
    private int id;
    private String name;
    private Integer parentId; // 부모 폴더의 ID만 안전하게 포함 (최상위면 null)
    private ParaStatus paraCategory; // ERD §1.1 — 프론트 PARA 4카드 그리드용

    public static FolderResponse from(Folder folder) {
        return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getParentFolder() != null ? folder.getParentFolder().getId() : null,
                folder.getParaCategory()
        );
    }
}