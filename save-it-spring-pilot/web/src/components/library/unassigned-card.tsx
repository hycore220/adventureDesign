import Link from "next/link";
import { Inbox } from "lucide-react";
import { UNASSIGNED_TOKEN } from "@/lib/para";

interface UnassignedCardProps {
  linkCount: number;
}

export function UnassignedCard({ linkCount }: UnassignedCardProps) {
  return (
    <Link
      href="/category/unassigned"
      className="flex items-center gap-3 rounded-2xl p-4 transition-transform active:scale-[0.98]"
      style={{ backgroundColor: UNASSIGNED_TOKEN.bg }}
    >
      <span
        className="inline-flex h-8 w-8 items-center justify-center rounded-lg"
        style={{ backgroundColor: UNASSIGNED_TOKEN.fg, color: "#fff" }}
        aria-hidden
      >
        <Inbox className="h-4 w-4" />
      </span>
      <div className="flex-1">
        <div className="text-sm font-semibold">{UNASSIGNED_TOKEN.label}</div>
        <div className="text-xs text-muted-foreground tabular-nums">
          {linkCount}개 링크
        </div>
      </div>
    </Link>
  );
}
