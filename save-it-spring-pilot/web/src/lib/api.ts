/**
 * Spring REST API client — web 용 (Supabase SDK 대체).
 *
 * 토큰 관리:
 *   - access  : localStorage["saveit_access"]   (15분 유효)
 *   - refresh : localStorage["saveit_refresh"]  (7일 유효)
 *   - user    : localStorage["saveit_user"]     ({userName, userId})
 *
 * 401 발생 시 1회 자동 refresh 시도 후 재시도. refresh 도 실패하면 토큰 폐기.
 *
 * SSR 안전성: typeof window === "undefined" 체크. 서버 컴포넌트에서는 토큰 못 읽음.
 * → 인증 필요한 데이터는 'use client' 컴포넌트에서 useEffect 로 fetch.
 */

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";

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

export function loadTokens(): AuthTokens | null {
  if (typeof window === "undefined") return null; // SSR 가드
  const access = localStorage.getItem(KEY_ACCESS);
  const refresh = localStorage.getItem(KEY_REFRESH);
  const userRaw = localStorage.getItem(KEY_USER);
  if (!access || !refresh || !userRaw) return null;
  try {
    const user = JSON.parse(userRaw) as { userName: string; userId: number };
    return {
      accessToken: access,
      refreshToken: refresh,
      userName: user.userName,
      userId: user.userId,
    };
  } catch {
    return null;
  }
}

function saveTokens(t: AuthTokens): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(KEY_ACCESS, t.accessToken);
  localStorage.setItem(KEY_REFRESH, t.refreshToken);
  localStorage.setItem(
    KEY_USER,
    JSON.stringify({ userName: t.userName, userId: t.userId }),
  );
}

function clearTokens(): void {
  if (typeof window === "undefined") return;
  localStorage.removeItem(KEY_ACCESS);
  localStorage.removeItem(KEY_REFRESH);
  localStorage.removeItem(KEY_USER);
}

// 외부에서 토큰 갱신 이벤트를 구독할 수 있도록 이벤트 리스너 등록.
type AuthListener = (t: AuthTokens | null) => void;
const listeners = new Set<AuthListener>();
export function onAuthChange(l: AuthListener): () => void {
  listeners.add(l);
  return () => {
    listeners.delete(l);
  };
}
function emit(t: AuthTokens | null) {
  for (const l of listeners) l(t);
}

// ──────────────────────────────────────────────────────────────────────
// 저수준 fetch (401 자동 refresh)
// ──────────────────────────────────────────────────────────────────────

export class ApiError extends Error {
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
  const tokens = loadTokens();
  const headers = new Headers(init.headers);
  if (tokens) headers.set("Authorization", `Bearer ${tokens.accessToken}`);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });

  if (res.status === 401 && retry && tokens) {
    const refreshed = await tryRefresh();
    if (refreshed) return fetchWithAuth(path, init, false);
    clearTokens();
    emit(null);
  }
  return res;
}

// ── single-flight refresh (A: race 방지) ──
// 동시에 여러 요청이 401 → 각자 refresh 하면 같은 refresh 토큰을 중복 사용해
// 백엔드에서 회전 충돌(과거 StaleObjectStateException) 발생.
// 진행 중인 refresh 가 있으면 그 Promise 를 공유해서 refresh 호출을 1회로 합친다.
let refreshInFlight: Promise<boolean> | null = null;

function tryRefresh(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = (async () => {
    const tokens = loadTokens(); // 실행 시점의 최신 토큰 사용 (stale 캡처 방지)
    if (!tokens) return false;
    try {
      const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: tokens.refreshToken }),
      });
      if (!res.ok) return false;
      const data = await res.json();
      const t: AuthTokens = {
        accessToken: data.accessToken,
        refreshToken: data.refreshToken,
        userName: tokens.userName,
        userId: tokens.userId,
      };
      saveTokens(t);
      emit(t);
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

async function unwrap<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    let message = text || `HTTP ${res.status}`;
    try {
      const obj = JSON.parse(text);
      if (typeof obj?.message === "string") message = obj.message;
      else if (typeof obj?.error === "string") message = obj.error;
    } catch {
      /* keep text */
    }
    throw new ApiError(res.status, message);
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
  /** Jackson 이 boolean isRead getter 를 JSON `read` 로 직렬화 */
  read?: boolean;
  readAt?: string | null;
  host?: string | null;
  contentType?: string | null;
  thumbnailUrl?: string | null;
  folderId?: number | null;
  /** 우선도 0=보통 / 1=중요 / 2=매우 (백엔드가 importance 에서 역매핑). */
  priority?: number;
}

export interface SpringFolderContents {
  subFolders: SpringFolderResponse[];
  links: SpringLinkResponse[];
}

// ── Auth ──────────────────────────────────────────────────────────────

export interface AuthResponse {
  id: number;
  userName: string;
  accessToken: string;
  refreshToken: string;
}

export async function signup(
  userName: string,
  password: string,
): Promise<AuthTokens> {
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
  saveTokens(t);
  emit(t);
  return t;
}

