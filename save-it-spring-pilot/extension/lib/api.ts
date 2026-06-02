/**
 * Spring REST API client — Supabase SDK 대체.
 *
 * 토큰 관리:
 *   - access  : chrome.storage.local["saveit_access"]   (15분 유효)
 *   - refresh : chrome.storage.local["saveit_refresh"]  (7일 유효)
 *   - userName: chrome.storage.local["saveit_user"]
 *
 * 401 발생 시 1회 자동 refresh 시도 후 재시도. refresh 도 실패하면 토큰 폐기.
 */

const API_BASE =
  (import.meta.env.WXT_PUBLIC_API_BASE as string | undefined) ||
  "http://localhost:8080";

const KEY_ACCESS = "saveit_access";
const KEY_REFRESH = "saveit_refresh";
const KEY_USER = "saveit_user";

// ──────────────────────────────────────────────────────────────────────
// 토큰 store
// ──────────────────────────────────────────────────────────────────────

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  userName: string;
  userId: number;
}

export async function loadTokens(): Promise<AuthTokens | null> {
  const r = await browser.storage.local.get([KEY_ACCESS, KEY_REFRESH, KEY_USER]);
  if (!r[KEY_ACCESS] || !r[KEY_REFRESH] || !r[KEY_USER]) return null;
  const u = r[KEY_USER] as { userName: string; userId: number };
  return {
    accessToken: r[KEY_ACCESS] as string,
    refreshToken: r[KEY_REFRESH] as string,
    userName: u.userName,
    userId: u.userId,
  };
}

async function saveTokens(t: AuthTokens): Promise<void> {
  await browser.storage.local.set({
    [KEY_ACCESS]: t.accessToken,
    [KEY_REFRESH]: t.refreshToken,
    [KEY_USER]: { userName: t.userName, userId: t.userId },
  });
}

async function clearTokens(): Promise<void> {
  await browser.storage.local.remove([KEY_ACCESS, KEY_REFRESH, KEY_USER]);
}

// 외부에서 토큰 갱신 이벤트를 구독할 수 있도록 이벤트 리스너 등록.
type AuthListener = (t: AuthTokens | null) => void;
const listeners = new Set<AuthListener>();
export function onAuthChange(l: AuthListener): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}
function emit(t: AuthTokens | null) {
  for (const l of listeners) l(t);
}

// ──────────────────────────────────────────────────────────────────────
// 저수준 fetch (401 자동 refresh)
// ──────────────────────────────────────────────────────────────────────

class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function fetchWithAuth(
  path: string,
  init: RequestInit = {},
  retry = true,
): Promise<Response> {
  const tokens = await loadTokens();
  const headers = new Headers(init.headers);
  if (tokens) headers.set("Authorization", `Bearer ${tokens.accessToken}`);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });

  if (res.status === 401 && retry && tokens) {
    const refreshed = await tryRefresh(tokens.refreshToken, tokens.userName, tokens.userId);
    if (refreshed) return fetchWithAuth(path, init, false);
    await clearTokens();
    emit(null);
  }
  return res;
}

async function tryRefresh(
  refreshToken: string,
  userName: string,
  userId: number,
): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const data = await res.json();
    const t: AuthTokens = {
      accessToken: data.accessToken,
      refreshToken: data.refreshToken,
      userName,
      userId,
    };
    await saveTokens(t);
    emit(t);
    return true;
  } catch {
    return false;
  }
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new ApiError(res.status, text || `HTTP ${res.status}`);
  }
  const ct = res.headers.get("Content-Type") ?? "";
  if (ct.includes("application/json")) return (await res.json()) as T;
  return (await res.text()) as unknown as T;
}

// ──────────────────────────────────────────────────────────────────────
// Public API
// ──────────────────────────────────────────────────────────────────────

export type ParaCategory = "PROJECT" | "AREA" | "RESOURCE" | "ARCHIVE";

export interface SpringFolderResponse {
  id: number;
  name: string;
  parentId: number | null;
  paraCategory: ParaCategory | null;
}

