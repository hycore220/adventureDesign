package com.example.spring_boot_1.UserData;

import java.time.LocalTime;

/**
 * PUT /me/reminder-prefs 입력 DTO.
 *
 * 모든 필드 nullable — 보낸 필드만 갱신.
 * userId 는 인증된 사용자 기준으로 서버에서 결정 (요청 본문 무시).
 */
public record UserReminderPrefsRequest(
        Boolean dailyEnabled,
        LocalTime dailyTime,
        String timezone,
        Boolean weeklyEnabled,
        Boolean emailEnabled,
        Integer maxItemsPerReminder
) {
}
