"use client";

import { useRouter } from "next/navigation";
import { ExternalLink, Trash2 } from "lucide-react";
import { markLinkRead } from "@/lib/api";
import { cn } from "@/lib/utils";
import type { Link as LinkRow } from "@/lib/types";

interface LinkCardProps {
  link: LinkRow;
  /** 주어지면 삭제 버튼 노출. 호출자가 confirm/삭제/목록갱신 처리. */
  onDelete?: (id: number) => void;
}

function hostOf(url: string) {
  try {
    return new URL(url).host.replace(/^www\./, "");
  } catch {
    return "";
  }
}

export function LinkCard({ link, onDelete }: LinkCardProps) {
  const router = useRouter();

  function handleMarkRead() {
    if (link.is_read) return;
    void (async () => {
      try {
        await markLinkRead(link.id);
        router.refresh();
      } catch {
        /* silent — UX 에 치명적이지 않음 */
      }
    })();
  }

  const dots = Math.min(2, link.priority ?? 0);

  return (
    <a
      href={link.url}
      target="_blank"
      rel="noopener noreferrer"
      onClick={handleMarkRead}
      onAuxClick={handleMarkRead}
      className={cn(
        "group flex w-full items-center gap-3 rounded-xl border bg-card px-4 py-3 text-left transition-colors active:bg-accent",
        link.is_read && "opacity-70",
      )}
    >
      <div className="min-w-0 flex-1">
        <div className="truncate text-sm font-medium">{link.title}</div>
        <div className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
          {dots > 0 && (
            <span className="flex gap-0.5" aria-label={`우선도 ${dots}`}>
              {Array.from({ length: dots }).map((_, i) => (
                <span
                  key={i}
                  className="h-1.5 w-1.5 rounded-full bg-foreground"
                />
              ))}
            </span>
          )}
          <span className="truncate font-mono">{hostOf(link.url)}</span>
        </div>
      </div>
      {onDelete && (
        <button
          type="button"
          aria-label="링크 삭제"
          title="삭제"
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onDelete(link.id);
          }}
          className="shrink-0 rounded-lg p-1 text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
        >
          <Trash2 className="h-4 w-4" />
        </button>
      )}
      <ExternalLink className="h-4 w-4 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
    </a>
  );
}
