package com.example.spring_boot_1.ReminderData;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LinkReminderResponse {
    private int id;
    private int linkId;
    private String userName;
    private String link;
    private String title;
    private String channel;
    private String mode;
    private LocalDateTime sentAt;
    private LocalDateTime openedAt;
    private LocalDateTime snoozedUntil;

    public static LinkReminderResponse from(LinkReminder reminder) {
        return new LinkReminderResponse(
                reminder.getId(),
                reminder.getLinkData().getId(),
                reminder.getUserData().getUserName(),
                reminder.getLinkData().getLink(),
                reminder.getLinkData().getTitle(),
                reminder.getChannel(),
                reminder.getMode(),
                reminder.getSentAt(),
                reminder.getOpenedAt(),
                reminder.getSnoozedUntil()
        );
    }
}
