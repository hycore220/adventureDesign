# Save-it Spring 백엔드 — 프론트 통합 가이드

> 이 문서는 [als8921/save-it](https://github.com/als8921/save-it) 의 Next.js + WXT 프론트가
> 이 Spring 백엔드와 통합할 때 알아야 할 차이점·계약·코드 예시를 모은다.
> save-it 문서(PRD/ERD/ARCHITECTURE/REMIND_STRATEGY)와 1:1로 대응되는 항목은 정합성을 명시,
> 다른 항목은 어떻게 다른지·왜 다른지·어떻게 호출하면 되는지를 설명한다.

---

## TL;DR — 인수 첫날 봐야 할 것

| 항목 | save-it 문서 가정 | Spring 백엔드 실제 | 프론트 영향 |
|---|---|---|---|
| **인증** | Supabase Auth (JWT) | **자체 JWT (access 15분 + refresh 7일)** | `Authorization: Bearer` 헤더 + refresh 회전 |
| **DB 접근** | RSC에서 Supabase 직접 호출 | Spring REST API 경유 필수 | `lib/supabase` → `lib/api` 로 교체 |
| **리마인드 생성** | Vercel Cron → API Route | Spring `@Scheduled` 잡 | 프론트 변경 없음 (자동 동작) |
| **DB** | Postgres (Supabase) | Postgres 16 | 동일 — ERD 그대로 사용 가능 |
| **PARA 분류** | folder.para_category | folder.paraCategory | 동일 (Java naming) |
| **태그** | v1-a (`tags`, `link_tags`) | **미구현** | v1 단계까지 미사용 |
| **익스텐션 동기화** | Supabase 직접 | JWT 또는 영구 ApiToken 선택 가능 | Bearer 헤더 동일 |
| **영구 토큰 발급** | Supabase service_role 키 | `POST /auth/api-tokens` (사용자 단위, 폐기 가능) | 익스텐션·CLI 자동화에 사용 |

→ 큰 그림: **데이터 모델은 그대로, 인증 모델도 Supabase 형식과 정합. 클라이언트가 부르는 자리만 도메인 교체.**

---

## 1. 인증 흐름 (JWT, Supabase Auth 정합)

### 1.1 토큰 정책

| 토큰 | 만료 | 저장 위치 | 용도 |
|---|---|---|---|
| **access** | 15분 (env로 조정) | 클라이언트 메모리 권장 | 모든 API 호출의 `Authorization: Bearer` |
| **refresh** | 7일 (env로 조정) | secure cookie 또는 secure storage | access 만료 시 회전 |

- access 는 HS384 서명 JWT, claims = `{ sub: userId, userName, iat, exp, iss: "saveit" }`
- refresh 는 32바이트 랜덤 토큰, **DB엔 SHA-256 해시만 저장** (평문은 발급 시 1회 노출)
- refresh 사용 시 **rotate** — 기존 토큰 즉시 폐기 + 새 쌍 발급 (theft mitigation)
- 로그아웃 = refresh 폐기. access 는 짧은 만료에 의존

### 1.2 가입 / 로그인

```ts
// 응답: { id, userName, accessToken, refreshToken }
const res = await fetch(`${API}/auth/signup`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ userName, password }),
});
const { accessToken, refreshToken } = await res.json();
```

### 1.3 보호된 API 호출

```ts
// Next.js Server Component
export async function getCurrentUser(accessToken: string) {
  const res = await fetch(`${API}/auth/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: 'no-store',
  });
  if (!res.ok) return null;
  return res.json();
}

// Client Component / Server Action
await fetch(`${API}/link/create`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`,
  },
  body: JSON.stringify({ link, title, folderId }),
});
```

> `credentials: 'include'` 불필요. CORS도 도메인 허용만 하면 됨.

### 1.4 토큰 회전 (refresh)

access가 만료(401)되면 refresh로 새 쌍 발급:

```ts
async function refreshTokens(refresh: string) {
  const res = await fetch(`${API}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken: refresh }),
  });
  if (!res.ok) {
    // refresh 도 만료/폐기 → 다시 로그인 화면으로
    throw new Error('session expired');
  }
  return res.json(); // { accessToken, refreshToken } — refresh 도 새로 발급됨
}
```

**자동 회전 fetch 래퍼 예시**:
```ts
// lib/api.ts
let access: string | null = null;
let refresh: string | null = null;

