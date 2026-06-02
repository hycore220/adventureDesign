"use client";

import { use, useEffect, useState } from "react";
import { AppHeader } from "@/components/shell/app-header";
import { SearchForm } from "@/components/library/search-form";
import { LinkCard } from "@/components/library/link-card";
import { ParaBadge } from "@/components/primitives/para-badge";
import { useAuth } from "@/lib/useAuth";
import { getUserLinks } from "@/lib/api";
import { type Link, toLink } from "@/lib/types";

export default function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q } = use(searchParams);
  const query = (q ?? "").trim();
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : null;

  const [allLinks, setAllLinks] = useState<Link[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!userName) return;
    setLoading(true);
    getUserLinks(userName)
      .then((springs) => setAllLinks(springs.map((s) => toLink(s, null))))
      .finally(() => setLoading(false));
  }, [userName]);

  // 클라이언트 측 keyword 매칭 — title/url 포함 검색
  const lower = query.toLowerCase();
  const results = query
    ? allLinks
        .filter(
          (l) =>
            l.title.toLowerCase().includes(lower) ||
            l.url.toLowerCase().includes(lower),
        )
        .slice(0, 50)
    : [];

  return (
    <>
      <AppHeader title="검색" />
      <div className="p-4 space-y-3">
        <SearchForm />
        {loading ? (
          <p className="py-8 text-center text-sm italic text-muted-foreground">
            불러오는 중…
          </p>
        ) : !query ? (
          <p className="py-8 text-center text-sm italic text-muted-foreground">
            검색어를 입력하세요
          </p>
        ) : results.length === 0 ? (
          <p className="py-8 text-center text-sm italic text-muted-foreground">
            일치하는 링크가 없어요
          </p>
        ) : (
          <ul className="space-y-2">
            {results.map((l) => (
              <li key={l.id} className="flex items-center gap-2">
                <ParaBadge category={l.para_status ?? null} />
                <div className="flex-1">
                  <LinkCard link={l} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}
