"use client";

import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { logout } from "@/lib/api";

export function SignOutButton() {
  const router = useRouter();
  async function handle() {
    await logout();
    router.push("/login");
    router.refresh();
  }
  return (
    <button
      type="button"
      onClick={handle}
      className="flex w-full items-center justify-between rounded-xl border bg-card px-4 py-3 text-sm transition-colors active:bg-accent"
    >
      <span>로그아웃</span>
      <LogOut className="h-4 w-4 text-muted-foreground" />
    </button>
  );
}
