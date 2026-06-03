import type { NormalizedFolder, SpringLinkResponse } from "./api";

/**
 * 라이브러리 홈 데이터의 모듈 레벨 캐시 (stale-while-revalidate).
 *
 * 탭을 옮길 때마다 홈이 remount → 상태가 빈 배열로 초기화되며
 * "0개 폴더·0개 링크" 가 잠깐 노출되던 문제를 막는다.
 * 재방문 시 캐시된 직전 값을 즉시 보여주고, 백그라운드로 새로 fetch 해 갱신.
 *
 * 인메모리(탭 새로고침 시 사라짐)면 충분 — 깜빡임만 없애는 게 목적.
 */
export interface HomeData {
  folders: NormalizedFolder[];
  links: SpringLinkResponse[];
}

const cache = new Map<string, HomeData>();

export function getHomeCache(userName: string | null | undefined): HomeData | null {
  if (!userName) return null;
  return cache.get(userName) ?? null;
}

export function setHomeCache(userName: string, data: HomeData): void {
  cache.set(userName, data);
}
