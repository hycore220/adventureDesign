import type { RemindCandidate } from "./api";

/**
 * "오늘 추천" 데이터의 모듈 레벨 캐시 (stale-while-revalidate).
 *
 * 탭을 옮길 때마다 Today 섹션이 remount → 매번 재fetch 하며 ~0.1초 스켈레톤이
 * 뜨던 것을 막는다. 재방문 시 직전 추천을 즉시 표시하고 백그라운드로 갱신.
 * 인메모리(탭 새로고침 시 사라짐)면 충분 — 탭 전환 깜빡임만 없애는 게 목적.
 */
const cache = new Map<string, RemindCandidate[]>();

export function getTodayCache(userName: string | null | undefined): RemindCandidate[] | null {
  if (!userName) return null;
  return cache.get(userName) ?? null;
}

export function setTodayCache(userName: string, items: RemindCandidate[]): void {
  cache.set(userName, items);
}
