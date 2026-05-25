package com.example.spring_boot_1.UserData;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /auth/api-tokens — 인증된 사용자의 API 토큰 관리.
 *
 * - POST /auth/api-tokens         : 새 토큰 발급 (응답에 평문 1회만 노출)
 * - GET  /auth/api-tokens         : 본인 토큰 목록 (해시 안 노출)
 * - DELETE /auth/api-tokens/{id}  : 토큰 폐기 (revokedAt 세팅)
 *
 * 인증은 세션 또는 기존 토큰 둘 다 가능 (SecurityConfig 가 통합 처리).
 */
@RestController
@RequestMapping("/auth/api-tokens")
@RequiredArgsConstructor
public class ApiTokenController {

    private final ApiTokenService service;

    @PostMapping
    public ResponseEntity<ApiTokenService.IssueResult> issue(@RequestBody IssueRequest request) {
        return ResponseEntity.ok(service.issue(request.name()));
    }

    @GetMapping
    public ResponseEntity<List<ApiTokenService.ApiTokenSummary>> listMine() {
        return ResponseEntity.ok(service.listMine());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> revoke(@PathVariable int id) {
        service.revoke(id);
        return ResponseEntity.ok("폐기 완료");
    }

    public record IssueRequest(String name) {}
}
