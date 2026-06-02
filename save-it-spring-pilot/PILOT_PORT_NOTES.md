# save-it 프론트 → Spring 백엔드 시범 포팅

`/tmp/save-it` 원본을 그대로 두고 `/tmp/save-it-spring/` 에 사본을 떠서
**extension popup 의 데이터 계층만** Supabase SDK 에서 우리 Spring REST 로 갈아끼웠다.

## 무엇이 바뀌었나

### 추가
- `extension/lib/api.ts` — JWT 토큰 관리 + 401 자동 refresh + Spring REST wrapper
- `extension/.env` — `WXT_PUBLIC_API_BASE=http://localhost:8080`
- `extension-smoke.sh` — extension 이 호출하는 시퀀스를 그대로 curl 로 재현하는 E2E smoke test

### 교체
- `extension/lib/supabase.ts` → 삭제
- `extension/lib/useAuth.ts` → JWT 기반 (Supabase Session 의존 제거)
- `extension/lib/types.ts` → `id: string` → `id: number`, snake_case 유지
- `extension/entrypoints/popup/LoginView.tsx` → 회원가입/로그인 토글 추가
- `extension/entrypoints/popup/AppShell.tsx` → `userId` → `userName` prop
- `extension/entrypoints/popup/SaveView.tsx` → `getFlatFolders`/`createFolder`/`createLink`
- `extension/entrypoints/popup/BrowseView.tsx` → lazy load 로 폴더별 링크 fetch
- `extension/entrypoints/content/index.tsx` → AppShell prop 변경 반영
- `extension/wxt.config.ts` → host_permissions 를 Spring 도메인으로
- `extension/package.json` → `@supabase/supabase-js` 의존성 제거

## 빌드 결과

```
✔ Built extension in 5.7s
Σ Total size: 599.25 kB
```

Supabase SDK 가 빠졌는데 사이즈가 거의 같은 건 React/Tailwind 가 대부분 차지하기 때문.

## Smoke test 결과 (10/10 ✅)

```
✅ POST /auth/signup
✅ GET  /folder/user/{userName}      (4개 PARA 루트 자동 생성 확인)
✅ GET  /folder/{id}/contents
✅ POST /folder/create               (parentId 검증)
✅ POST /link/create                 (folder PARA → link PARA 자동 전파)
✅ GET  /link/folder/{id}
✅ POST /link/{id}/read              (Jackson `read` 필드)
✅ POST /auth/refresh                (토큰 회전)
✅ 401 동작 확인 (invalid Bearer)
```

## 포팅 중 발견한 트랩

1. **save-it 의 미지정 폴더 (`folder_id=null`) 모델이 우리에겐 없음**
   → 미지정 PARA 탭 (5번째 칩) 을 UI 에서 제거. 우리는 모든 링크가 폴더에 속함.

2. **폴더 모델이 평탄 vs 2단**
   → `getFlatFolders()` 헬퍼가 PARA 루트의 children 을 평탄화해서
   save-it UI 가 기대하는 `{id, name, para_category}` 형태로 반환.

3. **API 경로**
   - `/folder/user/{userName}` (NOT `/folder/root/...`)
   - `/folder/{id}/contents` (NOT `/folder/{id}`)
   - `/folder/create` (NOT `/folder`)
   - `/link/create` (NOT `/link`)

4. **Jackson 직렬화**
   - `boolean isRead` → JSON `read` (앞 `is` 제거)
   - `AuthResponse` 의 ID 필드는 `id` (NOT `userId`)

5. **CORS**
   - 우리 `SecurityConfig` 가 `app.cors.allowed-origins` 환경변수로 도메인 제한.
     로컬 개발 땐 `http://localhost:3000` 만 허용되므로 extension popup 호출은
     `host_permissions` (manifest) 의 fetch 권한으로 우회 (extension 은 CORS pre-flight 없음).

6. **폴더 생성 응답이 ID 미반환**
   - Spring 의 `/folder/create` 가 `"폴더 생성 완료"` 문자열만 돌려줌
   - 새 폴더의 id 를 알려면 직후 `getRootFolders()` + `getFolderContents(root)` 로 재조회
   - 개선 여지: `FolderResponse` 를 반환하도록 변경하면 1 round-trip 절약

## 다음 단계 (실제 Chrome 에 로드해서 보고 싶다면)

```bash
# 1. Spring 실행 (이미 켜져있음)
curl -s http://localhost:8080/health   # → {"status":"ok"}

# 2. Chrome → chrome://extensions
#    "압축해제된 확장 프로그램을 로드합니다" 선택
#    /tmp/save-it-spring/extension/.output/chrome-mv3  폴더 선택

# 3. 아무 페이지에서 우상단 floating bookmark 클릭
#    → 회원가입 → PARA 카테고리 선택 → 폴더 생성 → 링크 저장
```

## 시사점 (프론트 담당자에게 전할 메시지)

이번 포팅은 **데이터 계층만 갈아끼우는 작업이 충분히 합리적인 규모**임을 증명함.
- UI 코드 (`Popup.tsx`/`SaveView` 의 form 구조/Tailwind 스타일/PARA 칩) 는 거의 그대로
- 바뀐 곳은 `lib/api.ts` (신규) 와 각 view 의 `supabase.from(...)` 호출 5~6 군데뿐
- 총 작업량: 4시간 미만

`INTEGRATION.md` 와 이 포팅 결과를 같이 보면 프론트 담당자가 본인 페이스로
한 화면씩 이주 가능. Supabase Realtime / Auth UI / Storage 같은 기능을
의존하지 않는 화면들이라 마찰점이 거의 없었음.
