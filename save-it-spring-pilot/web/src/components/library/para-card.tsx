import Link from "next/link";
import type { ParaCategory } from "@/lib/types";
import { PARA_TOKENS } from "@/lib/para";

interface ParaCardProps {
  category: ParaCategory;
  folderCount: number;
  linkCount: number;
}

export function ParaCard({ category, folderCount, linkCount }: ParaCardProps) {
  const token = PARA_TOKENS[category];
  return (
    <Link
      href={`/category/${category}`}
      className="group flex flex-col gap-3 rounded-2xl p-4 transition-transform active:scale-[0.98]"
      style={{ backgroundColor: token.bg }}
    >
      <span
        className="inline-flex h-8 w-8 items-center justify-center rounded-lg font-bold text-white"
        style={{ backgroundColor: token.fg }}
        aria-hidden
      >
        {token.letter}
      </span>
      <div>
        <div className="text-sm font-semibold text-foreground">{token.label}</div>
        <div className="mt-0.5 text-xs text-muted-foreground tabular-nums">
          {folderCount}개 폴더 · {linkCount}개 링크
        </div>
      </div>
    </Link>
  );
}
