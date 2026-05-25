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

    /** 사용자당 매 디지스트에 기록할 최대 후보 수. */
    @Value("${app.reminder.digest-size:5}")
    private int digestSize;

    public ReminderSchedulerService(
            UserDataRepository userDataRepository,
            ReminderCandidateService reminderCandidateService,
            LinkReminderRepository linkReminderRepository,
            LinkDataRepository linkDataRepository
    ) {
        this.userDataRepository = userDataRepository;
        this.reminderCandidateService = reminderCandidateService;
        this.linkReminderRepository = linkReminderRepository;
        this.linkDataRepository = linkDataRepository;
    }

    /**
     * 매일 09:00 KST에 모든 사용자의 today 후보 N개를 link_reminders 로 기록.
     * cron = "초 분 시 일 월 요일", 운영에선 zone/시각 환경변수 분리 권장.
     */
    @Scheduled(cron = "${app.reminder.daily-cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void dailyDigest() {
        log.info("리마인드 일간 디지스트 시작");
        int totalInserted = 0;
        int failedUsers = 0;

        List<UserData> users = userDataRepository.findAll();
        for (UserData user : users) {
            try {
                totalInserted += digestForUser(user);
            } catch (Exception ex) {
                failedUsers++;
                log.warn("사용자 {} 디지스트 실패: {}", user.getUserName(), ex.getMessage());
            }
        }

        log.info("리마인드 일간 디지스트 종료 — users={}, inserted={}, failed={}",
                users.size(), totalInserted, failedUsers);
    }

    /**
     * 단일 사용자에 대한 디지스트 — 명의를 SecurityContext 에 일시 주입하여
     * ReminderCandidateService 의 소유권 검증을 자연스럽게 통과시킨다.
     * 트랜잭션 경계는 사용자 단위 — 한 명 실패가 다음 사용자에 영향 없음.
     */
    @Transactional
    public int digestForUser(UserData user) {
        SecurityContext previous = SecurityContextHolder.getContext();
        try {
            assumeIdentityOf(user);
            List<RemindCandidateResponse> candidates =
                    reminderCandidateService.getCandidates(user.getUserName(), "today", digestSize);
            int inserted = 0;
            for (RemindCandidateResponse c : candidates) {
                LinkData link = linkDataRepository.findById(c.linkId()).orElse(null);
                if (link == null) continue;
                LinkReminder reminder = new LinkReminder();
                reminder.setUserData(user);
                reminder.setLinkData(link);
                reminder.setChannel("dashboard");
                reminder.setMode("daily");
                linkReminderRepository.save(reminder);
                inserted++;
            }
            return inserted;
        } finally {
            SecurityContextHolder.setContext(previous);
        }
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
