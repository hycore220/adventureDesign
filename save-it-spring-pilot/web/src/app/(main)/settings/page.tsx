"use client";

import { AppHeader } from "@/components/shell/app-header";
import { SignOutButton } from "@/components/actions/sign-out-button";
import { ReminderSettings } from "@/components/settings/reminder-settings";
import { useAuth } from "@/lib/useAuth";

export default function SettingsPage() {
  const auth = useAuth();
  const userName =
    auth.status === "authenticated" ? auth.session.user.userName : "";

  return (
    <>
      <AppHeader title="설정" />
      <div className="space-y-4 p-4">
        <section className="space-y-2">
          <h2 className="px-1 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            계정
          </h2>
          <div className="rounded-xl border bg-card px-4 py-3">
            <div className="text-xs text-muted-foreground">사용자</div>
            <div className="mt-0.5 text-sm font-medium">{userName}</div>
          </div>
          <SignOutButton />
        </section>

        <ReminderSettings />
      </div>
    </>
  );
}