export async function api(path: string, init?: RequestInit) {
  const call = () => fetch(`${API}${path}`, {
    ...init,
    headers: {
      ...(init?.headers || {}),
      ...(access ? { Authorization: `Bearer ${access}` } : {}),
    },
  });
  let res = await call();
  if (res.status === 401 && refresh) {
    try {
      const pair = await refreshTokens(refresh);
      access = pair.accessToken; refresh = pair.refreshToken;
      res = await call();
    } catch {
      // 세션 만료 — 라우터 push '/login'
    }
  }
  return res;
}
```

### 1.5 로그아웃

```ts
await fetch(`${API}/auth/logout`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ refreshToken: refresh }),
});
// 로컬 access/refresh 변수도 클리어
access = null; refresh = null;
```

### 1.6 익스텐션 / 외부 클라이언트

세션 만료가 빈번하지 않은 익스텐션은 **영구 ApiToken** 옵션:

```bash
# 사용자가 로그인한 상태에서
curl -X POST $API/auth/api-tokens \
  -H "Authorization: Bearer $access" \
  -H 'Content-Type: application/json' \
  -d '{"name":"my-extension"}'
# → { "id": 1, "token": "<64글자>", "name": "my-extension" }
```

토큰은 발급 시 1회 평문 노출. 익스텐션이 이걸 저장하고 매 호출에 `Authorization: Bearer <token>` 첨부. 폐기는 `/auth/api-tokens/{id}` DELETE.

JWT (점 2개 패턴)와 ApiToken (점 없음)을 백엔드가 자동 구분해서 적절한 필터로 라우팅.

### 1.7 CORS

```bash
APP_CORS_ALLOWED_ORIGINS=https://app.example.com,https://your-vercel.app
```

JWT는 헤더 기반이라 `SameSite` / `Secure` 쿠키 설정 신경 쓸 필요 없음.

---

## 2. API 카탈로그

전체 베이스: `${API_BASE}` (예: `http://localhost:8080`)
모든 응답은 JSON. 한글 메시지가 본문에 있을 수 있다.

### 2.1 Auth

| Method | Path | 요약 |
|---|---|---|
| POST | `/auth/signup` | 가입 + PARA 4폴더 자동 생성 + access/refresh 발급 |
| POST | `/auth/login` | 로그인 → access/refresh 발급 |
| POST | `/auth/refresh` | refresh 사용 → 새 쌍 발급 (rotate) |
| POST | `/auth/logout` | refresh 폐기 |
| GET | `/auth/me` | 현재 사용자 정보 (Bearer access 필요) |
| POST | `/auth/api-tokens` | 영구 ApiToken 발급 (익스텐션용) |

```ts
// signup / login 요청
{ "userName": "alice", "password": "..." }
// 응답
{
  "id": 1,
  "userName": "alice",
  "accessToken": "eyJhbGciOiJIUzM4NCJ9...",
  "refreshToken": "_in14AzhxFt..."
}

// /auth/me 응답 (토큰 필드는 null)
{ "id": 1, "userName": "alice", "accessToken": null, "refreshToken": null }
```

### 2.2 Folder (PARA 카드)

| Method | Path | 요약 |
|---|---|---|
| GET | `/folder/user/{userName}` | 사용자의 최상위 4카드 |
| GET | `/folder/{id}/contents` | 폴더 내부 (하위 폴더 + 링크) |
| POST | `/folder/create` | 하위 폴더 생성 (`parentId` 필수) |
| PUT | `/folder/{id}` | 이름/`paraCategory` 변경 |
| DELETE | `/folder/{id}` | 폴더 삭제 (최상위 PARA 폴더는 거부) |

**라이브러리 홈 응답 예시**:
```json
[
  {"id": 1, "name": "Project",   "parentId": null, "paraCategory": "PROJECT"},
  {"id": 2, "name": "Area",      "parentId": null, "paraCategory": "AREA"},
  {"id": 3, "name": "Resources", "parentId": null, "paraCategory": "RESOURCE"},
  {"id": 4, "name": "Archive",   "parentId": null, "paraCategory": "ARCHIVE"}
]
```

> `paraCategory` 가 PARA 4카드 색·라벨 결정의 source of truth. 이름(`name`)은 사용자 편집 가능.

**폴더 contents 응답**:
```json
{
  "subFolders": [{ "id": 5, "name": "이번 주", "parentId": 1, "paraCategory": "PROJECT" }],
  "links":     [{ "id": 10, "title": "...", "paraStatus": "PROJECT", "host": "...", ... }]
}
```

