-- ============================================================
--  OpenAI/유사도 통합 코드 완전 제거 (2026-05-21)
--
--  배경:
--    문서(REMIND_STRATEGY)는 의미 유사도(cosine)를 점수에 사용하지 않음.
--    호스트/메타데이터 매칭만 명시. 우리가 임의로 도입한 v2 기능을 베타 전 정리.
--
--  변경:
--    1. recommendation_weight — similarity / embedding* 컬럼 제거
--    2. user_data — interest_vector / interest_embedding_model /
--                    interest_vector_updated_at / recommendation_dirty 제거
--
--  실행: Spring 부팅 → Hibernate 가 새 엔티티 정의 적용 → 이 SQL 수동 실행.
--  Hibernate ddl-auto=update 가 자동으로 컬럼을 드롭하지 않으므로 수동 적용 필요.
-- ============================================================

-- ============================================================
-- (1) recommendation_weight — 의미 매칭 의존 컬럼 제거
-- ============================================================
ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS similarity;

ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS embedding_text;

ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS embedding_vector;

ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS embedding_model;

ALTER TABLE recommendation_weight
    DROP COLUMN IF EXISTS embedding_updated_at;

-- ============================================================
-- (2) user_data — 관심사 벡터 / dirty 플래그 제거
-- ============================================================
ALTER TABLE user_data
    DROP COLUMN IF EXISTS interest_vector;

ALTER TABLE user_data
    DROP COLUMN IF EXISTS interest_embedding_model;

ALTER TABLE user_data
    DROP COLUMN IF EXISTS interest_vector_updated_at;

ALTER TABLE user_data
    DROP COLUMN IF EXISTS recommendation_dirty;
