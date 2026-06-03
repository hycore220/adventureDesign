"use client";

import { PARA_TOKENS, UNASSIGNED_TOKEN } from "@/lib/para";
import { markLinkRead, type RemindCandidate } from "@/lib/api";

function hostOf(url: string) {
  try {
    return new URL(url).host.replace(/^www\./, "");
  } catch {
    return "";
  }
}

interface RemindCardProps {
  candidate: RemindCandidate;
}

export function RemindCard({ candidate }: RemindCardProps) {
  const dots = 0; // Spring 백엔드는 우선도 미저장
  const host = hostOf(candidate.link);
  const para = candidate.paraStatus
    ? (candidate.paraStatus.toLowerCase() as
        | "project"
        | "area"
        | "resource"
        | "archive")
    : null;
  const token = para ? PARA_TOKENS[para] : UNASSIGNED_TOKEN;

  return (
    <a
      href={candidate.link}
      target="_blank"
      rel="noopener noreferrer"
      onClick={() => {
        markLinkRead(candidate.linkId).catch(() => {});
      }}
      style={{ backgroundColor: token.bg }}
      className="relative block rounded-2xl px-4 py-4 transition-transform active:scale-[0.98]"
    >
      {dots > 0 && (
        <span
          className="absolute right-3 top-3 flex gap-0.5"
          aria-label={`우선도 ${dots}`}
        >
          {Array.from({ length: dots }).map((_, i) => (
            <span key={i} className="h-1.5 w-1.5 rounded-full bg-foreground" />
          ))}
        </span>
      )}

      <div className="pr-10 text-sm font-semibold text-foreground line-clamp-2">
        {candidate.title}
      </div>

      <div className="mt-1 text-xs text-muted-foreground">
        {candidate.reason}
        {host && (
          <>
            {" · "}
            <span className="font-mono">{host}</span>
          </>
        )}
        {" · "}
        <span className="tabular-nums">
          {Math.round(candidate.remindScore * 100)}점
        </span>
      </div>
    </a>
  );
}
