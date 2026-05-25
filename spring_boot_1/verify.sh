#!/usr/bin/env bash
# ============================================================================
#  save-it Spring 백엔드 — 문서 기반 시나리오 자동 검증 스크립트
#
#  사용 방법:
#    1. 로컬에서 Spring 서버 띄움:
#         ./gradlew bootRun
#       (MySQL 없으면 H2 인메모리로:
#         SPRING_DATASOURCE_URL='jdbc:h2:mem:testdb;MODE=MYSQL' \
#         SPRING_DATASOURCE_USERNAME=sa \
#         SPRING_DATASOURCE_PASSWORD= \
#         ./gradlew bootRun )
#    2. 별도 터미널에서 이 스크립트 실행:
#         bash verify.sh
#
#  검증 범위:
#    - PRD: PARA 4분류, 링크 저장/수정/삭제, 재노출 알고리즘
#    - REMIND_STRATEGY: 자격(§2.1), 점수(§2.2), 모드(§2.3), fatigue(§5.5), 스누즈(§5.3)
#    - ERD: folder.para_category cascade, link.host/contentType, user_reminder_prefs
#    - 컨텍스트 매칭: mode=youtube_ctx / domain_ctx
#    - 보안: 소유권 차단, Bearer 토큰
#    - KPI: 빈 데이터 응답, CTR 계산
#
#  의존:
#    - curl, jq (둘 다 brew install jq 면 끝)
#
#  시간 의존 시나리오(저장 후 14일/30일 등)는 manual 안내만 출력.
# ============================================================================

set -u

BASE="${SAVE_IT_BASE_URL:-http://localhost:8080}"
# JWT 마이그레이션 후: 변수에 access 토큰 저장 (변수명은 호환 위해 유지).
COOKIE_A=""
COOKIE_B=""

PASS=0
FAIL=0
SKIP=0
NOTES=()

# ---------- 출력 유틸 ----------
c_reset=$'\033[0m'
c_green=$'\033[1;32m'
c_red=$'\033[1;31m'
c_yellow=$'\033[1;33m'
c_blue=$'\033[1;34m'
c_dim=$'\033[2m'

pass() { PASS=$((PASS+1)); echo "  ${c_green}✓${c_reset} $1"; }
fail() { FAIL=$((FAIL+1)); echo "  ${c_red}✗${c_reset} $1"; echo "    ${c_dim}$2${c_reset}"; }
skip() { SKIP=$((SKIP+1)); echo "  ${c_yellow}⊘${c_reset} $1 ${c_dim}(skipped: $2)${c_reset}"; }
section() { echo; echo "${c_blue}━━ $1 ━━${c_reset}"; }
note() { NOTES+=("$1"); }

# 의존성 확인
for cmd in curl jq; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "${c_red}필수 명령어 $cmd 가 설치되지 않았습니다.${c_reset}"
    echo "Mac: brew install $cmd"
    exit 1
  fi
done

# ---------- HTTP 호출 헬퍼 ----------
# 사용: req METHOD PATH [DATA] [ACCESS_TOKEN]
# 응답: STATUS_CODE\nBODY
# (이전 시그니처: cookie 파일 경로 → 현재: JWT access 토큰. 변수명만 호환 유지)
req() {
  local method="$1" path="$2" data="${3:-}" token="${4:-}"
  local args=(-s -o /tmp/verify_body -w "%{http_code}" -X "$method" "$BASE$path")
  if [[ -n "$token" ]]; then args+=(-H "Authorization: Bearer $token"); fi
  if [[ -n "$data" ]]; then args+=(-H "Content-Type: application/json" -d "$data"); fi
  local status
  status="$(curl "${args[@]}")"
  echo "$status"
  cat /tmp/verify_body
}

# 사용: req_bearer METHOD PATH TOKEN [DATA]
req_bearer() {
  local method="$1" path="$2" token="$3" data="${4:-}"
  local args=(-s -o /tmp/verify_body -w "%{http_code}" -X "$method" "$BASE$path"
              -H "Authorization: Bearer $token")
  if [[ -n "$data" ]]; then args+=(-H "Content-Type: application/json" -d "$data"); fi
  local status
  status="$(curl "${args[@]}")"
  echo "$status"
  cat /tmp/verify_body
}

