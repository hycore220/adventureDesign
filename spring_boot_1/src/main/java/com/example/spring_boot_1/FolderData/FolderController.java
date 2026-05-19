package com.example.spring_boot_1.FolderData;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequiredArgsConstructor
@RequestMapping("/folder")
public class FolderController {

    private final FolderService folderService;

    // 반환 타입을 FolderResponse DTO 리스트로 변경
    @GetMapping("/user/{userName}")
    public ResponseEntity<List<FolderResponse>> getRootFolders(@PathVariable String userName) {
        return ResponseEntity.ok(folderService.getRootFolders(userName));
    }

    // 개선된 캡슐화 DTO 구조 반환
    @GetMapping("/{folderId}/contents")
    public ResponseEntity<FolderContentsResponse> getFolderContents(@PathVariable int folderId) {
        return ResponseEntity.ok(folderService.getFolderContents(folderId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable int id) {
        folderService.deleteFolder(id);
        return ResponseEntity.ok("폴더 및 내부 데이터 삭제 완료");
    }

    @PostMapping("/create")
    public ResponseEntity<String> createFolder(@RequestBody FolderRequest folderRequest) {
        folderService.createFolder(folderRequest);
        return ResponseEntity.ok("폴더 생성 완료");
    }
}