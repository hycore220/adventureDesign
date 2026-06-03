"use client";

import { ChevronLeft } from "lucide-react";
import { useRouter } from "next/navigation";

interface BackButtonProps {
  fallbackHref?: string;
}

export function BackButton({ fallbackHref = "/" }: BackButtonProps) {
  const router = useRouter();
  return (
    <button
      type="button"
      onClick={() => {
        if (window.history.length > 1) router.back();
        else router.push(fallbackHref);
      }}
      aria-label="뒤로"
      className="flex h-9 w-9 items-center justify-center rounded-full text-foreground hover:bg-accent transition-colors"
    >
      <ChevronLeft className="h-5 w-5" />
    </button>
  );
}