# Status 와 body 분리
extract_status() { head -n 1 <<< "$1"; }
extract_body()   { tail -n +2 <<< "$1"; }

# ============================================================================
#  사전 점검
# ============================================================================
section "사전 점검"

result="$(req GET /health)"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
if [[ "$status" == "200" ]] && echo "$body" | jq -e '.status == "ok"' >/dev/null 2>&1; then
  pass "/health 응답 정상"
else
  fail "/health 실패" "status=$status body=$body"
  echo "${c_red}서버가 안 떠있는 것 같아요. ./gradlew bootRun 으로 띄우신 뒤 다시 실행하세요.${c_reset}"
  exit 1
fi

# 테스트 사용자 식별자 — 같은 이름 충돌 피하려고 timestamp suffix
RUN_ID="$(date +%s)"
ALICE="alice_$RUN_ID"
BOB="bob_$RUN_ID"
PWD_STR="testpwd1234!"

# ============================================================================
#  A. 신규 가입 + 기본 폴더 자동 생성
# ============================================================================
section "A. 신규 가입 + 기본 폴더"

result="$(req POST /auth/signup "{\"userName\":\"$ALICE\",\"password\":\"$PWD_STR\"}")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
if [[ "$status" == "200" ]]; then
  COOKIE_A="$(echo "$body" | jq -r '.accessToken')"
  pass "A1. 신규 가입 (alice)"
else
  fail "A1. 신규 가입 실패" "status=$status body=$body"
fi

result="$(req POST /auth/signup "{\"userName\":\"$BOB\",\"password\":\"$PWD_STR\"}")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
if [[ "$status" == "200" ]]; then
  COOKIE_B="$(echo "$body" | jq -r '.accessToken')"
  pass "A2. 신규 가입 (bob)"
else
  fail "A2. 신규 가입 실패" "status=$status body=$body"
fi

# 가입 시 PARA 폴더 4종 자동 생성 확인
result="$(req GET "/folder/user/$ALICE" "" "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
count="$(echo "$body" | jq 'length' 2>/dev/null || echo 0)"
if [[ "$status" == "200" ]] && [[ "$count" -ge 4 ]]; then
  pass "A3. PARA 기본 폴더 자동 생성 (4종)"
else
  fail "A3. PARA 기본 폴더 부족" "count=$count body=$body"
fi

# 기본 reminder_prefs 자동 생성 확인
result="$(req GET /me/reminder-prefs "" "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
if [[ "$status" == "200" ]] && echo "$body" | jq -e '.dailyEnabled == true and .timezone == "Asia/Seoul"' >/dev/null; then
  pass "A4. reminder_prefs 기본값 row 자동 생성"
else
  fail "A4. reminder_prefs 자동 생성 실패" "status=$status body=$body"
fi

# 폴더 id 들 추출
PROJECT_ID="$(echo "$body" | jq -r '.' >/dev/null; req GET "/folder/user/$ALICE" "" "$COOKIE_A" | tail -n +2 | jq '.[] | select(.name=="Project") | .id')"
AREA_ID="$(req GET "/folder/user/$ALICE" "" "$COOKIE_A" | tail -n +2 | jq '.[] | select(.name=="Area") | .id')"
RESOURCE_ID="$(req GET "/folder/user/$ALICE" "" "$COOKIE_A" | tail -n +2 | jq '.[] | select(.name=="Resources") | .id')"
ARCHIVE_ID="$(req GET "/folder/user/$ALICE" "" "$COOKIE_A" | tail -n +2 | jq '.[] | select(.name=="Archive") | .id')"

if [[ -n "$PROJECT_ID" && -n "$AREA_ID" && -n "$RESOURCE_ID" && -n "$ARCHIVE_ID" ]]; then
  pass "A5. PARA 폴더 ID 추출 (Project=$PROJECT_ID Area=$AREA_ID Resource=$RESOURCE_ID Archive=$ARCHIVE_ID)"
else
  fail "A5. 폴더 ID 추출 실패" "P=$PROJECT_ID A=$AREA_ID R=$RESOURCE_ID Ar=$ARCHIVE_ID"
