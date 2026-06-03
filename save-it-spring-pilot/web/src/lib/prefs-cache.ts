import type { ReminderPrefs } from "./api";

/**
 * 알림 설정(reminder-prefs)의 모듈 레벨 캐시 (stale-while-revalidate).
 *
 * 설정 탭 재방문마다 remount → getReminderPrefs 재fetch 로 "불러오는 중…" 이
 * 깜빡이던 것을 막는다. 재방문 시 직전 설정을 즉시 표시하고 백그라운드로 갱신.
 */
const cache = new Map<string, ReminderPrefs>();

export function getPrefsCache(userName: string | null | undefined): ReminderPrefs | null {
  if (!userName) return null;
  return cache.get(userName) ?? null;
}

export function setPrefsCache(userName: string, prefs: ReminderPrefs): void {
  cache.set(userName, prefs);
}
