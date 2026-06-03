import type { ParaCategory } from "./types";

export const PARA_TOKENS: Record<
  ParaCategory,
  { letter: string; label: string; fg: string; bg: string }
> = {
  project:  { letter: "P", label: "Projects",  fg: "#2563eb", bg: "#eff6ff" },
  area:     { letter: "A", label: "Areas",     fg: "#f59e0b", bg: "#fef3c7" },
  resource: { letter: "R", label: "Resources", fg: "#ec4899", bg: "#fce7f3" },
  archive:  { letter: "A", label: "Archives",  fg: "#737373", bg: "#f5f5f5" },
} as const;

export const UNASSIGNED_TOKEN = {
  label: "미지정",
  fg: "#6b7280",
  bg: "#f9fafb",
} as const;

export const PARA_ORDER: ParaCategory[] = [
  "project",
  "area",
  "resource",
  "archive",
];

export const BRAND_COLOR = PARA_TOKENS.project.fg; // "#2563eb"
