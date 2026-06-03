/**
 * 뷰 데이터 캐시 — browser.storage.local 백킹.
 *
 * 익스텐션 위젯은 페이지 새로고침/재오픈마다 remount → 매번 재fetch 하며
 * "벨커브 계산 중…" / "불러오는 중…" 이 깜빡이던 문제를 막는다.
 * 직전 결과를 storage 에 저장해 두고, 다음 마운트 때 즉시 시드(stale)한 뒤
 * 백그라운드로 새로 fetch(revalidate)한다.
 *
 * storage.local 이라 페이지 새로고침은 물론 탭/세션을 넘어 유지된다.
 */
export async function readCache<T>(key: string): Promise<T | undefined> {
  try {
    const r = await browser.storage.local.get(key);
    return r[key] as T | undefined;
  } catch {
    return undefined;
  }
}

export function writeCache<T>(key: string, value: T): void {
  browser.storage.local.set({ [key]: value }).catch(() => {});
}
