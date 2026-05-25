package com.example.spring_boot_1.ReminderData;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class LinkReminderRequest {
    private Integer linkId;
    private String userName;
    private String channel;
    private String mode;
    private LocalDateTime snoozedUntil;
}
