package com.example.spring_boot_1.RecommendationData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.ParaStatus;

import java.time.LocalDateTime;

/**
 * "오늘 다시 볼 링크" 추천 응답.
 * 점수와 분해된 신호를 함께 돌려준다. 프론트는 점수만 써도 되고,
 * 분해값으로 "왜 보여주는가" 라벨을 직접 만들어도 된다.
 */
public record RemindCandidateResponse(
        int linkId,
        String link,
        String title,
        String paraStatus,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime readAt,
        LocalDateTime lastUpdate,
        String mode,
        String reason,
        double remindScore,
        ScoreBreakdown breakdown
) {

    public record ScoreBreakdown(
            double paraMultiplier,
            double unreadFactor,
            double importance,
            double bellRecency,
            double fatigueFactor,
            double rawScore,
            long ageDays,
            int recentRemindCount
    ) {
    }

    public static RemindCandidateResponse of(
            LinkData link,
            String mode,
            String reason,
            double remindScore,
            ScoreBreakdown breakdown
    ) {
        ParaStatus para = link.getPARAStatus();
        return new RemindCandidateResponse(
                link.getId(),
                link.getLink(),
                link.getTitle(),
                para == null ? null : para.name(),
                link.isRead(),
                link.getCreatedAt(),
                link.getReadAt(),
                link.getLastUpdate(),
                mode,
                reason,
                remindScore,
                breakdown
        );
    }
}