fi

# ============================================================================
#  B. 기본 추천 흐름
# ============================================================================
section "B. 링크 저장 → /today 즉시 노출"

# Project 폴더에 링크 저장
result="$(req POST /link/create \
  "{\"link\":\"https://example.com/project1\",\"title\":\"프로젝트 문서\",\"folderId\":$PROJECT_ID}" \
  "$COOKIE_A")"
status="$(extract_status "$result")"
if [[ "$status" == "200" ]]; then
  pass "B1. Project 폴더에 링크 저장"
else
  fail "B1. 링크 저장 실패" "status=$status"
fi

# /today 호출 — 방금 저장한 Project 링크가 등장해야 함
result="$(req GET "/recommendation-weights/users/$ALICE/today?limit=10" "" "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
count="$(echo "$body" | jq 'length')"
if [[ "$status" == "200" ]] && [[ "$count" -ge 1 ]]; then
  pass "B2. /today 에 즉시 등장 (count=$count)"
else
  fail "B2. /today 등장 안 함" "count=$count body=$body"
fi

# 첫 후보의 reason / paraStatus 확인
first_para="$(echo "$body" | jq -r '.[0].paraStatus // empty')"
first_reason="$(echo "$body" | jq -r '.[0].reason // empty')"
if [[ "$first_para" == "PROJECT" ]]; then
  pass "B3. paraStatus = PROJECT 확인"
else
  fail "B3. paraStatus 불일치" "got=$first_para"
fi

if [[ "$first_reason" == *"Projects"* ]] && [[ "$first_reason" == *"미열람"* ]]; then
  pass "B4. reason 라벨 형식 OK ($first_reason)"
else
  fail "B4. reason 라벨 형식 이상" "got='$first_reason'"
fi

# breakdown 안에 unreadFactor / paraMultiplier 가 있는지 (similarity 는 없어야 함)
first_breakdown="$(echo "$body" | jq '.[0].breakdown')"
if echo "$first_breakdown" | jq -e '.unreadFactor == 1.0 and .paraMultiplier == 1.0 and (has("similarity") | not)' >/dev/null; then
  pass "B5. ScoreBreakdown — unread=1, paraMult=1, similarity 필드 없음"
else
  fail "B5. ScoreBreakdown 형식 이상" "$first_breakdown"
fi

# 점수 합리적 범위
first_score="$(echo "$body" | jq -r '.[0].remindScore')"
if (( $(echo "$first_score > 0.05" | bc -l) )); then
  pass "B6. 점수 cutoff 통과 (score=$first_score)"
else
  fail "B6. 점수가 너무 낮음" "score=$first_score"
fi

# ============================================================================
#  C. PARA 정책 자격 검증
# ============================================================================
section "C. PARA 정책 자격 (REMIND §2.1)"

# Area 폴더 링크 — 미열람이라 즉시 자격
req POST /link/create \
  "{\"link\":\"https://example.com/area1\",\"title\":\"Area 글\",\"folderId\":$AREA_ID}" \
  "" "$COOKIE_A" >/dev/null

# Resource 폴더 링크 — 14일 대기 룰이라 미열람 + 14일+ 충족해야 함
req POST /link/create \
  "{\"link\":\"https://example.com/resource1\",\"title\":\"Resource 글\",\"folderId\":$RESOURCE_ID}" \
  "" "$COOKIE_A" >/dev/null

# Archive 폴더 링크 — 어떤 경우에도 등장 X
req POST /link/create \
  "{\"link\":\"https://example.com/archive1\",\"title\":\"Archive 글\",\"folderId\":$ARCHIVE_ID}" \
  "" "$COOKIE_A" >/dev/null

result="$(req GET "/recommendation-weights/users/$ALICE/today?limit=20" "" "$COOKIE_A")"
body="$(extract_body "$result")"

# Area 는 있어야 함
has_area="$(echo "$body" | jq '[.[] | select(.paraStatus == "AREA")] | length')"
if [[ "$has_area" -ge 1 ]]; then
  pass "C1. Area 미열람 즉시 자격 (count=$has_area)"
else
  fail "C1. Area 자격 통과 못함" "count=$has_area"
fi

