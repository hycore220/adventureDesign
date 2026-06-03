"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/useAuth";
import {
  getTodayRecommendations,
  loadTokens,
  type RemindCandidate,
} from "@/lib/api";
import { getTodayCache, setTodayCache } from "@/lib/today-cache";
import { RemindCard } from "./remind-card";

export function TodayReminderSection() {
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : null;

  // 첫 렌더부터 캐시된 직전 추천으로 시드 → 탭 재방문 시 스켈레톤 없이 즉시 표시.
  const seed = getTodayCache(loadTokens()?.userName);
  const [items, setItems] = useState<RemindCandidate[]>(seed ?? []);
  // 보여줄 데이터가 한 번이라도 확보됐는지 (캐시 히트 or fetch 완료)
  const [loaded, setLoaded] = useState<boolean>(seed != null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userName) return;
    setError(null);
    // 재방문이라 캐시가 있으면 즉시 표시
    const cached = getTodayCache(userName);
    if (cached) {
      setItems(cached);
      setLoaded(true);
    }
    // 백그라운드 갱신 (stale-while-revalidate)
    getTodayRecommendations(userName, 10)
      .then((list) => {
        setItems(list);
        setTodayCache(userName, list);
        setLoaded(true);
      })
      .catch((e) => {
        setError(e instanceof Error ? e.message : "fetch 실패");
        setLoaded(true); // 콜드 에러 시 스켈레톤 멈추고 에러/빈상태 노출
      });
  }, [userName]);

  // 캐시/데이터가 아직 없을 때만 스켈레톤
  const loading = !loaded;

  return (
    <section className="space-y-2">
      {loading && (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <div
              key={i}
              className="h-16 rounded-2xl border border-border bg-card animate-pulse"
            />
          ))}
        </div>
      )}

      {error && !loading && (
        <div className="rounded-2xl border border-border bg-card p-3 text-sm text-muted-foreground">
          리마인드 목록을 불러오지 못했어요.
        </div>
      )}

      {!loading && !error && items.length === 0 && (
        <div className="rounded-2xl border border-border bg-card p-3 text-sm text-muted-foreground">
          오늘 다시 볼 링크가 없어요.
        </div>
      )}

      {!loading && !error && items.length > 0 && (
        <ul className="space-y-2">
          {items.map((c) => (
            <li key={c.linkId}>
              <RemindCard candidate={c} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
