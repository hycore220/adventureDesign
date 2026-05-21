package com.example.spring_boot_1.LinkData;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinkRequest {
    private String link;
    private String title;
    private String userName;
    private String PARAStatus;
    private Integer folderId;

    /**
     * 콘텐츠 타입 — null 이면 host 에서 자동 추론.
     * 클라이언트가 명시적으로 보내면 그 값 우선 (예: 익스텐션이 페이지 메타 보고 ARTICLE 로 결정).
     */
    private String contentType;

    /** YouTube 카드 등에 노출할 썸네일 URL. 없으면 null. */
    private String thumbnailUrl;
}