# Resource 는 14일 미만이라 없어야 함
has_resource="$(echo "$body" | jq '[.[] | select(.paraStatus == "RESOURCE")] | length')"
if [[ "$has_resource" == "0" ]]; then
  pass "C2. Resource 는 저장 직후엔 미등장 (문서 §2.1 '2~4주 경과')"
else
  fail "C2. Resource 가 14일 안 됐는데 등장함" "count=$has_resource"
fi

# Archive 는 절대 X
has_archive="$(echo "$body" | jq '[.[] | select(.paraStatus == "ARCHIVE")] | length')"
if [[ "$has_archive" == "0" ]]; then
  pass "C3. Archive 는 모든 모드에서 제외 (eligible=false)"
else
  fail "C3. Archive 가 등장함" "count=$has_archive"
fi

skip "C4. Resource 14일+ 자격 (시간 의존)" "DB created_at 직접 수정 필요"
note "Resource 14일+ 검증: UPDATE link_data SET created_at = NOW() - INTERVAL 20 DAY WHERE host='example.com' AND title='Resource 글';"

# ============================================================================
#  D. 폴더 PARA cascade
# ============================================================================
section "D. 폴더 PARA 변경 → 링크 cascade (ERD §1.1)"

# Area 폴더의 PARA 를 RESOURCE 로 변경
result="$(req PUT "/folder/$AREA_ID" "{\"paraCategory\":\"RESOURCE\"}" "$COOKIE_A")"
status="$(extract_status "$result")"
if [[ "$status" == "200" ]]; then
  pass "D1. 폴더 PARA 변경 PUT 응답 OK"
else
  fail "D1. 폴더 PARA 변경 실패" "status=$status"
fi

# Area 에 저장돼있던 링크가 이제 paraStatus=RESOURCE 가 됐는지
result="$(req GET "/link/user/$ALICE" "" "$COOKIE_A")"
body="$(extract_body "$result")"
area_link_para="$(echo "$body" | jq -r '.[] | select(.title == "Area 글") | .paraStatus')"
if [[ "$area_link_para" == "RESOURCE" ]]; then
  pass "D2. 링크 PARAStatus cascade 동기화 (AREA → RESOURCE)"
else
  fail "D2. cascade 동작 안 함" "got=$area_link_para"
fi

# 되돌리기 (다른 테스트에 영향 안 가게)
req PUT "/folder/$AREA_ID" "{\"paraCategory\":\"AREA\"}" "$COOKIE_A" >/dev/null

# ============================================================================
#  E. 컨텍스트 매칭 (REMIND §3.3)
# ============================================================================
section "E. 컨텍스트 매칭"

# YouTube 링크 저장
req POST /link/create \
  "{\"link\":\"https://www.youtube.com/watch?v=test123\",\"title\":\"YT 영상\",\"folderId\":$PROJECT_ID}" \
  "" "$COOKIE_A" >/dev/null

# mode=youtube_ctx&host=www.youtube.com 으로 호출 — YouTube 영상만 떠야 함
result="$(req GET "/recommendation-weights/users/$ALICE/today?mode=youtube_ctx&host=www.youtube.com&limit=10" "" "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
yt_count="$(echo "$body" | jq 'length')"
all_yt="$(echo "$body" | jq '[.[] | select(.host == "www.youtube.com")] | length')"

if [[ "$status" == "200" ]] && [[ "$yt_count" -ge 1 ]] && [[ "$yt_count" == "$all_yt" ]]; then
  pass "E1. youtube_ctx 모드 — host=youtube.com 만 매칭 ($yt_count 개)"
else
  fail "E1. youtube_ctx 매칭 이상" "total=$yt_count yt만=$all_yt"
fi

# domain_ctx — 임의 호스트
result="$(req GET "/recommendation-weights/users/$ALICE/today?mode=domain_ctx&host=example.com&limit=10" "" "$COOKIE_A")"
body="$(extract_body "$result")"
ex_count="$(echo "$body" | jq 'length')"
all_ex="$(echo "$body" | jq '[.[] | select(.host == "example.com")] | length')"
if [[ "$ex_count" == "$all_ex" ]] && [[ "$ex_count" -ge 1 ]]; then
  pass "E2. domain_ctx 모드 — host=example.com 만 매칭"
