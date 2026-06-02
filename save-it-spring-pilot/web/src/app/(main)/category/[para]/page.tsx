"use client";

import { use, useEffect, useState } from "react";
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
  getFlatFolders,
  getLinksByFolder,
  upperPara,
  type NormalizedFolder,
} from "@/lib/api";
import { type Folder, type Link, toLink } from "@/lib/types";

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

  const [folders, setFolders] = useState<Folder[]>([]);
  const [linksByFolder, setLinksByFolder] = useState<
    Record<number, Link[]>
  >({});
  const [paraRoots, setParaRoots] = useState<
    Partial<Record<"PROJECT" | "AREA" | "RESOURCE" | "ARCHIVE", number>>
  >({});
  const [loading, setLoading] = useState(true);

  if (!isValidParaParam(para)) notFound();

  const isUnassigned = para === "unassigned";
  const title = isUnassigned
    ? UNASSIGNED_TOKEN.label
    : PARA_TOKENS[para as Exclude<typeof para, "unassigned">].label;

  useEffect(() => {
    if (!userName) return;
    if (isUnassigned) {
      // 우리 Spring 은 미지정 폴더 개념 없음 — 빈 화면 표시
      setLoading(false);
      return;
    }
    setLoading(true);
    getFlatFolders(userName)
      .then(async ({ folders: all, paraRoots }) => {
        setParaRoots(paraRoots);
        const filtered = all.filter(
          (f: NormalizedFolder) => f.para_category === para,
        );
        const adapted: Folder[] = filtered.map((f) => ({
          id: f.id,
          name: f.name,
          para_category: f.para_category,
        }));
        setFolders(adapted);
        // 폴더별 링크 병렬 prefetch
        const results = await Promise.all(
          adapted.map((f) =>
            getLinksByFolder(f.id)
              .then((links) => ({ id: f.id, links }))
              .catch(() => ({ id: f.id, links: [] })),
          ),
        );
        const map: Record<number, Link[]> = {};
        for (const r of results) {
          map[r.id] = r.links.map((l) => toLink(l, r.id));
        }
        setLinksByFolder(map);
      })
      .finally(() => setLoading(false));
  }, [userName, para, isUnassigned]);

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
        {loading ? (
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
            />
          ))
        )}
        {parentId && userId && (
          <AddFolderModal
            category={para as ParaCategoryLower}
            userId={userId}
            parentId={parentId}
          />
        )}
      </div>
    </>
  );
}

type ParaCategoryLower = "project" | "area" | "resource" | "archive";