### 2.3 Link

| Method | Path | 요약 |
|---|---|---|
| POST | `/link/create` | 링크 저장 → **LinkResponse JSON** 반환 |
| GET | `/link/user/{userName}` | 사용자의 모든 링크 |
| GET | `/link/folder/{folderId}` | 특정 폴더의 링크들 |
| PUT | `/link/{id}` | 링크 수정 (제목/URL/PARA/폴더 이동) |
| DELETE | `/link/{id}` | 삭제 (가중치 cascade) |
| POST | `/link/{id}/read` | 읽음 처리 (멱등) |
| GET | `/link/search?q=keyword` | title/URL 부분일치, 대소문자 무시, 최대 50개 |

**링크 저장 요청**:
```ts
{ link: "https://...", title: "제목", folderId: 1, paraStatus?: "Project" }
```
- `folderId`가 있으면 해당 폴더의 PARA가 자동 적용 (ERD §1.1 source of truth)
- `paraStatus`는 폴더가 없을 때만 사용. `"Project" | "area" | "r" | "archive"` 같은 자유 입력 가능 (정규화됨)
- 저장과 동시에 추천 가중치(`importance=0.5`) 자동 생성 → `/today` 즉시 후보

**응답 (LinkResponse)**:
```json
{
  "id": 10,
  "link": "https://...",
  "title": "...",
  "paraStatus": "PROJECT",
  "lastUpdate": "2026-05-21T...",
  "createdAt": "2026-05-21T...",
  "readAt": null,
  "host": "github.com",       // 자동 추출
  "contentType": "GITHUB",    // 자동 추론 (YOUTUBE/ARTICLE/GITHUB/OTHER)
  "thumbnailUrl": null,
  "read": false
}
```

**중복 URL 처리**: 백엔드는 동일 URL 중복 저장을 막지 않는다.
프론트가 FAB 모달에서 저장 전에 `GET /link/search?q=<url>` 로 미리 체크하고 안내하는 게 자연스럽다 (save-it 모바일 셸 설계 Task 16).

### 2.4 추천 / 리마인드 후보 (`/today`)

| Method | Path | 요약 |
|---|---|---|
| GET | `/recommendation-weights/users/{userName}/today` | 오늘 다시 볼 후보 |
| GET | `/recommendation-weights/users/{userName}/today?mode=resurface` | 30일+ 묵힌 자료 발굴 |
| GET | `/recommendation-weights/users/{userName}/today?mode=unread` | 미열람 우선 |
| GET | `/recommendation-weights/users/{userName}/today?mode=priority` | importance 가중 |
| GET | `/recommendation-weights/users/{userName}/today?mode=youtube_ctx&host=youtube.com` | 컨텍스트 매칭 |

**응답 예시**:
```json
[
  {
    "linkId": 12, "link": "https://...", "title": "...",
    "paraStatus": "PROJECT", "isRead": false,
    "createdAt": "...", "lastUpdate": "...",
    "mode": "today",
    "reason": "Projects · 미열람 · 2일 전 저장",
    "remindScore": 0.85,
    "breakdown": {
      "paraMultiplier": 1.0,
      "unreadFactor": 1.0,
      "importance": 0.5,
      "bellRecency": 1.0,
      "fatigueFactor": 1.0,
      "rawScore": 1.0,
      "ageDays": 2,
      "recentRemindCount": 0
    }
  }
]
```

| `breakdown` 필드 | 의미 |
|---|---|
| `paraMultiplier` | PARA별 가중치 — PROJECT=1.0, AREA=0.85, RESOURCE=0.65, ARCHIVE=0 |
| `unreadFactor` | 미열람=1.0, 읽음=0.0 |
| `importance` | 사용자가 설정한 별표급 가중치 (0~1) |
| `bellRecency` | peak에서 1.0, 멀어질수록 가우스 감쇠. Project peak=2일, Area=7일, Resource=21일 |
| `fatigueFactor` | 최근 7일 리마인드 회수마다 0.25씩 감쇠 |
| `recentRemindCount` | 최근 7일 리마인드 횟수 |

### 2.5 가중치 / 스누즈

