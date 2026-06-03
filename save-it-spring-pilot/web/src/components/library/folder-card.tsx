import Link from "next/link";
import { FolderOpen } from "lucide-react";

interface FolderCardProps {
  id: string;
  name: string;
  linkCount: number;
}

export function FolderCard({ id, name, linkCount }: FolderCardProps) {
  return (
    <Link
      href={`/folder/${id}`}
      className="flex items-center gap-3 rounded-xl border bg-card px-4 py-3 transition-colors active:bg-accent"
    >
      <FolderOpen className="h-4 w-4 shrink-0 text-muted-foreground" />
      <span className="flex-1 truncate text-sm font-medium">{name}</span>
      <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
        {linkCount}
      </span>
    </Link>
  );
}
