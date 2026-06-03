import type { ParaCategory } from "@/lib/types";
import { PARA_TOKENS, UNASSIGNED_TOKEN } from "@/lib/para";
import { cn } from "@/lib/utils";

interface ParaBadgeProps {
  category: ParaCategory | null;
  size?: "sm" | "md";
  className?: string;
}

export function ParaBadge({ category, size = "sm", className }: ParaBadgeProps) {
  const token = category ? PARA_TOKENS[category] : null;
  const sizing = size === "md" ? "h-7 w-7 text-sm" : "h-5 w-5 text-[10px]";

  if (!token) {
    return (
      <span
        className={cn(
          "inline-flex items-center justify-center rounded-md font-bold",
          sizing,
          className
        )}
        style={{ backgroundColor: UNASSIGNED_TOKEN.bg, color: UNASSIGNED_TOKEN.fg }}
        aria-label={UNASSIGNED_TOKEN.label}
        title={UNASSIGNED_TOKEN.label}
      >
        ·
      </span>
    );
  }

  return (
    <span
      className={cn(
        "inline-flex items-center justify-center rounded-md font-bold",
        sizing,
        className
      )}
      style={{ backgroundColor: token.fg, color: "#fff" }}
      aria-label={token.label}
      title={token.label}
    >
      {token.letter}
    </span>
  );
}
