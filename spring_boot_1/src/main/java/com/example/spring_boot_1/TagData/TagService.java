package com.example.spring_boot_1.TagData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.LinkDataRepository;
import com.example.spring_boot_1.LinkData.LinkResponse;
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import com.example.spring_boot_1.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 사용자 자유 태그 CRUD + 링크 부착/해제 — ERD §4.3.
 *
 * 모든 연산은 현재 인증 사용자 소유 자원으로 강제된다
 * (Supabase RLS 대신 SecurityUtil 소유권 검증).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TagService {

    private final TagRepository tagRepository;
    private final LinkTagRepository linkTagRepository;
    private final LinkDataRepository linkDataRepository;
    private final UserDataRepository userDataRepository;

    /** 태그 생성 — 이름 정규화 + 사용자별 중복(대소문자 무시) 금지. */
    public TagResponse create(String rawName) {
        String name = normalize(rawName);
        int userId = SecurityUtil.currentUserId();
        if (tagRepository.existsByUserData_IdAndNameIgnoreCase(userId, name)) {
            throw new IllegalArgumentException("이미 존재하는 태그입니다: " + name);
        }
        UserData user = userDataRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        Tag tag = new Tag();
        tag.setUserData(user);
        tag.setName(name);
        return TagResponse.from(tagRepository.save(tag));
    }

    @Transactional(readOnly = true)
    public List<TagResponse> listMine() {
        return tagRepository.findByUserData_IdOrderByNameAsc(SecurityUtil.currentUserId())
                .stream().map(TagResponse::from).toList();
    }

    /** 태그 삭제 — 정션(link_tags) 먼저 정리 후 태그 제거. */
    public void delete(int tagId) {
        Tag tag = requireOwnedTag(tagId);
        linkTagRepository.deleteByTagId(tagId);
        tagRepository.delete(tag);
    }

    /** 링크에 태그 부착 (멱등 — 이미 있으면 no-op). */
    public void attach(int tagId, int linkId) {
        requireOwnedTag(tagId);
        requireOwnedLink(linkId);
        if (!linkTagRepository.existsByLinkIdAndTagId(linkId, tagId)) {
            linkTagRepository.save(new LinkTag(linkId, tagId));
        }
    }

    /** 링크에서 태그 해제. */
    public void detach(int tagId, int linkId) {
        requireOwnedTag(tagId);
        requireOwnedLink(linkId);
        linkTagRepository.deleteByLinkIdAndTagId(linkId, tagId);
    }

    /** 특정 태그가 붙은 내 링크들 (자유 필터). */
    @Transactional(readOnly = true)
    public List<LinkResponse> linksByTag(int tagId) {
        requireOwnedTag(tagId);
        List<Integer> linkIds = linkTagRepository.findByTagId(tagId).stream()
                .map(LinkTag::getLinkId).toList();
        return linkDataRepository.findAllById(linkIds).stream()
                .map(LinkResponse::from).toList();
    }

    /** 특정 링크에 달린 태그들. */
    @Transactional(readOnly = true)
    public List<TagResponse> tagsByLink(int linkId) {
        requireOwnedLink(linkId);
        List<Integer> tagIds = linkTagRepository.findByLinkId(linkId).stream()
                .map(LinkTag::getTagId).toList();
        return tagRepository.findAllById(tagIds).stream()
                .map(TagResponse::from).toList();
    }

    // ── 내부 헬퍼 ───────────────────────────────────────────────

    private Tag requireOwnedTag(int tagId) {
        return tagRepository.findByIdAndUserData_Id(tagId, SecurityUtil.currentUserId())
                .orElseThrow(() -> new AccessDeniedException("해당 태그의 소유자가 아니거나 존재하지 않습니다."));
    }

    private void requireOwnedLink(int linkId) {
        LinkData link = linkDataRepository.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 링크 id입니다."));
        if (link.getUserData() == null
                || link.getUserData().getId() != SecurityUtil.currentUserId()) {
            throw new AccessDeniedException("해당 링크의 소유자가 아닙니다.");
        }
    }

    private static String normalize(String name) {
        if (name == null) throw new IllegalArgumentException("태그 이름이 필요합니다.");
        String n = name.trim();
        if (n.isEmpty()) throw new IllegalArgumentException("태그 이름이 비어 있습니다.");
        if (n.length() > 50) throw new IllegalArgumentException("태그 이름은 50자 이하여야 합니다.");
        return n;
    }
}
