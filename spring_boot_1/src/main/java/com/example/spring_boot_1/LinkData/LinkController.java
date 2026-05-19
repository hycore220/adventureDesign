package com.example.spring_boot_1.LinkData;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/link")
public class LinkController {

    private final LinkDataService linkDataService;

    public LinkController(LinkDataService linkDataService) {
        this.linkDataService = linkDataService;
    }

    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody LinkRequest linkRequest) {
        linkDataService.create(linkRequest);
        return ResponseEntity.ok("링크 저장 완료");
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
}