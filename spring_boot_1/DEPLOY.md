# save-it Spring 백엔드 — Fly.io 배포 가이드 (베타)

베타 띄우는 데 필요한 최소 절차만 담음. 안정화 후 멀티 인스턴스/Redis 등은 별도 문서로 분리.

---

## 0. 사전 준비

- Fly CLI 설치: <https://fly.io/docs/hands-on/install-flyctl/>
- `fly auth login` 으로 로그인
- 결제 카드 등록 (무료 한도 안에서 운영 가능하지만 등록은 필수)
- **외부 Postgres 인스턴스** — 베타엔 Supabase Free Tier / Neon / Render Postgres 추천.
  Fly native Postgres 도 가능하지만 외부 호스팅이 백업·콘솔이 더 단순.

---

## 1. 외부 Postgres 준비 (가장 단순한 옵션)

### Supabase (추천)

대시보드에서 `New Project` → 리전(예: `ap-northeast-2 Seoul`) 선택 → 데이터베이스 자동 생성.
Project Settings → Database → Connection string 에서 JDBC URL 정보 확인:

```
jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres
```

> Supabase 의 Auth/Realtime/Storage 는 이번 백엔드에선 사용 안 함. **Postgres 인스턴스만 활용**.

### Neon

[neon.tech](https://neon.tech) → `Create Project` → 리전 선택 → Connection string 복사.

### Fly native Postgres (1대 운영 시 가장 저렴)

```bash
fly postgres create --name save-it-db --region nrt --vm-size shared-cpu-1x
fly postgres attach save-it-db --app save-it-backend
```

attach 가 `DATABASE_URL` 환경변수를 앱에 자동 주입해주지만, Spring 형식이라 별도 변수로 다시 set 해야 함 (§3 참조).

### 로컬 Postgres 로 임시 운영하려면

Fly 머신에서 외부로 연결되어야 하므로 **공인 IP + 방화벽 인바운드 허용**이 필요. 베타엔 비추.

---

## 2. Fly 앱 생성

워킹디렉터리는 `spring_boot_1/` 기준.

```bash
cd /path/to/spring_boot_1
fly launch --no-deploy --name save-it-backend --region nrt --copy-config
```

- `--no-deploy`: 비밀 환경변수부터 세팅한 뒤 배포할 거라 빌드는 보류
- `--name`: 원하는 이름 (전세계 유일)
- `--region nrt`: 도쿄. 한국 사용자 기준 가장 빠름. 미국 위주면 `sjc`.
- `--copy-config`: 우리가 만든 `fly.toml` 그대로 사용

`fly.toml` 의 `app` 필드를 실제 이름과 일치시켜 두기.

---

## 3. Secrets 주입

비밀번호·시크릿은 `fly secrets set` 로만 주입. `fly.toml` 의 `[env]` 에 넣지 말 것.

```bash
# DB 접속 (Supabase 예시)
fly secrets set \
  SPRING_DATASOURCE_URL='jdbc:postgresql://db.xxxxxxxx.supabase.co:5432/postgres' \
  SPRING_DATASOURCE_USERNAME='postgres' \
  SPRING_DATASOURCE_PASSWORD='your-supabase-db-password'

# JWT 서명 비밀키 — 반드시 32바이트(256bit) 이상, 운영은 64바이트+ 권장
fly secrets set APP_JWT_SECRET="$(openssl rand -base64 48)"

# 프론트 도메인이 정해지면 CORS 갱신
fly secrets set APP_CORS_ALLOWED_ORIGINS='https://your-frontend.vercel.app'

# (선택) JWT TTL 조정
fly secrets set APP_JWT_ACCESS_TTL_MINUTES=15
fly secrets set APP_JWT_REFRESH_TTL_DAYS=7

# (선택) 일간 리마인드 cron 시각 변경
fly secrets set APP_REMINDER_DAILY_CRON='0 0 9 * * *'  # KST 09:00 매일
```

`fly secrets list` 로 등록 상태 확인.

> **JWT secret 관리 규칙**: 한 번 발급 후 변경하면 발급된 access 토큰이 전부 무효화돼서 사용자가 일제히 강제 로그아웃됨. 변경은 보안 침해 사고 시에만.

---

## 4. 첫 배포

```bash
fly deploy
```

- 로컬에서 Docker 빌드 → Fly registry 푸시 → 머신 부팅
- 첫 빌드는 Gradle 의존성 다운로드 때문에 5~10분 소요
- 이후 빌드는 캐시 활용해서 2~3분

배포 끝나면:

```bash
fly status            # 머신 상태
fly logs              # 실시간 로그
fly open              # 브라우저로 https://save-it-backend.fly.dev/health 열기
```

`{"status":"ok"}` 가 보이면 부팅 성공.

---

## 5. 마이그레이션 적용

현재는 `ddl-auto=update` 라 엔티티 추가/필드 추가는 자동 반영.
이전 SQL 파일들(`src/main/resources/db/migration/V20260521__*.sql`)은 MySQL 문법으로 작성되어 있으니 Postgres 에선 **수동 변환이 필요한 항목만 골라 실행**.

```bash
# Supabase 콘솔 → SQL Editor 에서 실행하거나 psql 로 직접
psql "$DATABASE_URL"

# 필요한 데이터 변환 SQL (예: snooze→snoozed_until 마이그레이션)
# 새로 가입한 사용자만 있다면 ddl-auto=update 가 다 해주므로 skip 가능
```

운영 직전엔 ddl-auto 를 `validate` 로 바꾸고 Flyway 도입 권장 (§9).

---

## 6. 동작 확인 — JWT 흐름

```bash
# 헬스 체크
curl https://save-it-backend.fly.dev/health
# → {"status":"ok"}

# 회원가입 → access + refresh 토큰 받기
RESP=$(curl -s -X POST https://save-it-backend.fly.dev/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"userName":"alice","password":"strong-pw"}')
TOKEN=$(echo "$RESP" | jq -r '.accessToken')
REFRESH=$(echo "$RESP" | jq -r '.refreshToken')

# 인증 필요 엔드포인트 — Authorization 헤더
curl -H "Authorization: Bearer $TOKEN" \
  https://save-it-backend.fly.dev/link/user/alice

# 토큰 만료(15분) 후 refresh
curl -X POST https://save-it-backend.fly.dev/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}"
# → 새 accessToken / refreshToken 쌍 반환
```

---

## 7. 알아둬야 할 베타 제약

**단일 머신 운영** — `fly.toml` 의 `min_machines_running = 1`, `auto_stop_machines = false`.
RateLimiter / 디지스트 스케줄러가 모두 JVM 메모리라 자동 스케일하면 즉시 깨짐.
베타 안정화 후 Redis + ShedLock 도입하고 스케일 활성화.

> JWT 인증 자체는 stateless 라 스케일 가능. 다만 위 두 컴포넌트가 막음.

**Refresh 토큰 만료** — 사용자가 7일 이상 안 들어오면 refresh 도 만료 → 다시 로그인 필요. 베타 운영엔 무리 없음.

**디지스트 cron 중복 방지 없음** — 멀티 인스턴스 가면 2대가 동시 발화해서 같은 사용자에게 리마인더 2배로 INSERT 될 수 있음. ShedLock 필수.

**Rate limit 카운터도 배포 시 리셋** — JVM 메모리. 베타엔 큰 문제 아님.

**CORS** — 프론트 배포 후 `APP_CORS_ALLOWED_ORIGINS` 갱신 필수. 안 갱신하면 브라우저에서 인증 요청 차단됨.

**JWT secret 강도** — 운영은 반드시 64바이트+ 임의 문자열. `openssl rand -base64 48` 추천.

---

## 8. 운영 명령 치트시트

```bash
fly logs                              # 실시간 로그
fly logs --since 1h                   # 최근 1시간
fly status                            # 머신 상태
fly machines list                     # 머신 목록
fly ssh console                       # 머신에 SSH (디버깅용)

fly scale memory 2048                 # 메모리 증설
fly scale show                        # 현재 사양 확인

fly secrets list                      # 시크릿 목록 (값은 안 보임)
fly secrets unset APP_CORS_ALLOWED_ORIGINS

fly apps destroy save-it-backend      # 전체 삭제 (주의)
```

DB 콘솔 (Supabase 기준):

```bash
psql "$DATABASE_URL" -c "SELECT id, user_name FROM user_data LIMIT 5;"
psql "$DATABASE_URL" -c "SELECT COUNT(*) FROM link_reminders WHERE sent_at > NOW() - INTERVAL '1 day';"
```

---

## 9. 다음 단계 (베타 안정화 이후)

1. **Redis 도입** — RateLimiter 를 Redis 로 옮겨야 멀티 인스턴스 가능. `fly redis create` 로 native Redis 띄우면 단순.
2. **ShedLock** — `@Scheduled` 잡 단일 실행 보장. 멀티 인스턴스 환경에 필수.
3. **Flyway** — 마이그레이션 SQL 자동 적용. `ddl-auto=update` → `validate` 졸업.
4. **Structured logging** — JSON 로그 + correlation ID. `logback-json-encoder` 추가.
5. **OpenAPI 문서** — `springdoc-openapi-starter-webmvc-ui` 한 줄.
6. **API 버전** — `/v1/` prefix.
7. **JWT 알고리즘 강화** — HS384 (현재) → RS256 + 키 회전 (대규모 운영 시).