| Method | Path | 요약 |
|---|---|---|
| POST | `/recommendation-weights` | 가중치 생성/upsert (`bookmarkId` 필수) |
| GET | `/recommendation-weights` | 사용자의 모든 가중치 |
| PUT | `/recommendation-weights/{id}` | `name` / `importance` / `snoozedUntil` 변경 |

**스누즈 동작**:
- `snoozedUntil = null` → 정상 후보
- `snoozedUntil = 미래 시각` → 후보 제외
- `snoozedUntil = 과거 시각` → **자동 만료 → 다시 후보** (별도 PUT 불필요)
- `snoozedUntil = 9999-12-31` → 영구 음소거 sentinel

**사용 예 — 3일간 침묵**:
```ts
const futureIso = new Date(Date.now() + 3 * 86400_000).toISOString().slice(0, 19);
await fetch(`${API}/recommendation-weights/${weightId}`, {
  method: 'PUT',
  headers: {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${accessToken}`,
  },
  body: JSON.stringify({ snoozedUntil: futureIso }),
});
```

### 2.6 리마인드 이력

| Method | Path | 요약 |
|---|---|---|
| POST | `/link-reminders` | 리마인드 발송 기록 (수동) |
| GET | `/link-reminders/user/{userName}` | 사용자 이력 |
| POST | `/link-reminders/{id}/open` | "열람" 표시 (CTR 측정용) |
| POST | `/link-reminders/{id}/snooze` | 개별 리마인드 스누즈 |
| DELETE | `/link-reminders/{id}` | 삭제 |

> 일간 디지스트는 백엔드 `@Scheduled` 잡이 매일 09:00 KST에 자동 INSERT한다. 프론트는 GET만 하면 됨.

### 2.7 KPI (PRD §성공 지표)

| Method | Path | 응답 |
|---|---|---|
| GET | `/metrics/me/reminders` | `{totalSent, totalOpened, overallCtr, byChannelMode}` |
| GET | `/metrics/me/seven-day-click` | `{totalLinks, clickedWithin7Days, rate}` |
| GET | `/metrics/me/completion` | `{totalRemindedLinks, completedLinks, rate}` |

### 2.8 사용자 환경설정

| Method | Path | 요약 |
|---|---|---|
| GET | `/me/reminder-prefs` | 리마인드 채널/시간/타임존 |
| PUT | `/me/reminder-prefs` | 환경설정 변경 |

---

## 3. 핵심 화면 구현 — 매핑

### 3.1 라이브러리 홈 (PARA 4카드 그리드)

save-it `superpowers/specs` Task 11 의 4카드 + 미지정 와이드 카드 구현 시:

```ts
// 1. 폴더 4개 + 미지정 링크 카운트
const folders = await api.get(`/folder/user/${userName}`); // 4개
const unfiledLinks = await api.get(`/link/user/${userName}`)
  .then(links => links.filter(l => !l.folderId)); // 미지정 카운트

// 2. 각 카드 카운트
const counts = await Promise.all(
  folders.map(f => api.get(`/folder/${f.id}/contents`).then(c => c.links.length))
);
```

**색·라벨**은 `paraCategory` (PROJECT/AREA/RESOURCE/ARCHIVE) 로 `lib/para.ts` 토큰 매핑.

### 3.2 카테고리 페이지 `/category/[para]`

```ts
const folders = await api.get(`/folder/user/${userName}`);
const target = folders.find(f => f.paraCategory === paraUpper);
const contents = await api.get(`/folder/${target.id}/contents`);
// contents.subFolders + contents.links 렌더
```

### 3.3 폴더 페이지 `/folder/[id]`

```ts
const { subFolders, links } = await api.get(`/folder/${id}/contents`);
```

### 3.4 링크 추가 FAB (save-it Task 16)

```ts
// 1. 중복 URL 사전 체크
const dupes = await api.get(`/link/search?q=${encodeURIComponent(url)}`);
const exact = dupes.find(l => l.link === url);
if (exact) {
  // "이미 [폴더명]에 저장됨" UI
  return { existing: exact };
}