else
  fail "E2. domain_ctx 매칭 이상" "total=$ex_count match=$all_ex"
fi

# domain_ctx host 누락 → 400
result="$(req GET "/recommendation-weights/users/$ALICE/today?mode=domain_ctx" "" "$COOKIE_A")"
status="$(extract_status "$result")"
if [[ "$status" == "400" ]]; then
  pass "E3. domain_ctx host 누락 → 400"
else
  fail "E3. host 누락 시 400 아님" "status=$status"
fi

# ============================================================================
#  F. markRead → /today 에서 제거
# ============================================================================
section "F. markRead 동작 (PRD §5 MVP)"

# 첫 링크 id 가져옴
result="$(req GET "/link/user/$ALICE" "" "$COOKIE_A")"
body="$(extract_body "$result")"
first_link_id="$(echo "$body" | jq -r '.[0].id')"

before="$(req GET "/recommendation-weights/users/$ALICE/today?limit=20" "" "$COOKIE_A" | tail -n +2 | jq '[.[] | select(.linkId == '"$first_link_id"')] | length')"

req POST "/link/$first_link_id/read" "" "$COOKIE_A" >/dev/null

after="$(req GET "/recommendation-weights/users/$ALICE/today?limit=20" "" "$COOKIE_A" | tail -n +2 | jq '[.[] | select(.linkId == '"$first_link_id"')] | length')"

if [[ "$before" -ge 1 ]] && [[ "$after" == "0" ]]; then
  pass "F1. markRead 후 /today 에서 즉시 제거 (before=$before, after=$after)"
else
  fail "F1. markRead 가 today 결과에 반영 안 됨" "before=$before after=$after"
fi

# ============================================================================
#  G. 스누즈 (REMIND §5.3)
# ============================================================================
section "G. 스누즈 (snoozedUntil)"

# 새 링크 저장
result="$(req POST /link/create \
  "{\"link\":\"https://example.com/snoozetest\",\"title\":\"스누즈 대상\",\"folderId\":$PROJECT_ID}" \
  "$COOKIE_A")"
new_link_id="$(req GET "/link/user/$ALICE" "" "$COOKIE_A" | tail -n +2 | jq -r '.[] | select(.title=="스누즈 대상") | .id' | head -1)"

# 추천 가중치 생성 (snoozedUntil 미래로)
future="$(date -u -v+1d '+%Y-%m-%dT%H:%M:%S' 2>/dev/null || date -u -d '+1 day' '+%Y-%m-%dT%H:%M:%S')"
result="$(req POST /recommendation-weights \
  "{\"bookmarkId\":$new_link_id,\"name\":\"스누즈 테스트\",\"importance\":0.5,\"snoozedUntil\":\"$future\"}" \
  "$COOKIE_A")"
status="$(extract_status "$result")"

if [[ "$status" == "200" ]]; then
  pass "G1. snoozedUntil 미래 시각 설정 OK"
else
  fail "G1. snoozedUntil POST 실패" "status=$status"
fi

# /today 에서 사라졌나
in_today="$(req GET "/recommendation-weights/users/$ALICE/today?limit=20" "" "$COOKIE_A" | tail -n +2 | jq '[.[] | select(.linkId == '"$new_link_id"')] | length')"
if [[ "$in_today" == "0" ]]; then
  pass "G2. 스누즈된 링크는 /today 에서 제외"
else
  fail "G2. 스누즈된 링크가 여전히 노출됨" "count=$in_today"
fi

# 스누즈 해제 (snoozeClear=true)
rw_id="$(req GET /recommendation-weights "" "$COOKIE_A" | tail -n +2 | jq -r '.[] | select(.bookmark.id == '"$new_link_id"') | .id' | head -1)"
req PUT "/recommendation-weights/$rw_id" '{"snoozeClear":true}' "$COOKIE_A" >/dev/null

in_today_after="$(req GET "/recommendation-weights/users/$ALICE/today?limit=20" "" "$COOKIE_A" | tail -n +2 | jq '[.[] | select(.linkId == '"$new_link_id"')] | length')"
if [[ "$in_today_after" -ge 1 ]]; then
  pass "G3. snoozeClear=true 후 다시 노출"
