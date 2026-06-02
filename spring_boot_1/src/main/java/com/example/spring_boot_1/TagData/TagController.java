package com.example.spring_boot_1.TagData;

import com.example.spring_boot_1.LinkData.LinkResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 사용자 자유 태그 API — ERD §4.3 (수동 부착 방식).
 *
 * 모든 경로는 SecurityFilterChain 의 anyRequest().authenticated() 로 인증 필수.
 */
@RestController
@RequestMapping("/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    /** 태그 생성. */
    @PostMapping
    public ResponseEntity<TagResponse> create(@RequestBody TagRequest request) {
        return ResponseEntity.ok(tagService.create(request.name()));
    }

    /** 내 태그 목록. */
    @GetMapping
    public ResponseEntity<List<TagResponse>> list() {
        return ResponseEntity.ok(tagService.listMine());
    }

    /** 태그 삭제 (붙어 있던 링크 연결도 함께 정리). */
    @DeleteMapping("/{tagId}")
    public ResponseEntity<String> delete(@PathVariable int tagId) {
        tagService.delete(tagId);
        return ResponseEntity.ok("태그 삭제 완료");
    }

    /** 링크에 태그 부착. */
    @PostMapping("/{tagId}/links/{linkId}")
    public ResponseEntity<String> attach(@PathVariable int tagId, @PathVariable int linkId) {
        tagService.attach(tagId, linkId);
        return ResponseEntity.ok("태그 부착 완료");
    }

    /** 링크에서 태그 해제. */
    @DeleteMapping("/{tagId}/links/{linkId}")
    public ResponseEntity<String> detach(@PathVariable int tagId, @PathVariable int linkId) {
        tagService.detach(tagId, linkId);
        return ResponseEntity.ok("태그 해제 완료");
    }

    /** 태그로 링크 필터. */
    @GetMapping("/{tagId}/links")
    public ResponseEntity<List<LinkResponse>> linksByTag(@PathVariable int tagId) {
        return ResponseEntity.ok(tagService.linksByTag(tagId));
    }

    /** 특정 링크에 달린 태그들. */
    @GetMapping("/by-link/{linkId}")
    public ResponseEntity<List<TagResponse>> tagsByLink(@PathVariable int linkId) {
        return ResponseEntity.ok(tagService.tagsByLink(linkId));
    }
}
