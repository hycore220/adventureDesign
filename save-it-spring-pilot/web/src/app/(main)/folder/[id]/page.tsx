"use client";

import { use, useEffect, useState } from "react";
import { notFound } from "next/navigation";
import { AppHeader } from "@/components/shell/app-header";
import { BackButton } from "@/components/shell/back-button";
import { ParaBadge } from "@/components/primitives/para-badge";
import { LinkCard } from "@/components/library/link-card";
import { AddLinkFab } from "@/components/actions/add-link-fab";
import { useAuth } from "@/lib/useAuth";
import { getFlatFolders, getLinksByFolder } from "@/lib/api";
import { type Folder, type Link, toLink } from "@/lib/types";

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

  const [folder, setFolder] = useState<Folder | null>(null);
  const [links, setLinks] = useState<Link[]>([]);
  const [loading, setLoading] = useState(true);
  const [notExists, setNotExists] = useState(false);

  useEffect(() => {
    if (!userName || !Number.isFinite(folderId)) return;
    setLoading(true);
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
        setFolder({
          id: found.id,
          name: found.name,
          para_category: found.para_category,
        });
        setLinks(springLinks.map((l) => toLink(l, folderId)));
      })
      .finally(() => setLoading(false));
  }, [userName, folderId]);

  if (notExists) notFound();

  if (loading || !folder) {
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
