package com.example.spring_boot_1.TagData;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link LinkTag} 복합 키 — ERD §4.3 link_tags PK (link_id, tag_id).
 * @IdClass 규약상 필드명/타입이 엔티티의 @Id 필드(linkId, tagId)와 일치해야 한다.
 */
public class LinkTagId implements Serializable {

    private int linkId;
    private int tagId;

    public LinkTagId() {
    }

    public LinkTagId(int linkId, int tagId) {
        this.linkId = linkId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinkTagId that)) return false;
        return linkId == that.linkId && tagId == that.tagId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(linkId, tagId);
    }
}
