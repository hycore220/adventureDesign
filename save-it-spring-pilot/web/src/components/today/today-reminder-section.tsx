"use client";

import { useEffect, useState } from "react";
import { useAuth } from "@/lib/useAuth";
import {
  getTodayRecommendations,
  type RemindCandidate,
} from "@/lib/api";
import { RemindCard } from "./remind-card";

export function TodayReminderSection() {
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : null;

  const [items, setItems] = useState<RemindCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!userName) return;
    setLoading(true);
    setError(null);
    getTodayRecommendations(userName, 10)
      .then(setItems)
      .catch((e) => setError(e instanceof Error ? e.message : "fetch 실패"))
      .finally(() => setLoading(false));
  }, [userName]);

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
