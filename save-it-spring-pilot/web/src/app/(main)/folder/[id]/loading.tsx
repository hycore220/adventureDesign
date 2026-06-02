import { AppHeader } from "@/components/shell/app-header";

export default function FolderLoading() {
  return (
    <>
      <AppHeader title="…" />
      <div className="space-y-2 p-4">
        {[0, 1, 2].map((i) => (
          <div key={i} className="h-14 animate-pulse rounded-xl bg-muted" />
        ))}
      </div>
    </>
  );
}
