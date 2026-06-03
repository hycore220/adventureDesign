package com.example.spring_boot_1.ReminderData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.LinkData.LinkDataRepository;
import com.example.spring_boot_1.RecommendationData.RemindCandidateResponse;
import com.example.spring_boot_1.RecommendationData.ReminderCandidateService;
import com.example.spring_boot_1.UserData.UserData;
import com.example.spring_boot_1.UserData.UserDataRepository;
import com.example.spring_boot_1.config.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * REMIND_STRATEGY §3.1 — 매일 정해진 시간에 사용자별 today 후보를 산출하여
 * link_reminders 테이블에 기록한다 (channel=dashboard, mode=daily).
 *
 * save-it 문서의 "Vercel Cron → API Route → DB" 흐름에서 Cron+API 자리를
 * Spring 단일 프로세스 내 @Scheduled 잡으로 대체한 구현.
 *
 * 멀티 인스턴스 운영으로 확장될 때 (Fly 2대+) 락 미사용 → 중복 발송 우려가 있으므로
 * ShedLock / DB 락으로 단일 실행 보장 필요. 베타 단계엔 단일 인스턴스 가정.
 */
@Service
public class ReminderSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ReminderSchedulerService.class);

    private final UserDataRepository userDataRepository;
    private final ReminderCandidateService reminderCandidateService;
    private final LinkReminderRepository linkReminderRepository;
    private final LinkDataRepository linkDataRepository;
    private final com.example.spring_boot_1.PushData.PushService pushService;
    private final com.example.spring_boot_1.UserData.UserReminderPrefsRepository prefsRepository;

    /** 사용자당 매 디지스트에 기록할 최대 후보 수 (prefs 미설정 시 fallback). */
    @Value("${app.reminder.digest-size:5}")
    private int digestSize;

    public ReminderSchedulerService(
            UserDataRepository userDataRepository,
            ReminderCandidateService reminderCandidateService,
            LinkReminderRepository linkReminderRepository,
            LinkDataRepository linkDataRepository,
            com.example.spring_boot_1.PushData.PushService pushService,
            com.example.spring_boot_1.UserData.UserReminderPrefsRepository prefsRepository
    ) {
        this.userDataRepository = userDataRepository;
        this.reminderCandidateService = reminderCandidateService;
        this.linkReminderRepository = linkReminderRepository;
        this.linkDataRepository = linkDataRepository;
        this.pushService = pushService;
        this.prefsRepository = prefsRepository;
    }

    /**
     * 매시간 정각 (UTC) 틱 — 사용자별 timezone 기준으로 "지금이 발송 시각인지" 판단.
     *
     * REMIND_STRATEGY §3.1 개인화:
     *   - daily : user.dailyTime 의 '시(hour)' 가 그 사용자 timezone 의 현재 시와 일치하면 발사
     *   - weekly: user.weeklyDow + weeklyTime 의 시가 일치하면 발사
     *
     * 분(minute) 단위가 아니라 시(hour) 단위 매칭 — "09시대에 한 번" 의미.
     * 매시간 정각 1회 실행이므로 시 일치 = 하루 1회 → 중복 발송 없음.
     * (멀티 인스턴스로 가면 ShedLock 필요. 베타 단일 인스턴스 가정.)
     */
    @Scheduled(cron = "0 0 * * * *", zone = "UTC")
    public void hourlyTick() {
        java.time.Instant now = java.time.Instant.now();
        int dailySent = 0, weeklySent = 0, failed = 0;

        // ── daily ──
        for (var prefs : prefsRepository.findByDailyEnabledTrue()) {
            try {
                if (!isDueNow(prefs.getTimezone(), now, prefs.getDailyTime().getHour())) continue;
                UserData user = userDataRepository.findById(prefs.getUserId()).orElse(null);
                if (user == null) continue;
                int n = digestForUser(user, false, prefs.getMaxItemsPerReminder());
                if (n > 0) dailySent++;
            } catch (Exception ex) {
                failed++;
                log.warn("daily 틱 실패 userId={} : {}", prefs.getUserId(), ex.getMessage());
            }
        }

        // ── weekly ──
        for (var prefs : prefsRepository.findByWeeklyEnabledTrue()) {
            try {
                if (!isWeeklyDueNow(prefs.getTimezone(), now, prefs.getWeeklyDow(), prefs.getWeeklyTime().getHour()))
                    continue;
                UserData user = userDataRepository.findById(prefs.getUserId()).orElse(null);
                if (user == null) continue;
                if (weeklySummaryForUser(user)) weeklySent++;
            } catch (Exception ex) {
                failed++;
                log.warn("weekly 틱 실패 userId={} : {}", prefs.getUserId(), ex.getMessage());
            }
        }

        if (dailySent + weeklySent + failed > 0) {
            log.info("리마인드 틱 — daily={}, weekly={}, failed={}", dailySent, weeklySent, failed);
        }
    }

    /** 해당 timezone 의 현재 '시' 가 targetHour 와 같으면 true. */
    private boolean isDueNow(String timezone, java.time.Instant now, int targetHour) {
        java.time.ZonedDateTime local = now.atZone(safeZone(timezone));
        return local.getHour() == targetHour;
    }

    /** 해당 timezone 의 현재 요일+시 가 target 과 같으면 true. dow: 1(월)~7(일). */
    private boolean isWeeklyDueNow(String timezone, java.time.Instant now, int targetDow, int targetHour) {
        java.time.ZonedDateTime local = now.atZone(safeZone(timezone));
        return local.getDayOfWeek().getValue() == targetDow && local.getHour() == targetHour;
    }

    private java.time.ZoneId safeZone(String tz) {
        try {
            return java.time.ZoneId.of(tz);
        } catch (Exception e) {
            return java.time.ZoneId.of("Asia/Seoul");
        }
    }

    /**
     * 단일 사용자에 대한 디지스트 — 명의를 SecurityContext 에 일시 주입하여
     * ReminderCandidateService 의 소유권 검증을 자연스럽게 통과시킨다.
     * 트랜잭션 경계는 사용자 단위 — 한 명 실패가 다음 사용자에 영향 없음.
     */
    /** 기존 시그니처 호환 — daily, 기본 size. */
    @Transactional
    public int digestForUser(UserData user) {
        return digestForUser(user, false, digestSize);
    }

    /**
     * 단일 사용자 디지스트. weekly=true 면 mode=weekly + 더 큰 묶음 + 다른 push 라벨.
     * 명의를 SecurityContext 에 일시 주입하여 소유권 검증을 통과시킨다.
     */
    @Transactional
    public int digestForUser(UserData user, boolean weekly, int size) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            assumeIdentityOf(user);
            int limit = size > 0 ? size : digestSize;
            List<RemindCandidateResponse> candidates =
                    reminderCandidateService.getCandidates(user.getUserName(), "today", limit);
            int inserted = 0;
            String previewTitle = null;
            String mode = weekly ? "weekly" : "daily";
            for (RemindCandidateResponse c : candidates) {
                LinkData link = linkDataRepository.findById(c.linkId()).orElse(null);
                if (link == null) continue;
                LinkReminder reminder = new LinkReminder();
                reminder.setUserData(user);
                reminder.setLinkData(link);
                reminder.setChannel("dashboard");
                reminder.setMode(mode);
                linkReminderRepository.save(reminder);
                if (previewTitle == null) previewTitle = link.getTitle();
                inserted++;
            }
            if (inserted > 0 && pushService.isEnabled()) {
                try {
                    var payload = weekly
                            ? com.example.spring_boot_1.PushData.PushService.Payload.weeklyDigest(inserted, previewTitle)
                            : com.example.spring_boot_1.PushData.PushService.Payload.todayDigest(inserted, previewTitle);
                    var r = pushService.sendToUser(user.getId(), payload);
                    log.info("push 발사 [{}] — user={} sent={} removed={} failed={}",
                            mode, user.getUserName(), r.sent(), r.removed(), r.failed());
                } catch (Exception ex) {
                    log.warn("push 발사 예외 user={} : {}", user.getUserName(), ex.getMessage());
                }
            }
            return inserted;
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    /**
     * 주간 회고 — 개별 링크가 아니라 "이번 주 통계 요약" push 발사.
     * 매일 디지스트와 성격이 다름: 활동/누적 현황 (저장 N · 미열람 M).
     * @return push 가 1대 이상 발사됐으면 true
     */
    @Transactional(readOnly = true)
    public boolean weeklySummaryForUser(UserData user) {
        long savedThisWeek = linkDataRepository.countSavedSince(
                user.getId(), java.time.LocalDateTime.now().minusDays(7));
        long totalUnread = linkDataRepository.countUnread(user.getId());

        if (!pushService.isEnabled()) return false;
        var payload = com.example.spring_boot_1.PushData.PushService.Payload
                .weeklySummary(savedThisWeek, totalUnread);
        var r = pushService.sendToUser(user.getId(), payload);
        log.info("weekly summary push — user={} saved={} unread={} sent={}",
                user.getUserName(), savedThisWeek, totalUnread, r.sent());
        return r.sent() > 0;
    }

    private void assumeIdentityOf(UserData user) {
        UserPrincipal principal = new UserPrincipal(user.getId(), user.getUserName());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
