package com.example.spring_boot_1.FolderData;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 폴더 정보 갱신 — name / paraCategory.
     * paraCategory 변경은 소속 링크들에 cascade 동기화 (ERD §1.1).
     */
    @PutMapping("/{id}")
    public ResponseEntity<FolderResponse> updateFolder(
            @PathVariable int id,
            @RequestBody FolderUpdateRequest request
    ) {
        return ResponseEntity.ok(folderService.updateFolder(id, request));
    }
}