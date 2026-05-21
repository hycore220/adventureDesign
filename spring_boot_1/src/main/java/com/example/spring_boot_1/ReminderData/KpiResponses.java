package com.example.spring_boot_1.ReminderData;

import java.time.LocalDateTime;
import java.util.List;

/**
 * KPI 응답 DTO 모음 — PRD §6 / REMIND_STRATEGY §9.
 */
public class KpiResponses {

    /** 채널 × 모드별 CTR (열람률) 한 행. */
    public record ChannelModeCtr(
            String channel,
            String mode,
            long sentCount,
            long openedCount,
            double ctr
    ) {}

    /** GET /metrics/me/reminders 전체 응답. */
    public record CtrSummary(
            LocalDateTime from,
            LocalDateTime to,
            long totalSent,
            long totalOpened,
            double overallCtr,
            List<ChannelModeCtr> byChannelMode
    ) {}

    /** GET /metrics/me/seven-day-click 응답. */
    public record SevenDayClickRate(
            LocalDateTime from,
            LocalDateTime to,
            long totalLinks,
            long clickedWithin7Days,
            double rate
    ) {}

    /** GET /metrics/me/completion 응답. */
    public record CompletionRate(
            LocalDateTime from,
            LocalDateTime to,
            long totalRemindedLinks,
            long completedLinks,
            double rate
    ) {}
}
