package com.example.spring_boot_1.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 전역 예외 핸들러.
 * - IllegalArgumentException → 400 (입력값 잘못, "존재하지 않는 X id" 류 포함)
 * - AccessDeniedException → 403 (소유권 검증 실패)
 * - IllegalStateException → 401 (SecurityContext 비어있음 등 비정상 상태)
 * - 기타 RuntimeException → 500 (메시지는 노출하지 않음)
 *
 * 응답 형태: { "error": "<code>", "message": "<safe message>" }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "bad_request",
                        "message", safeMessage(e)
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "forbidden",
                        "message", safeMessage(e, "권한이 없습니다.")
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(IllegalStateException e) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "unauthorized",
                        "message", safeMessage(e, "로그인이 필요합니다.")
                ));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException e) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                        "error", "forbidden",
                        "message", safeMessage(e, "권한이 없습니다.")
                ));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "error", "rate_limit_exceeded",
                        "operation", e.getOperation(),
                        "limit", String.valueOf(e.getLimit()),
                        "message", e.getMessage()
                ));
    }

    // OpenAiBudgetExceededException 핸들러는 2026-05-21 OpenAI 의존성 제거에 따라 폐기됨.

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleGeneric(RuntimeException e) {
        // 운영 로그엔 스택트레이스 남기지만, 응답엔 안 노출
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "internal_error",
                        "message", "처리 중 오류가 발생했습니다."
                ));
    }

    private String safeMessage(Exception e) {
        return safeMessage(e, "잘못된 요청입니다.");
    }

    private String safeMessage(Exception e, String fallback) {
        String msg = e.getMessage();
        return (msg == null || msg.isBlank()) ? fallback : msg;
    }
}
