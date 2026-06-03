"use client";

import { use, useCallback, useEffect, useState } from "react";
import { notFound } from "next/navigation";
import { AppHeader } from "@/components/shell/app-header";
import { BackButton } from "@/components/shell/back-button";
import { FolderAccordionItem } from "@/components/library/folder-accordion-item";
import { LinkCard } from "@/components/library/link-card";
import { AddFolderModal } from "@/components/actions/add-folder-modal";
import { QuickAddFab } from "@/components/actions/quick-add-fab";
import {
  PARA_TOKENS,
  UNASSIGNED_TOKEN,
  isValidParaParam,
} from "@/lib/para";
import { useAuth } from "@/lib/useAuth";
import {
  deleteFolder,
  deleteLink,
  getFlatFolders,
  getLinksByFolder,
  loadTokens,
  upperPara,
  type NormalizedFolder,
} from "@/lib/api";
import { getPageCache, setPageCache } from "@/lib/page-cache";
import { type Folder, type Link, toLink } from "@/lib/types";

type ParaRootMap = Partial<Record<"PROJECT" | "AREA" | "RESOURCE" | "ARCHIVE", number>>;
interface CategorySnapshot {
  folders: Folder[];
  linksByFolder: Record<number, Link[]>;
  paraRoots: ParaRootMap;
}

export default function CategoryPage({
  params,
}: {
  params: Promise<{ para: string }>;
}) {
  const { para } = use(params);
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : null;
  const userId =
    auth.status === "authenticated" ? auth.session.user.id : "";

  // 첫 렌더부터 캐시된 직전 내용으로 시드 → 재진입 시 "불러오는 중" 없음.
  const cacheKey = `category:${loadTokens()?.userName ?? ""}:${para}`;
  const seed = getPageCache<CategorySnapshot>(cacheKey);
  const [folders, setFolders] = useState<Folder[]>(seed?.folders ?? []);
  const [linksByFolder, setLinksByFolder] = useState<
    Record<number, Link[]>
  >(seed?.linksByFolder ?? {});
  const [paraRoots, setParaRoots] = useState<ParaRootMap>(seed?.paraRoots ?? {});
  const [loaded, setLoaded] = useState<boolean>(seed != null);

  if (!isValidParaParam(para)) notFound();

  const isUnassigned = para === "unassigned";
  const title = isUnassigned
    ? UNASSIGNED_TOKEN.label
    : PARA_TOKENS[para as Exclude<typeof para, "unassigned">].label;

  // 폴더+링크 재조회 → 캐시·상태 갱신. 생성/삭제 후에도 호출해 즉시 반영.
  const reload = useCallback(async () => {
    if (!userName || isUnassigned) return;
    const { folders: all, paraRoots } = await getFlatFolders(userName);
    setParaRoots(paraRoots);
    const adapted: Folder[] = all
      .filter((f: NormalizedFolder) => f.para_category === para)
      .map((f) => ({ id: f.id, name: f.name, para_category: f.para_category }));
    setFolders(adapted);
    const results = await Promise.all(
      adapted.map((f) =>
        getLinksByFolder(f.id)
          .then((links) => ({ id: f.id, links }))
          .catch(() => ({ id: f.id, links: [] })),
      ),
    );
    const map: Record<number, Link[]> = {};
    for (const r of results) map[r.id] = r.links.map((l) => toLink(l, r.id));
    setLinksByFolder(map);
    setPageCache<CategorySnapshot>(cacheKey, {
      folders: adapted,
      linksByFolder: map,
      paraRoots,
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userName, para, isUnassigned]);

  useEffect(() => {
    if (!userName) return;
    if (isUnassigned) {
      setLoaded(true);
      return;
    }
    reload().finally(() => setLoaded(true));
  }, [userName, isUnassigned, reload]);

  async function handleDeleteFolder(folderId: number) {
    if (
      !window.confirm("이 폴더를 삭제할까요?\n폴더 안의 링크도 함께 삭제됩니다.")
    )
      return;
    try {
      await deleteFolder(folderId);
      await reload();
    } catch (e) {
      window.alert(e instanceof Error ? e.message : "폴더 삭제 실패");
    }
  }

  async function handleDeleteLink(linkId: number) {
    if (!window.confirm("이 링크를 삭제할까요?")) return;
    try {
      await deleteLink(linkId);
      await reload();
    } catch (e) {
      window.alert(e instanceof Error ? e.message : "링크 삭제 실패");
    }
  }

  if (isUnassigned) {
    return (
      <>
        <AppHeader title={title} left={<BackButton fallbackHref="/" />} />
        <div className="space-y-2 p-4">
          <p className="py-8 text-center text-sm italic text-muted-foreground">
            미지정 폴더는 이 백엔드에서 지원하지 않습니다
          </p>
        </div>
        {userId && <QuickAddFab userId={userId} />}
      </>
    );
  }

  const parentId = paraRoots[upperPara(para as ParaCategoryLower) as Exclude<keyof typeof paraRoots, never>];

  return (
    <>
      <AppHeader title={title} left={<BackButton fallbackHref="/" />} />
      <div className="space-y-2 p-4">
        {!loaded && folders.length === 0 ? (
          <p className="py-8 text-center text-sm italic text-muted-foreground">
            불러오는 중…
          </p>
        ) : folders.length === 0 ? (
          <p className="py-8 text-center text-sm italic text-muted-foreground">
            아직 폴더가 없어요
          </p>
        ) : (
          folders.map((f) => (
            <FolderAccordionItem
              key={f.id}
              id={f.id}
              name={f.name}
              links={linksByFolder[f.id] ?? []}
              onDeleteFolder={handleDeleteFolder}
              onDeleteLink={handleDeleteLink}
            />
          ))
        )}
        {parentId && userId && (
          <AddFolderModal
            category={para as ParaCategoryLower}
            userId={userId}
            parentId={parentId}
            onCreated={reload}
          />
        )}
      </div>
    </>
  );
}

type ParaCategoryLower = "project" | "area" | "resource" | "archive";
