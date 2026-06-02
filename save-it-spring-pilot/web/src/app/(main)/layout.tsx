"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/useAuth";
import { ensurePushIfEnabled } from "@/lib/api";
import { BottomNav } from "@/components/shell/bottom-nav";

export default function MainLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const auth = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (auth.status === "anonymous") {
      router.replace("/login");
    }
    if (auth.status === "authenticated") {
      // 로그인 상태인데 알림 ON + 권한 granted 인데 구독 끊겨있으면 자동 재구독
      // (로그아웃 시 구독 해제 → 재로그인 시 여기서 복구)
      ensurePushIfEnabled().catch(() => {});
    }
  }, [auth.status, router]);

  return (
    <div className="relative mx-auto flex h-svh w-full max-w-md flex-col overflow-hidden">
      <main
        className="flex-1 overflow-y-auto"
        style={{
          paddingBottom: `calc(env(safe-area-inset-bottom) + 64px)`,
        }}
      >
        {auth.status === "loading" ? (
          <div className="flex h-full items-center justify-center text-xs text-muted-foreground">
            불러오는 중…
          </div>
        ) : auth.status === "authenticated" ? (
          children
        ) : null}
      </main>
      <BottomNav />
    </div>
  );
}
