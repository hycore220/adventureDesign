package com.example.spring_boot_1.config;

/**
 * 사용자별 일일 호출 한도를 초과했을 때 발생.
 * GlobalExceptionHandler 가 HTTP 429 로 매핑한다.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String operation;
    private final int limit;

    public RateLimitExceededException(String operation, int limit) {
        super("일일 한도(" + limit + "회)를 초과했습니다 — " + operation);
        this.operation = operation;
        this.limit = limit;
    }

    public String getOperation() {
        return operation;
    }

    public int getLimit() {
        return limit;
    }
}
