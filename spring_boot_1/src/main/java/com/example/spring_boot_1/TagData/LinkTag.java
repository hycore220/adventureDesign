package com.example.spring_boot_1.TagData;

import com.example.spring_boot_1.LinkData.LinkData;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 링크 ↔ 태그 M:N 정션 — ERD §4.3 `link_tags`.
 *
 * 복합 PK (link_id, tag_id). 네비게이션(@ManyToOne)으로 FK 를 생성하되,
 * insertable/updatable=false 로 두어 식별 컬럼은 int 필드로 직접 다룬다.
 *
 * NOTE: ddl-auto=update 는 ON DELETE CASCADE 를 발행하지 못하므로,
 *       링크/태그 삭제 시 정션 정리는 서비스 레이어가 책임진다
 *       (ERD 의 cascade 정본 DDL 은 db/migration 마이그레이션 파일에 기록).
 */
@Entity
@Getter
@Setter
@Table(name = "link_tags", indexes = {
        @Index(name = "idx_link_tags_tag", columnList = "tag_id")
})
@IdClass(LinkTagId.class)
public class LinkTag {

    @Id
    @Column(name = "link_id")
    private int linkId;

    @Id
    @Column(name = "tag_id")
    private int tagId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_id", insertable = false, updatable = false)
    private LinkData linkData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", insertable = false, updatable = false)
    private Tag tag;

    public LinkTag() {
    }

    public LinkTag(int linkId, int tagId) {
        this.linkId = linkId;
        this.tagId = tagId;
    }
}