// 2. 저장
const created = await api.post('/link/create', {
  link: url, title, folderId,
});
router.refresh(); // RSC 데이터 갱신
```

### 3.5 검색 페이지

```ts
const results = await api.get(`/link/search?q=${encodeURIComponent(q)}`);
// 한글/영문 부분일치, 대소문자 무시. 최대 50개.
```

### 3.6 "오늘 다시 볼 링크" 위젯

```ts
const today = await api.get(`/recommendation-weights/users/${userName}/today?limit=5`);
// 각 항목 클릭 시:
//   1. window.open(link.link, '_blank')
//   2. await api.post(`/link/${linkId}/read`)
//   3. router.refresh()
```

---

## 4. 데이터 모델 — ERD vs 실제 테이블

ERD.md 와의 매핑:

| ERD 엔티티 | Spring 테이블 | 비고 |
|---|---|---|
| `folders` | `folder` | `para_category` ✓ |
| `links` | `link_data` | `host`/`content_type`/`thumbnail_url` ✓ |
| `tags` / `link_tags` | **미구현** | v1-a 단계 |
| `link_reminders` | `link_reminders` | `channel`/`mode`/`opened_at`/`snoozed_until` ✓ |
| `user_reminder_prefs` | `user_reminder_prefs` | 가입 시 default row 자동 생성 |
| (없음) | `recommendation_weight` | save-it ERD에 없음 — 가중치/스누즈/importance 통합 |
| (없음) | `api_tokens` | Bearer 토큰 인증 |

### 4.1 ENUM 처리

JPA가 enum을 VARCHAR(255)로 직렬화. 응답에서 항상 **대문자 식별자**가 온다:
- `paraStatus`: `"PROJECT" | "AREA" | "RESOURCE" | "ARCHIVE" | "UNSPECIFIED"`
- `contentType`: `"YOUTUBE" | "ARTICLE" | "GITHUB" | "OTHER"`

요청에는 자유 입력 허용 (`"project"`, `"r"` 모두 정규화됨).

### 4.2 host 추출

ERD §1.2 의 Generated column `host` 는 Spring에선 `LinkData.@PrePersist` 에서 URL 파싱으로 채운다. 결과 동일 — 프론트는 `link.host` 그대로 사용.

---

## 5. 에러 응답 규약

모든 4xx/5xx 는 다음 형식:

```json
{ "error": "<short_code>", "message": "<사용자 친화 메시지>" }
```

| HTTP | error 코드 | 발생 시점 |
|---|---|---|
| 400 | `bad_request` | 잘못된 입력, 존재하지 않는 ID (404 대신) |
| 401 | `unauthorized` | 미인증 / 세션 만료 / 토큰 폐기 |
| 403 | `forbidden` | 타인 데이터 접근 |
| 409 | `conflict` | 중복 가입 등 |
| 429 | `rate_limit_exceeded` | 링크 생성 200건/일 초과 |
| 500 | `internal_error` | 처리 중 오류 (스택트레이스 노출 안 됨) |

> **404 대신 400**: 베타 단계에선 존재하지 않는 ID 도 400으로 매핑. 운영 시 404 분리 검토 예정.

---

## 6. 베타 한계 — 운영 전 처리 예정

프론트 작업엔 영향 없으나 인지하고 있어야 할 사항. **출처별로 분리**:

### Spring 백엔드 자체 한계 (코드 주석 명시)

| 항목 | 현재 상태 | 운영 전 |
|---|---|---|
| Rate Limiter | 인메모리 (단일 인스턴스 전제) | Redis 또는 DB 기반 |
| DB 마이그레이션 | `ddl-auto=update` | `validate` + Flyway |
| 자동 테스트 | verify.sh 통합 검증만 | ScoringEngine JUnit 추가 |
| Scheduled 잡 중복 방지 | 없음 (단일 인스턴스 전제) | ShedLock |
| 404 vs 400 | 존재하지 않는 ID는 일괄 400 | 운영 시 404 분리 검토 |
| JWT secret 강도 | dev 디폴트 32바이트 | 운영 `APP_JWT_SECRET` 환경변수 64바이트+ |

### save-it 문서가 명시한 v1 제외 항목 (프론트 측 한계)

| 항목 | 출처 | v1 처리 |
|---|---|---|
| 태그 / link_tags | ERD v1-a Phase | v1-a 진입 시 추가 |
| Realtime 동기화 | 모바일 셸 specs "미포함 사항" | mutation 후 `router.refresh()` 또는 v2 푸시 |
| 다크 모드, 링크 편집 UI | 모바일 셸 specs | 별도 큐 |

> CSRF는 stateless JWT 전환으로 자체 무관해짐 — 더 이상 운영 전 처리 항목 아님.

---

## 7. 로컬 셋업 — 인수받은 첫날

```bash
# 1. Postgres 16 (Mac brew)
brew install postgresql@16 && brew services start postgresql@16
createdb saveit_dev

