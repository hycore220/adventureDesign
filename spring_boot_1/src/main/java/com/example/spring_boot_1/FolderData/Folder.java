package com.example.spring_boot_1.FolderData;
import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.ParaStatus;

import com.example.spring_boot_1.UserData.UserData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 폴더 — ERD §1.1 "PARA는 폴더의 속성으로 둔다" 에 맞춰 paraCategory 보관.
 * 폴더의 paraCategory 가 source of truth, LinkData.PARAStatus 는 denormalized cache.
 * 폴더 PARA 변경 시 소속 링크들의 PARAStatus 가 cascade 동기화된다.
 */
@Entity
@Getter
@Setter
@Table(name = "folder")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String name;

    /**
     * PARA 분류 — null 은 "미지정" 으로 취급 (ERD §4.1).
     * UPPERCASE 로 저장하지만 기존 자유 문자열 데이터를 위해 lenient converter 사용.
     */
    @Convert(converter = ParaStatus.ParaStatusConverter.class)
    @Column(name = "para_category")
    private ParaStatus paraCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData userData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Folder parentFolder;

    @OneToMany(mappedBy = "parentFolder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Folder> subFolders = new ArrayList<>();

    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LinkData> links = new ArrayList<>();
}