export interface SpringLinkResponse {
  id: number;
  link: string;
  title: string;
  paraStatus: ParaCategory | null;
  lastUpdate: string;
  createdAt?: string;
  /**
   * 읽음 여부. Jackson 이 `boolean isRead` getter 를 JSON `read` 로 직렬화하므로
   * 우리도 `read` 로 받는다 (Spring 백엔드의 `LinkData.isRead` ↔ `LinkResponse.isRead`).
   */
  read?: boolean;
  readAt?: string | null;
  host?: string | null;
  contentType?: string | null;
  thumbnailUrl?: string | null;
  folderId?: number | null;
}

export interface SpringFolderContents {
  subFolders: SpringFolderResponse[];
  links: SpringLinkResponse[];
}

// ── Auth ──────────────────────────────────────────────────────────────

/** Spring AuthResponse — 필드명이 `id` (userId 아님). */
export interface AuthResponse {
  id: number;
  userName: string;
  accessToken: string;
  refreshToken: string;
}

export async function signup(userName: string, password: string): Promise<AuthTokens> {
  const res = await fetch(`${API_BASE}/auth/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userName, password }),
  });
  const data = await unwrap<AuthResponse>(res);
  const t: AuthTokens = {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    userName: data.userName,
    userId: data.id,
  };
  await saveTokens(t);
  emit(t);
  return t;
}

export async function login(userName: string, password: string): Promise<AuthTokens> {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ userName, password }),
  });
  const data = await unwrap<AuthResponse>(res);
  const t: AuthTokens = {
    accessToken: data.accessToken,
    refreshToken: data.refreshToken,
    userName: data.userName,
    userId: data.id,
  };
  await saveTokens(t);
  emit(t);
  return t;
}

export async function logout(): Promise<void> {
  await clearTokens();
  emit(null);
}

// ── Folders ───────────────────────────────────────────────────────────

/** 사용자의 PARA 루트 폴더 4개 조회. */
export async function getRootFolders(userName: string): Promise<SpringFolderResponse[]> {
  const res = await fetchWithAuth(`/folder/user/${encodeURIComponent(userName)}`);
  return unwrap<SpringFolderResponse[]>(res);
}

/** 폴더의 하위 폴더 + 링크 조회. */
export async function getFolderContents(folderId: number): Promise<SpringFolderContents> {
  const res = await fetchWithAuth(`/folder/${folderId}/contents`);
  return unwrap<SpringFolderContents>(res);
}

/** 폴더 생성 — parentId 필수 (PARA 루트 아래에만 만들 수 있음). */
export async function createFolder(
  userName: string,
  name: string,
  parentId: number,
): Promise<void> {
  const res = await fetchWithAuth(`/folder/create`, {
    method: "POST",
    body: JSON.stringify({ userName, name, parentId }),
  });
  await unwrap<string>(res);
}

// ── Links ─────────────────────────────────────────────────────────────

export interface LinkCreateRequest {
  userName: string;
  link: string;
  title: string;
  folderId: number;
}

/**
 * 링크 생성 — `paraStatus` 는 서버가 폴더로부터 자동 설정 (ERD §1.1).
 * 클라가 명시적으로 보낼 필요 없음.
 */
export async function createLink(req: LinkCreateRequest): Promise<SpringLinkResponse> {
  const res = await fetchWithAuth(`/link/create`, {
    method: "POST",
    body: JSON.stringify(req),
  });
  return unwrap<SpringLinkResponse>(res);
}

export async function getUserLinks(userName: string): Promise<SpringLinkResponse[]> {
  const res = await fetchWithAuth(`/link/user/${encodeURIComponent(userName)}`);
  return unwrap<SpringLinkResponse[]>(res);
}

/** 링크 읽음 처리 — POST /link/{id}/read. */
export async function markLinkRead(id: number): Promise<void> {
  await fetchWithAuth(`/link/${id}/read`, { method: "POST" });
}

/** 폴더별 링크 목록. */
export async function getLinksByFolder(folderId: number): Promise<SpringLinkResponse[]> {
  const res = await fetchWithAuth(`/link/folder/${folderId}`);
  return unwrap<SpringLinkResponse[]>(res);
}

// ── Recommendations (today) ───────────────────────────────────────────

/**
 * "오늘 다시 볼 링크" 후보.
 *
 * REMIND_STRATEGY 의 bell curve 스코어링.
 *   - mode=today: 미열람 + 경과일 최적 (peak: P=2d / A=7d / R=21d)
 *   - 우리 백엔드는 paraStatus 자동, fatigue 적용, 점수 내림차순 정렬
 *
 * Spring record `isRead()` 는 JSON `isRead` 로 직렬화됨 (lombok @Getter 와 다름).
 */
export interface RemindCandidate {
  linkId: number;
  link: string;
  title: string;
  paraStatus: ParaCategory | null;
  isRead: boolean;
  createdAt: string | null;
  readAt: string | null;
  lastUpdate: string | null;
  mode: string;
  reason: string;
  remindScore: number;
  breakdown?: {
    paraMultiplier: number;
    unreadFactor: number;
    importance: number;
    bellRecency: number;
    fatigueFactor: number;
    rawScore: number;
    ageDays: number;
    recentRemindCount: number;
  };
}

export async function getTodayRecommendations(
  userName: string,
  limit = 10,
): Promise<RemindCandidate[]> {
  const res = await fetchWithAuth(
    `/recommendation-weights/users/${encodeURIComponent(userName)}/today?limit=${limit}`,
  );
  return unwrap<RemindCandidate[]>(res);
}

// ──────────────────────────────────────────────────────────────────────
// 정규화 — save-it 의 평탄 폴더 모델로 변환
// ──────────────────────────────────────────────────────────────────────

/**
 * save-it UI가 기대하는 형식:
 *   { id, name, para_category }  (평탄 리스트, 미지정은 para_category=null)
 *
 * 우리 Spring 의 2단 구조:
 *   PARA 루트 (PROJECT/AREA/RESOURCE/ARCHIVE) → 그 아래 사용자 폴더
 *
 * PARA 루트는 UI 에 보이지 않게 숨기고, 그 아래 사용자 폴더만 평탄하게 반환.
 */
export async function getFlatFolders(userName: string): Promise<{
  folders: NormalizedFolder[];
  paraRoots: Record<ParaCategory, number>;
}> {
  const roots = await getRootFolders(userName);
  const paraRoots: Partial<Record<ParaCategory, number>> = {};
  const childrenPromises: Promise<{ root: SpringFolderResponse; subs: SpringFolderResponse[] }>[] = [];

  for (const root of roots) {
    if (root.parentId !== null) continue;            // 안전망
    if (!root.paraCategory) continue;                // PARA 미지정 루트는 skip
    paraRoots[root.paraCategory] = root.id;
    childrenPromises.push(
      getFolderContents(root.id).then((c) => ({ root, subs: c.subFolders })),
    );
  }

  const results = await Promise.all(childrenPromises);
  const folders: NormalizedFolder[] = [];
  for (const { root, subs } of results) {
    for (const s of subs) {
      folders.push({
        id: s.id,
        name: s.name,
        para_category: lowerPara(root.paraCategory),
      });
    }
  }
  return { folders, paraRoots: paraRoots as Record<ParaCategory, number> };
}

export interface NormalizedFolder {
  id: number;
  name: string;
  para_category: "project" | "area" | "resource" | "archive" | null;
}

export function lowerPara(p: ParaCategory | null): NormalizedFolder["para_category"] {
  if (!p) return null;
  return p.toLowerCase() as NormalizedFolder["para_category"];
}

export function upperPara(
  p: NormalizedFolder["para_category"] | "project" | "area" | "resource" | "archive",
): ParaCategory | null {
  if (!p) return null;
  return p.toUpperCase() as ParaCategory;
}