# 2. Spring 띄우기 (JWT secret 은 32바이트+ 임의 문자열)
cd spring_boot_1
SPRING_DATASOURCE_URL='jdbc:postgresql://localhost:5432/saveit_dev' \
SPRING_DATASOURCE_USERNAME=$(whoami) \
SPRING_DATASOURCE_PASSWORD='' \
SPRING_JPA_DDL_AUTO=create-drop \
APP_CORS_ALLOWED_ORIGINS='http://localhost:3000' \
APP_JWT_SECRET='change-me-in-prod-must-be-at-least-32-bytes-long-XXXXXX' \
./gradlew bootRun

# 3. 핸드셰이크
curl http://localhost:8080/health
# → {"status":"ok"}

# 4. 회귀 검증 (선택)
bash verify.sh   # 31/37 통과 — 남은 6개는 검증 스크립트 자체 이슈
```

**디지스트 cron 빠르게 확인** (개발용):
```bash
APP_REMINDER_DAILY_CRON='*/15 * * * * *' ./gradlew bootRun
# 15초마다 link_reminders INSERT — 로그 "리마인드 일간 디지스트" 확인
```

운영 default: `0 0 9 * * *` KST (매일 09:00 한 번).

---

## 8. 자주 묻는 질문 (예상)

**Q. RSC에서 Supabase 직접 호출 패턴은 못 쓰나?**
A. 못 씀. `lib/supabase` 대신 `lib/api.ts` 라는 fetch 래퍼를 만들고, 그 안에서 `cookies()` 를 헤더로 실어 보내는 패턴을 쓰면 똑같이 동작함. mutation 후 `router.refresh()` 로 재조회하는 흐름도 그대로.

**Q. Supabase Realtime 같은 실시간 동기화는?**
A. v1 범위 밖. 클라이언트 mutation → `router.refresh()` 패턴으로 충분. SSE/WebSocket이 필요해지면 별도 검토.

**Q. 익스텐션에서 인증은?**
A. 두 선택지:
  - **JWT (권장)**: 웹과 동일한 access + refresh 흐름. 다만 access 15분 만료라 백그라운드 워커가 자주 refresh 해야 함.
  - **영구 ApiToken**: 로그인한 사용자가 `POST /auth/api-tokens` 로 토큰 발급 → 익스텐션이 그 토큰을 저장하고 `Authorization: Bearer <token>` 로 호출. 만료 없음, 사용자가 명시적으로 폐기 가능. 백엔드는 JWT 패턴(`.` 2개)인지 보고 자동으로 적절한 필터로 라우팅.

**Q. 가입하자마자 빈 화면이 뜨면?**
A. 안 뜸. 가입 시 PARA 4폴더 + user_reminder_prefs default row가 자동 생성. 빈 카드 4개로 시작하고 사용자가 링크 추가하면 카운트 채워짐.

**Q. /today가 비어있다?**
A. 가능한 원인 2가지:
  1. 모든 링크가 read 상태 또는 Archive 폴더 → 자격 미충족
  2. peak 시점이 안 와서 점수가 cutoff 미달 → 시간 흐름 시뮬레이션 필요 (이는 정상 동작, 사용자에겐 "오늘은 다시 볼 게 없다" 메시지로 표현 권장)

**Q. 폴더 PARA를 바꾸면 소속 링크는?**
A. 자동 cascade. ERD §1.1 source of truth 정책. 별도 호출 불필요.

---

## 9. 참고 자료

- 회귀 검증 시나리오: [TEST_SCENARIOS.md](../TEST_SCENARIOS.md)
- 배포 가이드: [DEPLOY.md](./DEPLOY.md)
- save-it 원본 문서:
  - [PRD](https://github.com/als8921/save-it/blob/main/docs/PRD.md)
  - [ERD](https://github.com/als8921/save-it/blob/main/docs/ERD.md)
  - [REMIND_STRATEGY](https://github.com/als8921/save-it/blob/main/docs/REMIND_STRATEGY.md)
  - [ARCHITECTURE](https://github.com/als8921/save-it/blob/main/docs/ARCHITECTURE.md)
  - [모바일 셸 specs](https://github.com/als8921/save-it/blob/main/docs/superpowers/specs/2026-05-20-web-pwa-mobile-shell-design.md)

질문/이슈는 PR 코멘트나 Slack 으로.
