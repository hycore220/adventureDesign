"use client";

import { useEffect, useState } from "react";
import {
  base64UrlToUint8Array,
  getReminderPrefs,
  getVapidPublicKey,
  loadTokens,
  sendTestPush,
  sendTestWeeklyPush,
  subscribePushOnServer,
  toSubscribePayload,
  unsubscribePushOnServer,
  updateReminderPrefs,
  type ReminderPrefs,
} from "@/lib/api";
import { getPrefsCache, setPrefsCache } from "@/lib/prefs-cache";

const HOURS = Array.from({ length: 24 }, (_, i) => i);
const DOW = [
  { v: 1, label: "월" },
  { v: 2, label: "화" },
  { v: 3, label: "수" },
  { v: 4, label: "목" },
  { v: 5, label: "금" },
  { v: 6, label: "토" },
  { v: 7, label: "일" },
];

type PushState = "loading" | "off" | "on" | "denied" | "unsupported";

function hourOf(t: string): number {
  const h = parseInt(t.split(":")[0] ?? "9", 10);
  return Number.isFinite(h) ? h : 9;
}
function toHHmm(hour: number): string {
  return `${String(hour).padStart(2, "0")}:00`;
}
function isPushSupported(): boolean {
  if (typeof window === "undefined") return false;
  return (
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    "Notification" in window
  );
}
async function getExistingSub(): Promise<PushSubscription | null> {
  if (!isPushSupported()) return null;
  const reg = await navigator.serviceWorker.getRegistration("/");
  if (!reg) return null;
  return await reg.pushManager.getSubscription();
}

/**
 * 이 기기를 push 에 구독 (권한 → SW → subscribe → 서버 저장).
 * 권한 거부/미지원이면 그 상태 반환. 호출자는 prefs 저장과 별개로 best-effort 처리.
 */
async function ensureSubscribed(): Promise<PushState> {
  if (!isPushSupported()) return "unsupported";
  if (Notification.permission === "denied") return "denied";
  const existing = await getExistingSub();
  if (existing) return "on";

  const perm = await Notification.requestPermission();
  if (perm !== "granted") return perm === "denied" ? "denied" : "off";

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
  return "on";
}

