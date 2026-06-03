package com.example.spring_boot_1.LinkData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LinkResponse {
    private int id;
    private String link;
    private String title;
    /** PARA 분류. JSON에선 enum 이름 문자열(PROJECT/AREA/...)로 노출. */
    private String paraStatus;
    private LocalDateTime lastUpdate;
    private LocalDateTime createdAt;
    private boolean isRead;
    private LocalDateTime readAt;

    // ERD §4.2 v1 / 컨텍스트 매칭(REMIND §3.3) — 익스텐션 UI 에 노출
    private String host;
    private String contentType;
    private String thumbnailUrl;

    /** 우선도 (문서 ERD priority): 0=보통 / 1=중요 / 2=매우. importance 에서 역매핑. */
    private int priority;

    /** 우선도 모를 때(0=보통) 기본. */
    public static LinkResponse from(LinkData link) {
        return from(link, 0);
    }

    public static LinkResponse from(LinkData link, int priority) {
        ParaStatus para = link.getPARAStatus();
        ContentType ct = link.getContentType();
        return new LinkResponse(
                link.getId(),
                link.getLink(),
                link.getTitle(),
                para == null ? null : para.name(),
                link.getLastUpdate(),
                link.getCreatedAt(),
                link.isRead(),
                link.getReadAt(),
                link.getHost(),
                ct == null ? null : ct.name(),
                link.getThumbnailUrl(),
                priority
        );
    }
}
