package com.example.spring_boot_1;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 운영용 헬스 체크.
 *
 * /health 는 SecurityConfig 의 permitAll 에 추가되어야 로드밸런서/모니터링이
 * 인증 없이 접근 가능. 현재는 인증 필요한 상태 — 베타에선 그대로 둠.
 */
@RestController
@RequestMapping("/")
public class MainController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