export function ReminderSettings() {
  // 첫 렌더부터 캐시된 직전 설정으로 시드 → 탭 재방문 시 "불러오는 중…" 없이 즉시 표시.
  const seed = getPrefsCache(loadTokens()?.userName);
  const [prefs, setPrefs] = useState<ReminderPrefs | null>(seed);
  const [pushState, setPushState] = useState<PushState>("loading");
  const [loading, setLoading] = useState(seed == null);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      // 백그라운드 갱신 (캐시가 있으면 위에서 이미 표시 중)
      try {
        const p = await getReminderPrefs();
        setPrefs(p);
        const u = loadTokens()?.userName;
        if (u) setPrefsCache(u, p);
      } catch {
        /* ignore */
      }
      // push 구독 상태
      if (!isPushSupported()) setPushState("unsupported");
      else if (Notification.permission === "denied") setPushState("denied");
      else {
        const ex = await getExistingSub();
        setPushState(ex ? "on" : "off");
      }
      setLoading(false);
    })();
  }, []);

  // 권한 변화 실시간 감지 — 사용자가 자물쇠에서 "허용"으로 바꾸면 자동 구독.
  // (denied 상태에서 우리 버튼으로 prompt 재호출은 브라우저가 막음 → 이 listener 로 우회)
  useEffect(() => {
    if (typeof navigator === "undefined" || !navigator.permissions) return;
    let status: PermissionStatus | null = null;
    let cancelled = false;
    navigator.permissions
      .query({ name: "notifications" as PermissionName })
      .then((s) => {
        if (cancelled) return;
        status = s;
        s.onchange = async () => {
          if (s.state === "granted") {
            setPushState("loading");
            try {
              setPushState(await ensureSubscribed());
              setMsg("알림 켜졌어요");
              setTimeout(() => setMsg(null), 2000);
            } catch {
              setPushState("off");
            }
          } else if (s.state === "denied") {
            setPushState("denied");
          } else {
            setPushState("off");
          }
        };
      })
      .catch(() => {});
    return () => {
      cancelled = true;
      if (status) status.onchange = null;
    };
  }, []);

  /** denied 상태에서 "다시 확인" — 사용자가 자물쇠에서 바꾼 뒤 즉시 반영. */
  async function recheckPermission() {
    if (typeof window === "undefined" || !("Notification" in window)) return;
    if (Notification.permission === "granted") {
      setPushState("loading");
      try {
        setPushState(await ensureSubscribed());
      } catch {
        setPushState("off");
      }
    } else if (Notification.permission === "denied") {
      setPushState("denied");
      setMsg("아직 차단 상태예요");
      setTimeout(() => setMsg(null), 2000);
    } else {
      // default 로 돌아왔으면 (드묾) 다시 권한 요청 가능
      setPushState("loading");
      try {
        setPushState(await ensureSubscribed());
      } catch {
        setPushState("off");
      }
    }
  }

  async function patch(p: Partial<ReminderPrefs>) {
    if (!prefs) return;
    setSaving(true);
    setMsg(null);
    setPrefs({ ...prefs, ...p });
    try {
      const updated = await updateReminderPrefs(p as never);
      setPrefs(updated);
      const u = loadTokens()?.userName;
      if (u) setPrefsCache(u, updated); // 캐시도 최신화 → 재방문 시 옛 값 안 보이게
      setMsg("저장됨");
      setTimeout(() => setMsg(null), 1500);
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "저장 실패");
    } finally {
      setSaving(false);
    }
  }

  /** 알림 토글 ON → prefs 저장 + (필요시) push 구독을 best-effort 로 같이 처리. */
  async function enableChannel(field: "dailyEnabled" | "weeklyEnabled") {
    await patch({ [field]: true } as Partial<ReminderPrefs>);
    // 아직 이 기기가 구독 안 됐으면 자동 구독 시도
    if (pushState === "off") {
      setPushState("loading");
      try {
        const r = await ensureSubscribed();
        setPushState(r);
      } catch {
        setPushState("off");
      }
    }
  }

  async function disableChannel(field: "dailyEnabled" | "weeklyEnabled") {
    await patch({ [field]: false } as Partial<ReminderPrefs>);
    // push 구독은 유지 (다른 채널이 쓸 수 있음). 둘 다 끄면 그대로 둠 — 재구독 비용 절약.
  }

  async function reSubscribe() {
    setPushState("loading");
    try {
      setPushState(await ensureSubscribed());
    } catch {
      setPushState("off");
    }
  }

  async function unsub() {
    const ex = await getExistingSub();
    if (ex) {
      await unsubscribePushOnServer(ex.endpoint).catch(() => {});
      await ex.unsubscribe();
    }
    setPushState("off");
  }

  async function runTest(kind: "daily" | "weekly") {
    if (testing) return;
    setTesting(true);
    setMsg(null);
    try {
      const res = kind === "weekly" ? await sendTestWeeklyPush() : await sendTestPush();
      const label = kind === "weekly" ? "주간" : "매일";
      if (res.sent > 0) setMsg(`${label} 발사 완료 — ${res.sent}대`);
      else if (res.removed > 0) setMsg("만료된 구독 정리됨 — 다시 켜주세요");
      else setMsg(`발사 실패 (sent=${res.sent} failed=${res.failed})`);
    } catch (e) {
      setMsg(e instanceof Error ? e.message : "에러");
    } finally {
      setTimeout(() => setTesting(false), 3000);
    }
  }

  function detectTz() {
    try {
      return Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch {
      return "Asia/Seoul";
    }
  }

  if (loading) {
    return (
      <section className="space-y-2">
        <h2 className="px-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          알림
        </h2>
        <div className="rounded-xl border bg-card p-4 text-xs text-muted-foreground">
          불러오는 중…
        </div>
      </section>
    );
  }
  if (!prefs) return null;

  const browserTz = detectTz();
  const tzMismatch = browserTz && browserTz !== prefs.timezone;
  const anyEnabled = prefs.dailyEnabled || prefs.weeklyEnabled;

  return (
    <section className="space-y-2">
      <div className="flex items-center justify-between px-1">
        <h2 className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          알림
        </h2>
        {msg && <span className="text-[11px] text-muted-foreground">{msg}</span>}
      </div>

      <div className="space-y-4 rounded-xl border bg-card p-4">
        {/* 매일 알림 */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm font-medium">매일 알림</div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                매일 정한 시각에 다시 볼 링크를 받아요
              </div>
            </div>
            <button
              type="button"
              disabled={saving}
              onClick={() =>
                prefs.dailyEnabled
                  ? disableChannel("dailyEnabled")
                  : enableChannel("dailyEnabled")
              }
              className="shrink-0 rounded-md border px-3 py-1 text-xs font-medium disabled:opacity-40"
            >
              {prefs.dailyEnabled ? "ON" : "OFF"}
            </button>
          </div>
          {prefs.dailyEnabled && (
            <div className="flex items-center gap-2 pl-1">
              <span className="text-xs text-muted-foreground">시각</span>
              <select
                disabled={saving}
                value={hourOf(prefs.dailyTime)}
                onChange={(e) => patch({ dailyTime: toHHmm(Number(e.target.value)) })}
                className="rounded-md border bg-background px-2 py-1 text-xs"
              >
                {HOURS.map((h) => (
                  <option key={h} value={h}>
                    {String(h).padStart(2, "0")}:00
                  </option>
                ))}
              </select>
              <span className="text-xs text-muted-foreground">({prefs.timezone})</span>
            </div>
          )}
        </div>

        <div className="h-px bg-border" />

        {/* 주간 요약 */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm font-medium">주간 요약</div>
              <div className="mt-0.5 text-xs text-muted-foreground">
                주 1회 이번 주 돌아볼 링크를 정리해서 받아요
              </div>
            </div>
            <button
              type="button"
              disabled={saving}
              onClick={() =>
                prefs.weeklyEnabled
                  ? disableChannel("weeklyEnabled")
                  : enableChannel("weeklyEnabled")
              }
              className="shrink-0 rounded-md border px-3 py-1 text-xs font-medium disabled:opacity-40"
            >
              {prefs.weeklyEnabled ? "ON" : "OFF"}
            </button>
          </div>
          {prefs.weeklyEnabled && (
            <div className="space-y-2 pl-1">
              <div className="flex flex-wrap gap-1">
                {DOW.map((d) => (
                  <button
                    key={d.v}
                    type="button"
                    disabled={saving}
                    onClick={() => patch({ weeklyDow: d.v })}
                    className={`h-7 w-7 rounded-full border text-xs transition-colors ${
                      prefs.weeklyDow === d.v
                        ? "border-primary bg-primary text-primary-foreground"
                        : "border-border bg-background"
                    }`}
                  >
                    {d.label}
                  </button>
                ))}
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground">시각</span>
                <select
                  disabled={saving}
                  value={hourOf(prefs.weeklyTime)}
                  onChange={(e) => patch({ weeklyTime: toHHmm(Number(e.target.value)) })}
                  className="rounded-md border bg-background px-2 py-1 text-xs"
                >
                  {HOURS.map((h) => (
                    <option key={h} value={h}>
                      {String(h).padStart(2, "0")}:00
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}
        </div>

        <div className="h-px bg-border" />

        {/* 한 번에 받을 링크 수 */}
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium">한 번에 받을 링크 수</span>
          <select
            disabled={saving}
            value={prefs.maxItemsPerReminder}
            onChange={(e) => patch({ maxItemsPerReminder: Number(e.target.value) })}
            className="rounded-md border bg-background px-2 py-1 text-xs"
          >
            {[3, 5, 10, 15, 20].map((n) => (
              <option key={n} value={n}>
                {n}개
              </option>
            ))}
          </select>
        </div>

        {/* 시간대 자동 감지 제안 */}
        {tzMismatch && (
          <div className="rounded-lg bg-accent/40 p-2 text-[11px] text-muted-foreground">
            기기 시간대 <b>{browserTz}</b> ≠ 설정 <b>{prefs.timezone}</b>{" "}
            <button
              type="button"
              className="underline"
              onClick={() => patch({ timezone: browserTz })}
            >
              {browserTz}로 변경
            </button>
          </div>
        )}

        <div className="h-px bg-border" />

        {/* 푸시 채널 상태 (부가) */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <div className="min-w-0">
              <div className="text-xs font-medium">이 기기 푸시</div>
              <div className="mt-0.5 text-[11px] text-muted-foreground">
                {pushState === "on" && "✅ 이 기기로 알림을 받습니다"}
                {pushState === "off" &&
                  "알림 켜면 자동 구독돼요. 안 되면 아래 버튼으로 재시도"}
                {pushState === "denied" &&
                  "브라우저에서 알림이 차단됨 — 주소창 자물쇠 → 알림 허용"}
                {pushState === "unsupported" && "이 브라우저는 푸시 미지원"}
                {pushState === "loading" && "처리 중…"}
              </div>
            </div>
            {pushState === "on" ? (
              <button
                type="button"
                onClick={unsub}
                className="shrink-0 rounded-md border px-2.5 py-1 text-[11px] text-muted-foreground"
              >
                이 기기 해제
              </button>
            ) : pushState === "off" ? (
              <button
                type="button"
                onClick={reSubscribe}
                className="shrink-0 rounded-md border px-2.5 py-1 text-[11px] font-medium"
              >
                이 기기 켜기
              </button>
            ) : pushState === "denied" ? (
              <button
                type="button"
                onClick={recheckPermission}
                className="shrink-0 rounded-md border px-2.5 py-1 text-[11px] font-medium"
              >
                다시 확인
              </button>
            ) : null}
          </div>

          {/* denied 상태 — 자물쇠에서 푸는 단계 안내 */}
          {pushState === "denied" && (
            <div className="rounded-lg bg-accent/40 p-2 text-[11px] leading-relaxed text-muted-foreground">
              브라우저가 알림을 차단해서 버튼으로 다시 켤 수 없어요 (브라우저 정책).
              <br />
              1. 주소창 왼쪽 <b>자물쇠 🔒</b> 클릭
              <br />
              2. <b>알림 → 허용</b> 으로 변경
              <br />
              3. 바꾸면 자동으로 켜지거나, <b>다시 확인</b> 버튼을 눌러주세요
            </div>
          )}

          {/* iOS 안내 */}
          <p className="text-[11px] text-muted-foreground">
            iOS는 홈 화면에 추가 후에만 알림을 받을 수 있어요.
          </p>

          {/* 테스트 버튼 — 구독돼 있고 채널 켜져 있을 때만 */}
          {pushState === "on" && anyEnabled && (
            <div className="flex flex-wrap items-center gap-2 pt-1">
              <button
                type="button"
                onClick={() => runTest("daily")}
                disabled={testing || !prefs.dailyEnabled}
                className="rounded-md border px-3 py-1.5 text-xs font-medium disabled:opacity-40"
              >
                매일 테스트
              </button>
              <button
                type="button"
                onClick={() => runTest("weekly")}
                disabled={testing || !prefs.weeklyEnabled}
                className="rounded-md border px-3 py-1.5 text-xs font-medium disabled:opacity-40"
              >
                주간 테스트
              </button>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
