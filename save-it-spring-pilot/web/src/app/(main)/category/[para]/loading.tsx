import { AppHeader } from "@/components/shell/app-header";

export default function CategoryLoading() {
  return (
    <>
      <AppHeader title="…" />
      <div className="space-y-2 p-4">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="h-12 animate-pulse rounded-xl bg-muted" />
        ))}
      </div>
    </>
  );
}
