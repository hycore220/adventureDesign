-- ============================================================
--  RecommendationWeight: snooze (BOOLEAN) → snoozed_until (DATETIME)
--
--  목적:
--    - 기존: snooze=true 는 "영구 음소거"였고 사용자가 직접 끄지 않으면 영영 추천 제외.
--    - 변경 후: snoozed_until 만료 시각 (NULL = 음소거 아님, 미래 = 그때까지 제외).
--    - 영구 음소거는 9999-12-31 sentinel 로 표현.
--
--  실행 환경:
--    - MySQL 8 기준.
--    - ddl-auto=update 환경에서는 Hibernate 가 snoozed_until 컬럼은 자동 추가하지만,
--      기존 snooze 컬럼을 자동 삭제하지 않으므로 이 마이그레이션을 한 번 수동 실행해야 한다.
--    - Flyway 도입 전이라 파일명에 V{날짜} 형식으로 두고, 운영자가 직접
--      `mysql ... < this_file.sql` 로 적용한다.
--
--  롤백:
--    - 비파괴적 마이그레이션을 원한다면 5단계 DROP COLUMN 을 주석 처리하고
--      배포 후 안정화되면 별도 마이그레이션으로 떨군다.
-- ============================================================

-- 1. snoozed_until 컬럼 추가 (이미 있으면 무시 — IF NOT EXISTS 는 MySQL 8.0.29+)
ALTER TABLE recommendation_weight
    ADD COLUMN IF NOT EXISTS snoozed_until DATETIME NULL;

-- 2. 기존 snooze=1 → 영구 음소거 sentinel 로 변환
UPDATE recommendation_weight
SET snoozed_until = '9999-12-31 00:00:00'
WHERE snooze = 1
  AND snoozed_until IS NULL;

-- 3. snooze=0 인 행은 별도 처리 불필요 (snoozed_until 은 NULL 그대로)

-- 4. weight_value 캐시 재계산 트리거 — recommendation_dirty 를 켜서
--    다음 /weights/user/{userId}/top3 호출 시 자동 재계산되도록 한다.
UPDATE user_data
SET recommendation_dirty = TRUE
WHERE id IN (
    SELECT DISTINCT ld.user_data_id
    FROM recommendation_weight rw
             JOIN link_data ld ON rw.link_data_id = ld.id
    WHERE rw.snoozed_until IS NOT NULL
);

-- 5. 구 snooze 컬럼 제거.
--    배포 직후 즉시 drop 하면 롤백이 어려우니, 안정화 후 별도 PR 로 적용해도 됨.
ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS snooze;
