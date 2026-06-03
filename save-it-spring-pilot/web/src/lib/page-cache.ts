/**
 * 범용 페이지 데이터 캐시 (stale-while-revalidate).
 *
 * 폴더/카테고리 페이지처럼 진입마다 remount → 재fetch 로 "불러오는 중…" 이
 * 깜빡이던 것을 막는다. key 로 임의 스냅샷을 보관하고, 재방문 시 즉시 시드한 뒤
 * 백그라운드로 갱신. 인메모리(탭 새로고침 시 사라짐).
 */
const cache = new Map<string, unknown>();

export function getPageCache<T>(key: string | null | undefined): T | null {
  if (!key) return null;
  return (cache.get(key) as T | undefined) ?? null;
}

export function setPageCache<T>(key: string, value: T): void {
  cache.set(key, value);
}