else
  fail "G3. 스누즈 해제 후 노출 안 됨" "count=$in_today_after"
fi

# ============================================================================
#  H. 소유권 차단
# ============================================================================
section "H. 소유권 검증 — 타인 데이터 접근 차단"

# bob 이 alice 의 link 목록 조회 → 403
result="$(req GET "/link/user/$ALICE" "" "$COOKIE_B")"
status="$(extract_status "$result")"
if [[ "$status" == "403" ]]; then
  pass "H1. 다른 사용자 링크 목록 조회 → 403"
else
  fail "H1. 소유권 검증 실패 (403 기대)" "status=$status"
fi

# bob 이 alice 의 /today 조회 → 403
result="$(req GET "/recommendation-weights/users/$ALICE/today" "" "$COOKIE_B")"
status="$(extract_status "$result")"
if [[ "$status" == "403" ]]; then
  pass "H2. 다른 사용자 /today 조회 → 403"
else
  fail "H2. /today 소유권 검증 실패" "status=$status"
fi

# 미인증 호출 → 401
result="$(req GET /me/reminder-prefs)"
status="$(extract_status "$result")"
if [[ "$status" == "401" ]]; then
  pass "H3. 미인증 → 401"
else
  fail "H3. 미인증 응답 이상" "status=$status"
fi

# ============================================================================
#  I. Bearer 토큰 인증 (익스텐션)
# ============================================================================
section "I. Bearer 토큰 인증"

# 토큰 발급 (세션 인증)
result="$(req POST /auth/api-tokens '{"name":"테스트 익스텐션"}' "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
plain_token="$(echo "$body" | jq -r '.plainToken // empty')"
token_id="$(echo "$body" | jq -r '.id // empty')"

