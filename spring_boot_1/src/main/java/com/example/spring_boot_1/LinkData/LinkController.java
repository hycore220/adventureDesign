package com.example.spring_boot_1.LinkData;

import com.example.spring_boot_1.config.RateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/link")
public class LinkController {

    private final LinkDataService linkDataService;
    private final RateLimiterService rateLimiter;

    public LinkController(LinkDataService linkDataService, RateLimiterService rateLimiter) {
        this.linkDataService = linkDataService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/create")
    public ResponseEntity<LinkResponse> create(@RequestBody LinkRequest linkRequest) {
        rateLimiter.acquire(RateLimiterService.OP_LINK_CREATE);
        return ResponseEntity.ok(linkDataService.create(linkRequest));
    }

    @GetMapping("/user/{userName}")
    public ResponseEntity<List<LinkResponse>> getByUserName(@PathVariable String userName) {
        return ResponseEntity.ok(linkDataService.getByUserName(userName));
    }

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<LinkResponse>> getByFolderId(@PathVariable int folderId) {
        return ResponseEntity.ok(linkDataService.getByFolderId(folderId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable int id, @RequestBody LinkRequest linkRequest) {
        linkDataService.update(id, linkRequest);
        return ResponseEntity.ok("링크 수정 및 이동 완료");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable int id) {
        linkDataService.delete(id);
        return ResponseEntity.ok("링크 삭제 완료");
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<String> markRead(@PathVariable int id) {
        linkDataService.markRead(id);
        return ResponseEntity.ok("읽음 처리 완료");
    }

    /**
     * 본인 링크 검색. title 또는 url 부분일치, 대소문자 무시, 최대 50개.
     * GET /link/search?q=keyword
     */
    @GetMapping("/search")
    public ResponseEntity<List<LinkResponse>> search(@RequestParam String q) {
        return ResponseEntity.ok(linkDataService.search(q));
    }
}