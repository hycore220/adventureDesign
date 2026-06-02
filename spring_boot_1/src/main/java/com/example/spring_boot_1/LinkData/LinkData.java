package com.example.spring_boot_1.LinkData;

import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.FolderData.Folder;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "link_data", indexes = {
        @Index(name = "idx_link_data_user_host", columnList = "user_id, host"),
        @Index(name = "idx_link_data_user_content_type", columnList = "user_id, content_type")
})
public class LinkData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(columnDefinition = "TEXT")
    private String link;

    @Column
    private String title;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserData userData;

    /**
     * PARA 분류. DB 컬럼명은 호환을 위해 "PARAStatus" 유지.
     * 값은 ParaStatus.name() (UPPERCASE)로 저장하고, 읽을 땐 lenient 매핑으로
     * 기존 자유 문자열("Project", "project" 등)까지 흡수한다.
     *
     * ERD §1.1: 폴더가 PARA 의 source of truth, 이 컬럼은 denormalized cache.
     * 폴더에 속한 링크면 폴더의 paraCategory 를 따라간다.
     */
    @Convert(converter = ParaStatus.ParaStatusConverter.class)
    @Column(name = "PARAStatus")
    private ParaStatus PARAStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column
    private LocalDateTime lastUpdate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ddl-auto=update로 기존 테이블에 추가될 때 DEFAULT가 없으면 MySQL이 실패한다.
    @Column(name = "is_read", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    // ============================================================
    //  ERD §4.2 v1 / §7 MVP 보강 컬럼 — 컨텍스트 매칭(REMIND §3.3) 동작에 필요
    // ============================================================

    /**
     * URL 호스트. @PrePersist 에서 link 로부터 자동 추출.
     * GENERATED ALWAYS AS 가 가장 깔끔하지만 MySQL 8 + JPA 호환 위해 자바측 파생.
     */
    @Column(length = 255)
    private String host;

    /** 콘텐츠 타입 — YouTube/Article/GitHub/Other. host 로부터 자동 추론 또는 클라이언트 지정. */
    @Convert(converter = ContentType.ContentTypeConverter.class)
    @Column(name = "content_type", length = 32)
    private ContentType contentType;

    /** 썸네일 URL — YouTube 카드 등 익스텐션 컨텍스트 매칭 UI 에 사용. */
    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.lastUpdate = now;
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.PARAStatus == null) {
            this.PARAStatus = ParaStatus.UNSPECIFIED;
        }
        syncDerivedColumns();
    }

    @PreUpdate
    public void onUpdate() {
        this.lastUpdate = LocalDateTime.now();
        syncDerivedColumns();
    }

    /** link 변경 시 host / contentType 자동 갱신. 명시적으로 contentType 을 받았으면 보존. */
    private void syncDerivedColumns() {
        this.host = extractHost(this.link);
        if (this.contentType == null) {
            this.contentType = ContentType.inferFromHost(this.host);
        }
    }

    /** URL 에서 호스트만 뽑아냄. 파싱 실패 시 null. lower-cased. */
    private static String extractHost(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(java.util.Locale.ROOT);
            // www. 접두 정규화 — 컨텍스트 매칭(REMIND §3.3)에서
            // youtube.com ↔ www.youtube.com 불일치 방지.
            if (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
