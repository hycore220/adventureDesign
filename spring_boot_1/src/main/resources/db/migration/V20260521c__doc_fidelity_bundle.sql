-- ============================================================
--  문서 충실도 7개 항목 데이터 backfill (2026-05-21)
--
--  스키마 변경(컬럼/인덱스/테이블 추가)은 Hibernate ddl-auto=update 가
--  엔티티 변경을 보고 자동 적용한다. 이 파일은 **데이터 backfill 만** 담는다.
--
--  실행 순서: Spring 부팅 → Hibernate 가 DDL 적용 → 이 SQL 수동 실행.
--
--  적용 대상:
--    1. folder.para_category — 기존 폴더에 이름 기반 PARA 매핑
--    2. link_data.host / content_type — 기존 링크 URL 에서 자동 추출 백필
--    3. user_reminder_prefs — 기존 사용자에게 기본 row 보장
-- ============================================================

-- ============================================================
-- (1) folder.para_category backfill
-- ============================================================
UPDATE folder SET para_category = 'PROJECT'  WHERE para_category IS NULL AND name = 'Project';
UPDATE folder SET para_category = 'AREA'     WHERE para_category IS NULL AND name = 'Area';
UPDATE folder SET para_category = 'RESOURCE' WHERE para_category IS NULL AND name = 'Resources';
UPDATE folder SET para_category = 'ARCHIVE'  WHERE para_category IS NULL AND name = 'Archive';

-- 하위 폴더는 부모의 PARA 를 상속
UPDATE folder child
    JOIN folder parent ON child.parent_id = parent.id
SET child.para_category = parent.para_category
WHERE child.para_category IS NULL
  AND parent.para_category IS NOT NULL;

-- ============================================================
-- (2) link_data.host / content_type backfill
-- ============================================================
-- URL 에서 호스트 추출. https://www.example.com/path 같은 형식 가정.
UPDATE link_data
SET host = LOWER(SUBSTRING_INDEX(SUBSTRING_INDEX(link, '://', -1), '/', 1))
WHERE host IS NULL AND link LIKE '%://%';

UPDATE link_data SET content_type = 'YOUTUBE'
WHERE content_type IS NULL
  AND host IN ('youtube.com', 'www.youtube.com', 'youtu.be', 'm.youtube.com');

UPDATE link_data SET content_type = 'GITHUB'
WHERE content_type IS NULL
  AND host IN ('github.com', 'www.github.com');

UPDATE link_data SET content_type = 'OTHER'
WHERE content_type IS NULL;

-- ============================================================
-- (3) user_reminder_prefs 기본 row — 기존 사용자에게 자동 생성
--    (신규 가입자는 UserDataService.create 가 처리)
-- ============================================================
INSERT IGNORE INTO user_reminder_prefs (
    user_id, daily_enabled, daily_time, timezone,
    weekly_enabled, email_enabled, max_items_per_reminder
)
SELECT id, TRUE, '09:00:00', 'Asia/Seoul', TRUE, FALSE, 5
FROM user_data;

-- ============================================================
-- 참고: api_tokens 테이블은 Hibernate 가 빈 테이블로 자동 생성.
-- 기존 사용자가 익스텐션을 쓰려면 /auth/api-tokens POST 로 새로 발급해야 함.
-- ============================================================
