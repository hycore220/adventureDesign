import type { ReactNode } from "react";

interface AppHeaderProps {
  title: string;
  left?: ReactNode;
  right?: ReactNode;
}

export function AppHeader({ title, left, right }: AppHeaderProps) {
  return (
    <header
      className="sticky top-0 z-30 flex items-center gap-2 border-b bg-background/95 px-3 backdrop-blur supports-[backdrop-filter]:bg-background/80"
      style={{ paddingTop: "env(safe-area-inset-top)" }}
    >
      <div className="flex h-12 w-full items-center gap-2">
        <div className="flex w-9 justify-start">{left}</div>
        <h1 className="flex-1 truncate text-center text-base font-semibold">{title}</h1>
        <div className="flex w-9 justify-end">{right}</div>
      </div>
    </header>
  );
}
