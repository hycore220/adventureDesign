import { AppHeader } from "@/components/shell/app-header";

export default function LibraryLoading() {
  return (
    <>
      <AppHeader title="라이브러리" />
      <div className="p-4 space-y-3">
        <div className="grid grid-cols-2 gap-3">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="h-28 animate-pulse rounded-2xl bg-muted" />
          ))}
        </div>
        <div className="h-16 animate-pulse rounded-2xl bg-muted" />
      </div>
    </>
  );
}
