"use client";

import { useState } from "react";
import Link from "next/link";
import {
  ArrowUpRight,
  ChevronDown,
  ChevronRight,
  FolderOpen,
  Trash2,
} from "lucide-react";
import { LinkCard } from "./link-card";
import type { Link as LinkRow } from "@/lib/types";

interface FolderAccordionItemProps {
  id: number;
  name: string;
  links: LinkRow[];
  /** 주어지면 폴더 삭제 버튼 노출. */
  onDeleteFolder?: (id: number) => void;
  /** 주어지면 각 링크에 삭제 버튼 노출. */
  onDeleteLink?: (id: number) => void;
}

export function FolderAccordionItem({
  id,
  name,
  links,
  onDeleteFolder,
  onDeleteLink,
}: FolderAccordionItemProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="overflow-hidden rounded-xl border bg-card">
      <div className="flex items-stretch">
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
          aria-controls={`folder-${id}-content`}
          className="flex flex-1 items-center gap-2 px-3 py-3 text-left transition-colors active:bg-accent"
        >
          {expanded ? (
            <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground" />
          ) : (
            <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" />
          )}
          <FolderOpen className="h-4 w-4 shrink-0 text-muted-foreground" />
          <span className="flex-1 truncate text-sm font-medium">{name}</span>
          <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
            {links.length}
          </span>
        </button>
        {onDeleteFolder && (
          <button
            type="button"
            aria-label={`${name} 폴더 삭제`}
            title="폴더 삭제"
            onClick={() => onDeleteFolder(id)}
            className="flex w-10 items-center justify-center border-l text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive active:bg-accent"
          >
            <Trash2 className="h-4 w-4" />
          </button>
        )}
        <Link
          href={`/folder/${id}`}
          aria-label={`${name} 폴더 페이지 열기`}
          title="폴더 페이지에서 관리"
          className="flex w-10 items-center justify-center border-l text-muted-foreground transition-colors hover:text-foreground active:bg-accent"
        >
          <ArrowUpRight className="h-4 w-4" />
        </Link>
      </div>
      {expanded && (
        <div id={`folder-${id}-content`} className="border-t bg-background/40 p-2">
          {links.length === 0 ? (
            <p className="px-2 py-3 text-xs italic text-muted-foreground">
              비어있음
            </p>
          ) : (
            <ul className="space-y-1">
              {links.map((l) => (
                <li key={l.id}>
                  <LinkCard link={l} onDelete={onDeleteLink} />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
