package com.example.spring_boot_1.TagData;

import java.time.LocalDateTime;

public record TagResponse(int id, String name, LocalDateTime createdAt) {

    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getCreatedAt());
    }
}
