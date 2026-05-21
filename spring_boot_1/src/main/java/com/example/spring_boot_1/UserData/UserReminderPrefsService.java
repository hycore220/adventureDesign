package com.example.spring_boot_1.UserData;

import com.example.spring_boot_1.config.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * 사용자별 리마인드 설정 CRUD.
 *
 * 일반적 흐름:
 *   1. UserDataService.create 가 신규 가입 시 기본값 row 생성
 *   2. 사용자가 PUT /me/reminder-prefs 로 조정
 *   3. cron 디스패처가 findByDailyEnabledTrueAndDailyTimeAndTimezone 으로 발송 대상 추출
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserReminderPrefsService {

    private final UserReminderPrefsRepository repository;

    /** 신규 사용자 가입 시 기본값으로 row 생성. UserDataService.create 가 호출. */
    public UserReminderPrefs createDefault(int userId) {
        UserReminderPrefs prefs = new UserReminderPrefs();
        prefs.setUserId(userId);
        // 나머지 필드는 엔티티 기본값 사용 (Asia/Seoul 09:00, dailyEnabled=true 등)
        return repository.save(prefs);
    }

    @Transactional(readOnly = true)
    public UserReminderPrefs getMine() {
        int userId = SecurityUtil.currentUserId();
        return repository.findById(userId)
                .orElseGet(() -> {
                    // 베타 마이그레이션 호환 — 기존 사용자엔 row 없을 수 있음
                    UserReminderPrefs prefs = new UserReminderPrefs();
                    prefs.setUserId(userId);
                    return repository.save(prefs);
                });
    }

    public UserReminderPrefs updateMine(UserReminderPrefsRequest request) {
        UserReminderPrefs prefs = getMine();
        if (request.dailyEnabled() != null) prefs.setDailyEnabled(request.dailyEnabled());
        if (request.dailyTime() != null) prefs.setDailyTime(request.dailyTime());
        if (request.timezone() != null) prefs.setTimezone(request.timezone());
        if (request.weeklyEnabled() != null) prefs.setWeeklyEnabled(request.weeklyEnabled());
        if (request.emailEnabled() != null) prefs.setEmailEnabled(request.emailEnabled());
        if (request.maxItemsPerReminder() != null) {
            int v = request.maxItemsPerReminder();
            if (v < 1 || v > 20) {
                throw new IllegalArgumentException("maxItemsPerReminder 는 1~20 사이여야 합니다.");
            }
            prefs.setMaxItemsPerReminder(v);
        }
        return repository.save(prefs);
    }
}
