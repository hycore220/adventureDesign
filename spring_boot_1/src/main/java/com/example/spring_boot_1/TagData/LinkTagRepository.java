package com.example.spring_boot_1.TagData;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LinkTagRepository extends JpaRepository<LinkTag, LinkTagId> {

    boolean existsByLinkIdAndTagId(int linkId, int tagId);

    void deleteByLinkIdAndTagId(int linkId, int tagId);

    /** 태그 삭제 시 정션 정리. */
    void deleteByTagId(int tagId);

    /** 링크 삭제 시 정션 정리 (FK 무결성 — LinkDataService.delete 에서 호출). */
    void deleteByLinkId(int linkId);

    List<LinkTag> findByLinkId(int linkId);

    List<LinkTag> findByTagId(int tagId);
}
