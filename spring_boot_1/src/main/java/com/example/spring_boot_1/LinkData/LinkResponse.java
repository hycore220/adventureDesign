package com.example.spring_boot_1.LinkData;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LinkResponse {
    private int id;
    private String link;
    private String title;
    private String paraStatus;
    private LocalDateTime lastUpdate;
}