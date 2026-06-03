-- ERD §4.3 tags / link_tags (v1) — 사용자 자유 태그.
-- PARA 폴더와 분리된 검색/필터 축 (ERD 설계 노트 #3). 사용자가 직접 부착하는 수동 방식.
--
-- NOTE: 현 베타는 Flyway 미적용(spring.jpa.hibernate.ddl-auto=update)이라
--       런타임 스키마는 JPA 엔티티(Tag / LinkTag)가 생성한다.
--       이 파일은 ERD 정합 + 향후 Flyway 도입 시 사용할 정본 DDL 이며,
--       엔티티가 표현하지 못하는 부분(ON DELETE CASCADE, lower(name) 함수형 unique)을
--       문서로 남긴다.

create table if not exists tags (
    id          serial primary key,
    user_id     integer     not null references user_data(id) on delete cascade,
    name        varchar(50) not null,
    created_at  timestamp   not null default now()
);

-- 사용자별 태그 이름 유일 (대소문자 무시) — ERD unique(user_id, lower(name)).
create unique index if not exists uq_tags_user_name on tags (user_id, lower(name));

create table if not exists link_tags (
    link_id integer not null references link_data(id) on delete cascade,
    tag_id  integer not null references tags(id)      on delete cascade,
    primary key (link_id, tag_id)
);

create index if not exists idx_link_tags_tag on link_tags (tag_id);
