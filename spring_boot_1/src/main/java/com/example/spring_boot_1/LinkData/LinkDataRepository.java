package com.example.spring_boot_1.LinkData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LinkDataRepository extends JpaRepository<LinkData, Integer> {
    List<LinkData> findByUserDataUserName(String userName);

    List<LinkData> findByFolderId(int folderId);

    /**
     * 본인 데이터에 한정해 title 또는 link(url)에 검색어가 포함된 링크를 찾는다.
     * 대소문자 무시 부분 일치. save-it 문서 §5.2 권장 동작.
     */
    @Query("""
            select l from LinkData l
            where l.userData.id = :userId
              and (lower(l.title) like lower(concat('%', :q, '%'))
                   or lower(l.link)  like lower(concat('%', :q, '%')))
            order by l.lastUpdate desc
            """)
    List<LinkData> searchForUser(@Param("userId") int userId, @Param("q") String q);

    /**
     * 컨텍스트 매칭(REMIND_STRATEGY §3.3) — 사용자 본인 + 특정 호스트로 저장된 링크.
     * host 정확 매칭. 사용자가 youtube.com 보고 있을 때 저장된 YouTube 영상 등.
     */
    List<LinkData> findByUserDataIdAndHost(int userId, String host);
}