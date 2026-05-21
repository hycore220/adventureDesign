package com.example.spring_boot_1.LinkData;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * 링크의 콘텐츠 타입 — ERD §4.2 v1 컬럼.
 *
 * 컨텍스트 매칭(REMIND_STRATEGY §3.3)에서 호스트만으로는 부족한
 * 경우(예: 뉴스/블로그를 ARTICLE 로 묶고 싶을 때) 사용한다.
 *
 * 저장 시점에 자동 추론하거나, 클라이언트가 명시적으로 보낸다.
 */
public enum ContentType {
    YOUTUBE,
    ARTICLE,
    GITHUB,
    OTHER;

    public static ContentType fromString(String raw) {
        if (raw == null || raw.isBlank()) return OTHER;
        try {
            return ContentType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }

    /**
     * URL 호스트만으로 가장 단순한 추론. v1 의 첫 단계 (ERD §8 열린 질문 — 분류 시점).
     * 향후 메타데이터 기반 분류로 고도화 가능.
     */
    public static ContentType inferFromHost(String host) {
        if (host == null) return OTHER;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("youtube.com") || h.equals("www.youtube.com")
                || h.equals("youtu.be") || h.equals("m.youtube.com")) {
            return YOUTUBE;
        }
        if (h.equals("github.com") || h.equals("www.github.com")) {
            return GITHUB;
        }
        // ARTICLE 자동 판별은 v1 후속 — 일단 OTHER
        return OTHER;
    }

    /** JPA lenient 매핑 — DB 에 어떤 케이스로 들어와도 enum 으로 읽힘. */
    @Converter(autoApply = false)
    public static class ContentTypeConverter implements AttributeConverter<ContentType, String> {
        @Override
        public String convertToDatabaseColumn(ContentType attribute) {
            return attribute == null ? null : attribute.name();
        }

        @Override
        public ContentType convertToEntityAttribute(String dbData) {
            return fromString(dbData);
        }
    }
}
