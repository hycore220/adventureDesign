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

    public static LinkResponse from(LinkData link) {
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
                link.getThumbnailUrl()
        );
    }
}
