-- ============================================================
--  RecommendationWeight: legacy weight 컬럼 3개 제거
--
--  목적:
--    - weight_value: 캐시 컬럼. /top3 가 scoreRemind 로 일원화되며 폐기.
--    - last_update: setLastUpdate 호출이 코드 어디에도 없어 항상 NULL 이었음.
--    - frequency: 자동 갱신 코드 없어 W_FREQUENCY=0.10 점수 항이 영구 0.
--
--  점수 공식 정리 (2026-05-21):
--    이전: paraMult × (0.25·unread + 0.30·importance + 0.15·similarity
--                    + 0.10·frequency + 0.20·bellRecency) × fatigueFactor
--    이후: paraMult × (0.30·unread + 0.30·importance + 0.15·similarity
--                    + 0.25·bellRecency) × fatigueFactor
--
--  실행 환경: MySQL 8 기준. Hibernate ddl-auto=update 은 컬럼 자동 삭제하지 않으니
--             수동 실행 필요.
--
--  롤백: 비파괴적으로 가려면 DROP COLUMN 절을 주석 처리. 베타 안정화 후 실 적용.
-- ============================================================

ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS weight_value;

ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS last_update;

ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS frequency;
