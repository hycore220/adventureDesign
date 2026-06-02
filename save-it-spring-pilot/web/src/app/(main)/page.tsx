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
  type NormalizedFolder,
  type SpringLinkResponse,
} from "@/lib/api";

export default function LibraryHome() {
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : null;
  const userId =
    auth.status === "authenticated" ? auth.session.user.id : undefined;

  const [folders, setFolders] = useState<NormalizedFolder[]>([]);
  const [links, setLinks] = useState<SpringLinkResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!userName) return;
    setLoading(true);
    Promise.all([getFlatFolders(userName), getUserLinks(userName)])
      .then(([{ folders }, allLinks]) => {
        setFolders(folders);
        setLinks(allLinks);
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
              folderCount={folderCountByCategory.get(category) ?? 0}
              linkCount={linkCountByCategory.get(category) ?? 0}
            />
          ))}
        </div>
        <UnassignedCard
          linkCount={linkCountByCategory.get("unassigned") ?? 0}
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
