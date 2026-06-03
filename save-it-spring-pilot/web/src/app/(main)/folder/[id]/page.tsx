"use client";

import { use, useEffect, useState } from "react";
import { notFound } from "next/navigation";
import { AppHeader } from "@/components/shell/app-header";
import { BackButton } from "@/components/shell/back-button";
import { ParaBadge } from "@/components/primitives/para-badge";
import { LinkCard } from "@/components/library/link-card";
import { AddLinkFab } from "@/components/actions/add-link-fab";
import { useAuth } from "@/lib/useAuth";
import { getFlatFolders, getLinksByFolder, loadTokens } from "@/lib/api";
import { getPageCache, setPageCache } from "@/lib/page-cache";
import { type Folder, type Link, toLink } from "@/lib/types";

interface FolderSnapshot {
  folder: Folder;
  links: Link[];
}

export default function FolderPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = use(params);
  const folderId = Number(id);
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : null;
  const userId =
    auth.status === "authenticated" ? auth.session.user.id : "";

  // 첫 렌더부터 캐시된 직전 폴더 내용으로 시드 → 재진입 시 "불러오는 중" 없음.
  const cacheKey = `folder:${loadTokens()?.userName ?? ""}:${folderId}`;
  const seed = getPageCache<FolderSnapshot>(cacheKey);
  const [folder, setFolder] = useState<Folder | null>(seed?.folder ?? null);
  const [links, setLinks] = useState<Link[]>(seed?.links ?? []);
  const [notExists, setNotExists] = useState(false);

  useEffect(() => {
    if (!userName || !Number.isFinite(folderId)) return;
    // 백그라운드 갱신 (캐시 시드돼 있으면 이미 표시 중)
    Promise.all([
      getFlatFolders(userName),
      getLinksByFolder(folderId).catch(() => []),
    ])
      .then(([{ folders }, springLinks]) => {
        const found = folders.find((f) => f.id === folderId);
        if (!found) {
          setNotExists(true);
          return;
        }
        const nextFolder: Folder = {
          id: found.id,
          name: found.name,
          para_category: found.para_category,
        };
        const nextLinks = springLinks.map((l) => toLink(l, folderId));
        setFolder(nextFolder);
        setLinks(nextLinks);
        setPageCache<FolderSnapshot>(cacheKey, { folder: nextFolder, links: nextLinks });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userName, folderId]);

  if (notExists) notFound();

  // 폴더 데이터가 아직 없을 때만 로딩 (캐시 있으면 즉시 렌더)
  if (!folder) {
    return (
      <div className="flex h-full items-center justify-center p-8 text-xs text-muted-foreground">
        불러오는 중…
      </div>
    );
  }

  const backHref = `/category/${folder.para_category ?? "unassigned"}`;

  return (
    <>
      <AppHeader
        title={folder.name}
        left={<BackButton fallbackHref={backHref} />}
        right={<ParaBadge category={folder.para_category} />}
      />
      <div className="space-y-2 p-4">
        {links.length === 0 ? (
          <p className="py-8 text-center text-sm italic text-muted-foreground">
            이 폴더는 비어 있어요
          </p>
        ) : (
          links.map((l) => <LinkCard key={l.id} link={l} />)
        )}
      </div>
      {userId && <AddLinkFab folderId={folder.id} userId={userId} />}
    </>
  );
}
