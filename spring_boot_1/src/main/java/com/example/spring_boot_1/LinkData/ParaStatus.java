package com.example.spring_boot_1.LinkData;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Locale;

/**
 * PARA 분류 enum. DB엔 enum 이름(PROJECT/AREA/RESOURCE/ARCHIVE/UNSPECIFIED)으로 저장.
 *
 * 기존 데이터의 자유 문자열("Project", "project", "p", "프로젝트" 등)과 호환 위해
 * 부속 {@link ParaStatusConverter}가 lenient 매핑을 처리한다.
 *
 * 정책 매핑은 {@code com.example.spring_boot_1.RecommendationData.ScoringEngine}에 위임.
 */
public enum ParaStatus {
    PROJECT,
    AREA,
    RESOURCE,
    ARCHIVE,
    UNSPECIFIED;

    public static ParaStatus fromString(String value) {
        if (value == null) return UNSPECIFIED;
        String v = value.strip().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return UNSPECIFIED;
        return switch (v) {
            case "project", "projects", "p" -> PROJECT;
            case "area", "areas", "a" -> AREA;
            case "resource", "resources", "r" -> RESOURCE;
            case "archive", "archives" -> ARCHIVE;
            case "unspecified", "미지정", "none", "null" -> UNSPECIFIED;
            default -> UNSPECIFIED;
        };
    }

    /**
     * AttributeConverter — 컬럼 자체는 VARCHAR이지만 enum 이름을 저장하고,
     * 읽을 땐 lenient 매핑으로 기존 데이터까지 흡수.
     */
    @Converter(autoApply = false)
    public static class ParaStatusConverter implements AttributeConverter<ParaStatus, String> {
        @Override
        public String convertToDatabaseColumn(ParaStatus attribute) {
            return attribute == null ? UNSPECIFIED.name() : attribute.name();
        }

        @Override
        public ParaStatus convertToEntityAttribute(String dbData) {
            return ParaStatus.fromString(dbData);
        }
    }
}
