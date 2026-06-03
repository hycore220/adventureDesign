package com.example.spring_boot_1.UserData;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalTime;
import java.util.List;

public interface UserReminderPrefsRepository extends JpaRepository<UserReminderPrefs, Integer> {

    /**
     * cron 디스패처용 — 특정 (시간, 타임존) 조합에 일일 다이제스트를 받는 사용자.
     * 매시간 정각에 호출되어 "지금 발송 대상" 을 추려낸다.
     */
    List<UserReminderPrefs> findByDailyEnabledTrueAndDailyTimeAndTimezone(LocalTime dailyTime, String timezone);

    /** 매시간 틱 — daily 켜진 전체. 각자 timezone 으로 "지금 시각" 비교는 코드에서. */
    List<UserReminderPrefs> findByDailyEnabledTrue();

    /** 매시간 틱 — weekly 켜진 전체. */
    List<UserReminderPrefs> findByWeeklyEnabledTrue();
}
