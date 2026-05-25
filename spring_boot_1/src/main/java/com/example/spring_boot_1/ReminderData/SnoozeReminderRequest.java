package com.example.spring_boot_1.ReminderData;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SnoozeReminderRequest {
    private LocalDateTime snoozedUntil;
}
