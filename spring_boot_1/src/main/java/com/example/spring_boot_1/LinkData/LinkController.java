package com.example.spring_boot_1.LinkData;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
@RequestMapping("/link")
public class LinkController {

    private final LinkDataService linkDataService;

    @PostMapping("/create")
    public ResponseEntity<String> create(@RequestBody LinkData linkData) {
        linkDataService.create(linkData.getLink(), linkData.getUserName(), linkData.getPARAStatus());
        return ResponseEntity.ok("저장 완료");
    }

    @GetMapping("/{userName}")
    public ResponseEntity<List<LinkData>> getByUserName(@PathVariable String userName) {
        return ResponseEntity.ok(linkDataService.getByUserName(userName));
    }

    // 수정
    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable int id, @RequestBody LinkData linkData) {
        linkDataService.update(id, linkData.getLink(), linkData.getUserName(), linkData.getPARAStatus());
        return ResponseEntity.ok("수정 완료");
    }

    // 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable int id) {
        linkDataService.delete(id);
        return ResponseEntity.ok("삭제 완료");
    }
}