export async function login(
  userName: string,
  password: string,
): Promise<AuthTokens> {
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
  saveTokens(t);
  emit(t);
  return t;
}

export async function logout(): Promise<void> {
  const tokens = loadTokens();

  // 1) 푸시 구독 해제 — 토큰 지우기 전에 (서버 DELETE 가 Bearer 인증 필요).
  //    이걸 안 하면 로그아웃해도 이 기기로 계속 알림이 옴.
  try {
    if (typeof navigator !== "undefined" && "serviceWorker" in navigator) {
      const reg = await navigator.serviceWorker.getRegistration("/");
      const sub = await reg?.pushManager.getSubscription();
      if (sub) {
        await unsubscribePushOnServer(sub.endpoint).catch(() => {});
        await sub.unsubscribe().catch(() => {});
      }
    }
  } catch {
    /* best effort — 실패해도 로그아웃은 진행 */
  }

  // 2) refresh token 폐기
  if (tokens) {
    fetch(`${API_BASE}/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: tokens.refreshToken }),
    }).catch(() => {});
  }

  // 3) 클라 토큰 정리
  clearTokens();
  emit(null);
}

// ── Folders ───────────────────────────────────────────────────────────

export async function getRootFolders(
  userName: string,
): Promise<SpringFolderResponse[]> {
  const res = await fetchWithAuth(
    `/folder/user/${encodeURIComponent(userName)}`,
  );
  return unwrap<SpringFolderResponse[]>(res);
}

export async function getFolderContents(
  folderId: number,
): Promise<SpringFolderContents> {
  const res = await fetchWithAuth(`/folder/${folderId}/contents`);
  return unwrap<SpringFolderContents>(res);
}

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

/** 폴더 삭제 — 백엔드가 PARA 루트 폴더는 거부(400). 하위 링크는 함께 정리됨. */
export async function deleteFolder(folderId: number): Promise<void> {
  const res = await fetchWithAuth(`/folder/${folderId}`, { method: "DELETE" });
  await unwrap<string>(res);
}

// ── Links ─────────────────────────────────────────────────────────────

export interface LinkCreateRequest {
  userName: string;
  link: string;
  title: string;
  folderId: number;
  /** 우선도 0=보통 / 1=중요 / 2=매우. 생략 시 보통. */
  priority?: number;
}

export async function createLink(
  req: LinkCreateRequest,
): Promise<SpringLinkResponse> {
  const res = await fetchWithAuth(`/link/create`, {
    method: "POST",
    body: JSON.stringify(req),
  });
  return unwrap<SpringLinkResponse>(res);
}

export async function getUserLinks(
  userName: string,
): Promise<SpringLinkResponse[]> {
  const res = await fetchWithAuth(`/link/user/${encodeURIComponent(userName)}`);
  return unwrap<SpringLinkResponse[]>(res);
}

export async function markLinkRead(id: number): Promise<void> {
  await fetchWithAuth(`/link/${id}/read`, { method: "POST" });
}

/** 링크 삭제 — 가중치/태그 정션도 백엔드가 함께 정리. */
export async function deleteLink(id: number): Promise<void> {
  const res = await fetchWithAuth(`/link/${id}`, { method: "DELETE" });
  await unwrap<string>(res);
}

export async function getLinksByFolder(
  folderId: number,
): Promise<SpringLinkResponse[]> {
  const res = await fetchWithAuth(`/link/folder/${folderId}`);
  return unwrap<SpringLinkResponse[]>(res);
}

// ── Web Push ──────────────────────────────────────────────────────────

export async function getVapidPublicKey(): Promise<string> {
  const res = await fetch(`${API_BASE}/push/vapid-public-key`);
  const data = await unwrap<{ publicKey: string }>(res);
  return data.publicKey;
}

export interface PushSubscribePayload {
  endpoint: string;
  keys: { p256dh: string; auth: string };
}

export async function subscribePushOnServer(
  sub: PushSubscribePayload,
): Promise<void> {
  const res = await fetchWithAuth(`/push/subscribe`, {
    method: "POST",
    body: JSON.stringify(sub),
  });
  await unwrap<{ ok: boolean }>(res);
}

export async function unsubscribePushOnServer(
  endpoint: string,
): Promise<void> {
  const res = await fetchWithAuth(`/push/subscribe`, {
    method: "DELETE",
    body: JSON.stringify({ endpoint }),
  });
  await unwrap<{ ok: boolean }>(res);
}

export interface PushTestResult {
  sent: number;
  removed: number;
  failed: number;
  candidates: number;
}

export async function sendTestPush(): Promise<PushTestResult> {
  const res = await fetchWithAuth(`/push/test`, { method: "POST" });
  return unwrap(res);
}

export async function sendTestWeeklyPush(): Promise<PushTestResult> {
  const res = await fetchWithAuth(`/push/test-weekly`, { method: "POST" });
  return unwrap(res);
}

export function base64UrlToUint8Array(base64: string): Uint8Array {
  const padding = "=".repeat((4 - (base64.length % 4)) % 4);
  const base64Std = (base64 + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64Std);
  const arr = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) arr[i] = raw.charCodeAt(i);
  return arr;
}

/**
 * 로그인/앱 로드 시 호출 — 알림이 켜져 있는데 이 기기가 구독 안 됐으면 자동 재구독.
 *
 * 조건:
 *   - 권한이 이미 "granted" (이전에 허용함) → prompt 없이 조용히 구독 가능
 *   - prefs.dailyEnabled 또는 weeklyEnabled 가 true
 *   - 현재 구독 없음
 *
 * 로그아웃 시 구독을 끊으므로, 재로그인하면 이걸로 자동 복구됨.
 * 권한이 default/denied 면 (user gesture 필요) 아무것도 안 함 — 설정에서 수동.
 */
export async function ensurePushIfEnabled(): Promise<void> {
  if (typeof window === "undefined") return;
  if (
    !("serviceWorker" in navigator) ||
    !("PushManager" in window) ||
    !("Notification" in window)
  )
    return;
  if (Notification.permission !== "granted") return; // 권한 없으면 silent 구독 불가

  let prefs: ReminderPrefs;
  try {
    prefs = await getReminderPrefs();
  } catch {
    return;
  }
  if (!prefs.dailyEnabled && !prefs.weeklyEnabled) return; // 알림 자체 OFF

  const reg0 = await navigator.serviceWorker.getRegistration("/");
  const existing = await reg0?.pushManager.getSubscription();
  if (existing) return; // 이미 구독됨

  // 재구독 (granted 상태라 prompt 안 뜸)
  const reg = await navigator.serviceWorker.register("/sw.js", { scope: "/" });
  await navigator.serviceWorker.ready;
  const vapidPublic = await getVapidPublicKey();
  const keyArr = base64UrlToUint8Array(vapidPublic);
  const applicationServerKey = keyArr.buffer.slice(
    keyArr.byteOffset,
    keyArr.byteOffset + keyArr.byteLength,
  ) as ArrayBuffer;
  const sub = await reg.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey,
  });
  await subscribePushOnServer(toSubscribePayload(sub));
}

export function toSubscribePayload(
  sub: PushSubscription,
): PushSubscribePayload {
  const json = sub.toJSON() as {
    endpoint?: string;
    keys?: { p256dh?: string; auth?: string };
  };
  return {
    endpoint: json.endpoint ?? "",
    keys: {
      p256dh: json.keys?.p256dh ?? "",
      auth: json.keys?.auth ?? "",
    },
  };
}

// ── Reminder Prefs (개인화 알림 설정) ─────────────────────────────────

export interface ReminderPrefs {
  userId: number;
  dailyEnabled: boolean;
  /** "HH:mm:ss" 또는 "HH:mm" */
  dailyTime: string;
  timezone: string;
  weeklyEnabled: boolean;
  /** 1(월)~7(일) */
  weeklyDow: number;
  weeklyTime: string;
  emailEnabled: boolean;
  maxItemsPerReminder: number;
}

export async function getReminderPrefs(): Promise<ReminderPrefs> {
  const res = await fetchWithAuth(`/me/reminder-prefs`);
  return unwrap<ReminderPrefs>(res);
}

export async function updateReminderPrefs(
  patch: Partial<{
    dailyEnabled: boolean;
    dailyTime: string; // "HH:mm"
    timezone: string;
    weeklyEnabled: boolean;
    weeklyDow: number;
    weeklyTime: string; // "HH:mm"
    emailEnabled: boolean;
    maxItemsPerReminder: number;
  }>,
): Promise<ReminderPrefs> {
  const res = await fetchWithAuth(`/me/reminder-prefs`, {
    method: "PUT",
    body: JSON.stringify(patch),
  });
  return unwrap<ReminderPrefs>(res);
}

// ── Recommendations (today) ───────────────────────────────────────────

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

export interface NormalizedFolder {
  id: number;
  name: string;
  para_category: "project" | "area" | "resource" | "archive" | null;
}

export async function getFlatFolders(userName: string): Promise<{
  folders: NormalizedFolder[];
  paraRoots: Record<ParaCategory, number>;
}> {
  const roots = await getRootFolders(userName);
  const paraRoots: Partial<Record<ParaCategory, number>> = {};
  const childrenPromises: Promise<{
    root: SpringFolderResponse;
    subs: SpringFolderResponse[];
  }>[] = [];

  for (const root of roots) {
    if (root.parentId !== null) continue;
    if (!root.paraCategory) continue;
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

export function lowerPara(
  p: ParaCategory | null,
): NormalizedFolder["para_category"] {
  if (!p) return null;
  return p.toLowerCase() as NormalizedFolder["para_category"];
}

export function upperPara(
  p:
    | NormalizedFolder["para_category"]
    | "project"
    | "area"
    | "resource"
    | "archive",
): ParaCategory | null {
  if (!p) return null;
  return p.toUpperCase() as ParaCategory;
}
