#!/usr/bin/env bash
# Extension 시범 포팅의 API 호출 시퀀스를 그대로 흉내내는 smoke test.
# 목적: 우리 Spring 백엔드가 api.ts 가 보낼 정확한 요청 shape을 제대로 받아주는지 검증.

set -euo pipefail

API="${API:-http://localhost:8080}"
USERNAME="pilot_$(date +%s%N | tail -c 10)"
PASSWORD="pilot-strong-pw"

pass() { echo "✅ $*"; }
fail() { echo "❌ $*"; exit 1; }
section() { echo; echo "── $* ────────────────────────"; }

section "1) POST /auth/signup"
SIGNUP=$(curl -s -X POST "$API/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{\"userName\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")
ACCESS=$(echo "$SIGNUP" | jq -r '.accessToken')
REFRESH=$(echo "$SIGNUP" | jq -r '.refreshToken')
USERID=$(echo "$SIGNUP" | jq -r '.id')
[[ "$ACCESS" != "null" && -n "$ACCESS" ]] || fail "signup: accessToken 없음 ($SIGNUP)"
[[ "$REFRESH" != "null" && -n "$REFRESH" ]] || fail "signup: refreshToken 없음"
pass "signup OK userId=$USERID len(access)=${#ACCESS}"

section "2) GET /folder/root/{userName}  ← getRootFolders()"
ROOTS=$(curl -s "$API/folder/user/$USERNAME" -H "Authorization: Bearer $ACCESS")
ROOT_COUNT=$(echo "$ROOTS" | jq 'length')
PROJECT_ID=$(echo "$ROOTS" | jq -r '.[] | select(.paraCategory=="PROJECT") | .id')
AREA_ID=$(echo "$ROOTS" | jq -r '.[] | select(.paraCategory=="AREA") | .id')
[[ "$ROOT_COUNT" -ge 4 ]] || fail "root 폴더 4개 미만 (got=$ROOT_COUNT)"
[[ -n "$PROJECT_ID" ]] || fail "PROJECT 루트 없음"
pass "root folders=$ROOT_COUNT  PROJECT=$PROJECT_ID  AREA=$AREA_ID"

section "3) GET /folder/{id}  ← getFolderContents(PROJECT root)"
CONTENTS=$(curl -s "$API/folder/$PROJECT_ID/contents" -H "Authorization: Bearer $ACCESS")
SUB_COUNT=$(echo "$CONTENTS" | jq '.subFolders | length')
LINK_COUNT=$(echo "$CONTENTS" | jq '.links | length')
pass "PROJECT contents subs=$SUB_COUNT links=$LINK_COUNT (신규 가입자 → 0,0 예상)"

section "4) POST /folder  ← createFolder(userName, name, parentId=PROJECT)"
CREATE=$(curl -s -X POST "$API/folder/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS" \
  -d "{\"userName\":\"$USERNAME\",\"name\":\"Side Hustle\",\"parentId\":$PROJECT_ID}")
echo "$CREATE" | grep -q "완료" || fail "folder 생성 응답 이상: $CREATE"
pass "folder 생성 응답 OK"

section "5) GET /folder/{PROJECT_ID}  ← 새 폴더 보이는지 확인"
CONTENTS2=$(curl -s "$API/folder/$PROJECT_ID/contents" -H "Authorization: Bearer $ACCESS")
SIDE_ID=$(echo "$CONTENTS2" | jq -r '.subFolders[] | select(.name=="Side Hustle") | .id')
[[ -n "$SIDE_ID" && "$SIDE_ID" != "null" ]] || fail "Side Hustle 폴더 찾을 수 없음: $CONTENTS2"
pass "Side Hustle 폴더 id=$SIDE_ID 확인"

section "6) POST /link/create  ← createLink({userName, link, title, folderId})"
LINK_RES=$(curl -s -X POST "$API/link/create" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS" \
  -d "{\"userName\":\"$USERNAME\",\"link\":\"https://example.com/blog/1\",\"title\":\"Test Article\",\"folderId\":$SIDE_ID}")
LINK_ID=$(echo "$LINK_RES" | jq -r '.id')
LINK_PARA=$(echo "$LINK_RES" | jq -r '.paraStatus')
[[ -n "$LINK_ID" && "$LINK_ID" != "null" ]] || fail "링크 저장 실패: $LINK_RES"
[[ "$LINK_PARA" == "PROJECT" ]] || fail "paraStatus 자동 전파 실패: got=$LINK_PARA"
pass "링크 저장 id=$LINK_ID paraStatus=$LINK_PARA (folder PARA 자동 상속 OK)"

section "7) GET /link/folder/{id}  ← getLinksByFolder()"
LINKS=$(curl -s "$API/link/folder/$SIDE_ID" -H "Authorization: Bearer $ACCESS")
LINKS_COUNT=$(echo "$LINKS" | jq 'length')
FIRST_TITLE=$(echo "$LINKS" | jq -r '.[0].title')
[[ "$LINKS_COUNT" == "1" ]] || fail "폴더 링크 개수 불일치: $LINKS_COUNT"
[[ "$FIRST_TITLE" == "Test Article" ]] || fail "제목 불일치"
pass "폴더 링크 조회 OK ($LINKS_COUNT개, title=$FIRST_TITLE)"

section "8) POST /link/{id}/read  ← markLinkRead()"
READ_RES=$(curl -s -X POST "$API/link/$LINK_ID/read" -H "Authorization: Bearer $ACCESS")
LINKS2=$(curl -s "$API/link/folder/$SIDE_ID" -H "Authorization: Bearer $ACCESS")
# Jackson 이 boolean isRead 를 JSON `read` 로 직렬화함
IS_READ=$(echo "$LINKS2" | jq -r '.[0].read')
[[ "$IS_READ" == "true" ]] || fail "isRead=true 안 됨 (응답=$READ_RES, 링크=$LINKS2)"
pass "isRead=true 전파됨"

section "9) POST /auth/refresh  ← 토큰 자동 갱신"
RESP=$(curl -s -X POST "$API/auth/refresh" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH\"}")
NEW_ACCESS=$(echo "$RESP" | jq -r '.accessToken')
[[ -n "$NEW_ACCESS" && "$NEW_ACCESS" != "null" && "$NEW_ACCESS" != "$ACCESS" ]] || fail "새 access token 발급 안 됨"
pass "refresh 로 새 access 발급 OK"

section "10) 401 동작 확인 (구 토큰으로 호출 → 401)"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$API/folder/root/$USERNAME" \
  -H "Authorization: Bearer invalid.jwt.token")
[[ "$HTTP_CODE" == "401" ]] || fail "잘못된 토큰에 401 아님: $HTTP_CODE"
pass "invalid token → 401 OK"

echo
echo "🎉 모든 시범 포팅 API 시나리오 통과! ($USERNAME)"