if [[ "$status" == "200" ]] && [[ -n "$plain_token" ]] && [[ ${#plain_token} -ge 32 ]]; then
  pass "I1. API 토큰 발급 (id=$token_id length=${#plain_token})"
else
  fail "I1. 토큰 발급 실패" "status=$status body=$body"
fi

# 토큰만으로 (세션 없이) 보호된 엔드포인트 호출
result="$(req_bearer GET /me/reminder-prefs "$plain_token")"
status="$(extract_status "$result")"
if [[ "$status" == "200" ]]; then
  pass "I2. Bearer 토큰만으로 /me/reminder-prefs 인증 통과"
else
  fail "I2. Bearer 토큰 인증 실패" "status=$status"
fi

# 토큰 폐기
req DELETE "/auth/api-tokens/$token_id" "" "$COOKIE_A" >/dev/null

result="$(req_bearer GET /me/reminder-prefs "$plain_token")"
status="$(extract_status "$result")"
if [[ "$status" == "401" ]]; then
  pass "I3. 폐기된 토큰으로 호출 → 401"
else
  fail "I3. 폐기 후에도 통과됨" "status=$status"
fi

# ============================================================================
#  J. KPI 집계 API (PRD §6 / REMIND §9)
# ============================================================================
section "J. KPI 집계 API"

result="$(req GET /metrics/me/reminders "" "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
if [[ "$status" == "200" ]] && echo "$body" | jq -e 'has("totalSent") and has("totalOpened") and has("overallCtr")' >/dev/null; then
  pass "J1. /metrics/me/reminders 응답 스키마 OK"
else
  fail "J1. KPI 응답 스키마 이상" "body=$body"
fi

result="$(req GET /metrics/me/seven-day-click "" "$COOKIE_A")"
status="$(extract_status "$result")"
if [[ "$status" == "200" ]]; then
  pass "J2. /metrics/me/seven-day-click 응답 OK"
else
  # H2 인메모리는 INTERVAL 7 DAY 미지원 — 본 시나리오만 스킵
  if echo "$(extract_body "$result")" | grep -qi "interval\|h2\|sql"; then
    skip "J2. 7일 클릭률" "H2 DB는 native SQL 미지원, MySQL 필요"
  else
    fail "J2. 7일 클릭률 응답 실패" "status=$status"
  fi
fi

result="$(req GET /metrics/me/completion "" "$COOKIE_A")"
status="$(extract_status "$result")"
if [[ "$status" == "200" ]]; then
  pass "J3. /metrics/me/completion 응답 OK"
else
  fail "J3. completion 응답 실패" "status=$status"
fi

# ============================================================================
#  K. 에러 응답 형식 (GlobalExceptionHandler)
# ============================================================================
section "K. 에러 응답 형식"

# 잘못된 입력 — folderId 가 존재하지 않는 값
result="$(req POST /link/create '{"link":"https://x.com/a","title":"t","folderId":999999}' "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
if [[ "$status" == "400" ]] && echo "$body" | jq -e 'has("error") and has("message")' >/dev/null; then
  pass "K1. 잘못된 입력 → 400 + {error, message} JSON"
else
  fail "K1. 400 응답 형식 이상" "status=$status body=$body"
fi

# 존재하지 않는 폴더 ID
result="$(req PUT /folder/999999 '{"paraCategory":"PROJECT"}' "$COOKIE_A")"
status="$(extract_status "$result")"
if [[ "$status" == "400" ]] || [[ "$status" == "404" ]]; then
  pass "K2. 존재하지 않는 ID → 4xx"
else
  fail "K2. 4xx 미반환" "status=$status"
fi

# ============================================================================
#  L. 검색 엔드포인트
# ============================================================================
section "L. 검색"

result="$(req GET "/link/search?q=프로젝트" "" "$COOKIE_A")"
status="$(extract_status "$result")"
body="$(extract_body "$result")"
matches="$(echo "$body" | jq 'length')"
if [[ "$status" == "200" ]] && [[ "$matches" -ge 1 ]]; then
  pass "L1. 한글 title 부분 매칭 ($matches 건)"
else
  fail "L1. 검색 매칭 실패" "matches=$matches"
fi

result="$(req GET "/link/search?q=nonexistent_xyz_$RUN_ID" "" "$COOKIE_A")"
status="$(extract_status "$result")"
matches="$(extract_body "$result" | jq 'length')"
if [[ "$status" == "200" ]] && [[ "$matches" == "0" ]]; then
  pass "L2. 매칭 0건 정상 응답"
else
  fail "L2. 매칭 0건 케이스 이상" "matches=$matches"
fi

# ============================================================================
#  시간 의존 시나리오 안내
# ============================================================================
section "시간 의존 시나리오 (수동 검증)"

note "다음 시나리오는 created_at / sent_at 을 DB에서 직접 조작해야 검증 가능:"
note "1. Resource 14일+ 자격 → today 등장"
note "   UPDATE link_data SET created_at = NOW() - INTERVAL 20 DAY WHERE title='Resource 글';"
note "2. resurface mode (30일+) 자격"
note "   UPDATE link_data SET created_at = NOW() - INTERVAL 35 DAY WHERE title='Resource 글';"
note "3. fatigue 컷 (7일 윈도우 4번 발송)"
note "   POST /reminders/create 4번 호출 후 /today 점수 0 확인"
note "4. snoozedUntil 만료 (과거 시각) → 자동 활성화"
note "   UPDATE recommendation_weight SET snoozed_until = NOW() - INTERVAL 1 HOUR;"

echo
for n in "${NOTES[@]}"; do echo "  ${c_yellow}!${c_reset} $n"; done

# ============================================================================
#  결과 요약
# ============================================================================
echo
echo "${c_blue}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${c_reset}"
echo "${c_blue}  결과 요약${c_reset}"
echo "${c_blue}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${c_reset}"
echo "  ${c_green}통과: $PASS${c_reset}"
if [[ "$FAIL" -gt 0 ]]; then
  echo "  ${c_red}실패: $FAIL${c_reset}"
else
  echo "  실패: $FAIL"
fi
echo "  ${c_yellow}스킵: $SKIP${c_reset} (시간 의존 또는 DB 제약)"
echo

if [[ "$FAIL" -gt 0 ]]; then
  echo "${c_red}일부 시나리오가 실패했습니다. 실패 원인을 위 출력에서 확인하세요.${c_reset}"
  exit 1
else
  echo "${c_green}자동 검증 가능한 시나리오 전체 통과.${c_reset}"
  exit 0
fi
