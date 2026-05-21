package com.example.spring_boot_1.ReminderData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LinkReminderRepository extends JpaRepository<LinkReminder, Integer> {
    List<LinkReminder> findByUserDataUserNameOrderBySentAtDesc(String userName);

    List<LinkReminder> findByLinkDataIdOrderBySentAtDesc(int linkId);

    List<LinkReminder> findByUserDataUserNameAndLinkDataIdOrderBySentAtDesc(String userName, int linkId);

    long countByLinkDataIdAndSentAtAfter(int linkId, LocalDateTime since);

    /**
     * 사용자의 모든 링크에 대해 since 이후 발송된 리마인드 개수를 link_id 별로 집계.
     * getReminderCandidates의 N+1 회피용. 결과는 [linkId, count] 튜플.
     */
    @Query("""
            select lr.linkData.id, count(lr)
            from LinkReminder lr
            where lr.userData.id = :userId
              and lr.sentAt > :since
            group by lr.linkData.id
            """)
    List<Object[]> countRecentByUserGroupedByLink(
            @Param("userId") int userId,
            @Param("since") LocalDateTime since
    );

    // ============================================================
    //  KPI 집계 — PRD §6 / REMIND_STRATEGY §9
    // ============================================================

    /**
     * 사용자의 채널/모드별 총 발송 수 및 열람 수 — CTR(opened/sent) 산출용.
     * 결과 튜플: [channel, mode, sentCount, openedCount]
     */
    @Query("""
            select lr.channel, lr.mode, count(lr), sum(case when lr.openedAt is not null then 1 else 0 end)
            from LinkReminder lr
            where lr.userData.id = :userId
              and lr.sentAt between :from and :to
            group by lr.channel, lr.mode
            """)
    List<Object[]> aggregateCtrByChannelMode(
            @Param("userId") int userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * 저장-후-7일 내 클릭률 — 저장된 링크 중 created_at + 7일 안에 사용자가
     * 해당 링크의 리마인드를 (한 번이라도) 열어본 비율.
     *
     * JPQL 의 날짜 연산 문법 호환성을 피하기 위해 native query 사용.
     * 결과 튜플: [totalLinks, linksWithClickWithin7d]
     */
    @Query(value = """
            select
                count(distinct ld.id) as total_links,
                count(distinct case when exists (
                    select 1 from link_reminders lr
                    where lr.link_data_id = ld.id
                      and lr.opened_at is not null
                      and lr.opened_at <= ld.created_at + interval '7 day'
                ) then ld.id end) as clicked_within_7d
            from link_data ld
            where ld.user_id = :userId
              and ld.created_at between :from and :to
            """, nativeQuery = true)
    List<Object[]> sevenDayClickRate(
            @Param("userId") int userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    /**
     * 리마인드 → is_read 전환율 — 리마인드 발송된 링크 중 사용자가 결국
     * is_read=true 로 만든 비율 (전환의 정의 = 어쨌든 읽음).
     *
     * 결과 튜플: [totalRemindedLinks, completedLinks]
     */
    @Query("""
            select
                count(distinct lr.linkData.id),
                count(distinct case when lr.linkData.isRead = true then lr.linkData.id else null end)
            from LinkReminder lr
            where lr.userData.id = :userId
              and lr.sentAt between :from and :to
            """)
    List<Object[]> completionRate(
            @Param("userId") int userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}
