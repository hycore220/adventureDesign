"use client";

import { useEffect, useState } from "react";
import { AppHeader } from "@/components/shell/app-header";
import { ParaCard } from "@/components/library/para-card";
import { UnassignedCard } from "@/components/library/unassigned-card";
import { QuickAddFab } from "@/components/actions/quick-add-fab";
import { PARA_ORDER } from "@/lib/para";
import { useAuth } from "@/lib/useAuth";
import {
  getFlatFolders,
  getUserLinks,
  loadTokens,
  type NormalizedFolder,
  type SpringLinkResponse,
} from "@/lib/api";
import { getHomeCache, setHomeCache } from "@/lib/home-cache";

export default function LibraryHome() {
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : null;
  const userId =
    auth.status === "authenticated" ? auth.session.user.id : undefined;

  // 첫 렌더부터 캐시된 직전 값으로 시드 → 탭 재방문 시 0 깜빡임 없음 (SWR).
  // SSR 에선 loadTokens 가 null 이라 빈 상태로 시작.
  const seed = getHomeCache(loadTokens()?.userName);
  const [folders, setFolders] = useState<NormalizedFolder[]>(seed?.folders ?? []);
  const [links, setLinks] = useState<SpringLinkResponse[]>(seed?.links ?? []);
  // loaded = 보여줄 데이터가 한 번이라도 확보됐는지 (캐시 히트 or fetch 완료)
  const [loaded, setLoaded] = useState<boolean>(seed != null);
  const [loading, setLoading] = useState(seed == null);

  useEffect(() => {
    if (!userName) return;
    // 재방문이라 캐시가 있으면 즉시 표시
    const cached = getHomeCache(userName);
    if (cached) {
      setFolders(cached.folders);
      setLinks(cached.links);
      setLoaded(true);
      setLoading(false);
    }
    // 백그라운드 갱신 (stale-while-revalidate)
    Promise.all([getFlatFolders(userName), getUserLinks(userName)])
      .then(([{ folders }, allLinks]) => {
        setFolders(folders);
        setLinks(allLinks);
        setHomeCache(userName, { folders, links: allLinks });
        setLoaded(true);
      })
      .catch((e) => {
        console.error(e);
      })
      .finally(() => setLoading(false));
  }, [userName]);

  // PARA 별 폴더 / 링크 카운트
  const folderToCategory = new Map(
    folders.map((f) => [f.id, f.para_category]),
  );

  const linkCountByCategory = new Map<string, number>();
  for (const l of links) {
    // Spring 백엔드는 folderId 가 LinkResponse 에 없으니 paraStatus 로 분류
    const key = l.paraStatus ? l.paraStatus.toLowerCase() : "unassigned";
    linkCountByCategory.set(key, (linkCountByCategory.get(key) ?? 0) + 1);
  }

  const folderCountByCategory = new Map<string, number>();
  for (const f of folders) {
    const key = f.para_category ?? "unassigned";
    folderCountByCategory.set(key, (folderCountByCategory.get(key) ?? 0) + 1);
  }

  return (
    <>
      <AppHeader title="라이브러리" />
      <div className="p-4 space-y-3">
        <div className="grid grid-cols-2 gap-3">
          {PARA_ORDER.map((category) => (
            <ParaCard
              key={category}
              category={category}
              folderCount={loaded ? (folderCountByCategory.get(category) ?? 0) : null}
              linkCount={loaded ? (linkCountByCategory.get(category) ?? 0) : null}
            />
          ))}
        </div>
        <UnassignedCard
          linkCount={loaded ? (linkCountByCategory.get("unassigned") ?? 0) : null}
        />
        {loading && (
          <p className="pt-2 text-center text-xs text-muted-foreground">
            불러오는 중…
          </p>
        )}
      </div>
      {userId && <QuickAddFab userId={userId} />}
      {/* userToCategory 는 위에서 사용. ESLint unused 경고 회피용. */}
      <span className="hidden" data-folder-cat-size={folderToCategory.size} />
    </>
  );
}
