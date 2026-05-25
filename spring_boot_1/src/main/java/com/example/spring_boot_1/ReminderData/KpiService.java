package com.example.spring_boot_1.ReminderData;

import com.example.spring_boot_1.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 인증된 사용자 본인의 리마인드 KPI 집계.
 *
 * 정의는 PRD §6 / REMIND_STRATEGY §9 를 따른다.
 *   1. CTR: opened_at / sent_at 비율 (채널×모드별)
 *   2. 7일 클릭률: 저장-후-7일 내 한 번이라도 열람한 비율
 *   3. 전환율: 리마인드 받은 링크 중 is_read=true 가 된 비율
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KpiService {

    private final LinkReminderRepository linkReminderRepository;

    public KpiResponses.CtrSummary ctrSummary(LocalDateTime from, LocalDateTime to) {
        int userId = SecurityUtil.currentUserId();
        List<Object[]> rows = linkReminderRepository.aggregateCtrByChannelMode(userId, from, to);

        long totalSent = 0;
        long totalOpened = 0;
        List<KpiResponses.ChannelModeCtr> byChannelMode = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            String channel = (String) row[0];
            String mode = (String) row[1];
            long sent = ((Number) row[2]).longValue();
            long opened = ((Number) row[3]).longValue();
            totalSent += sent;
            totalOpened += opened;
            double ctr = sent == 0 ? 0.0 : round4((double) opened / sent);
            byChannelMode.add(new KpiResponses.ChannelModeCtr(channel, mode, sent, opened, ctr));
        }

        double overall = totalSent == 0 ? 0.0 : round4((double) totalOpened / totalSent);
        return new KpiResponses.CtrSummary(from, to, totalSent, totalOpened, overall, byChannelMode);
    }

    public KpiResponses.SevenDayClickRate sevenDayClickRate(LocalDateTime from, LocalDateTime to) {
        int userId = SecurityUtil.currentUserId();
        List<Object[]> rows = linkReminderRepository.sevenDayClickRate(userId, from, to);
        long total = 0;
        long clicked = 0;
        if (!rows.isEmpty()) {
            Object[] row = rows.get(0);
            total = ((Number) row[0]).longValue();
            clicked = ((Number) row[1]).longValue();
        }
        double rate = total == 0 ? 0.0 : round4((double) clicked / total);
        return new KpiResponses.SevenDayClickRate(from, to, total, clicked, rate);
    }

    public KpiResponses.CompletionRate completionRate(LocalDateTime from, LocalDateTime to) {
        int userId = SecurityUtil.currentUserId();
        List<Object[]> rows = linkReminderRepository.completionRate(userId, from, to);
        long total = 0;
        long completed = 0;
        if (!rows.isEmpty()) {
            Object[] row = rows.get(0);
            total = ((Number) row[0]).longValue();
            completed = ((Number) row[1]).longValue();
        }
        double rate = total == 0 ? 0.0 : round4((double) completed / total);
        return new KpiResponses.CompletionRate(from, to, total, completed, rate);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
