package com.example.spring_boot_1.UserData;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 사용자별 리마인드 알림 설정 — REMIND_STRATEGY §6.2 / ERD §4.5.
 *
 * 일일/주간 다이제스트, 발송 시간/타임존, 이메일 옵트인 등을 보관한다.
 * cron(예: Vercel Cron) 디스패처가 "지금이 보낼 시간인 사용자"를 결정할 때 사용.
 *
 * PK 가 user_id 라 1:1 매핑이 강제된다.
 */
@Entity
@Getter
@Setter
@Table(name = "user_reminder_prefs")
public class UserReminderPrefs {

    @Id
    @Column(name = "user_id")
    private int userId;

    @OneToOne
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserData userData;

    @Column(name = "daily_enabled", nullable = false)
    private boolean dailyEnabled = true;

    @Column(name = "daily_time", nullable = false)
    private LocalTime dailyTime = LocalTime.of(9, 0);

    @Column(nullable = false)
    private String timezone = "Asia/Seoul";

    @Column(name = "weekly_enabled", nullable = false)
    private boolean weeklyEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "max_items_per_reminder", nullable = false)
    private int maxItemsPerReminder = 5;
}
