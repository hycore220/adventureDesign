import { Bookmark, ChevronLeft, Flame, FolderOpen, LogOut, Plus, X } from "lucide-react";
import { Button } from "../../components/ui/button";
import { cn } from "../../lib/utils";
import { logout } from "../../lib/api";
import { BrowseView } from "./BrowseView";
import { SaveView } from "./SaveView";
import { TodayView } from "./TodayView";

type Mode = "today" | "browse" | "add";

interface AppShellProps {
  userName: string;
  initialUrl: string;
  initialTitle: string;
  onSaved: () => void;
  onClose?: () => void;
}

export function AppShell({
  userName,
  initialUrl,
  initialTitle,
  onSaved,
  onClose,
}: AppShellProps) {
  // 첫 진입은 "오늘의 추천" — 시범 포팅의 새 기능 부각
  const [mode, setMode] = useState<Mode>("today");
  const [pendingFolderId, setPendingFolderId] = useState<number | null>(null);

  function goToAdd(folderId: number | null = null) {
    setPendingFolderId(folderId);
    setMode("add");
  }

  async function handleSignOut() {
    await logout();
  }

  const isMain = mode === "today" || mode === "browse";

  return (
    <div className="flex flex-col">
      {isMain ? (
        <>
          <header className="flex items-center gap-2 border-b px-3 py-2.5">
            <span
              className="flex h-7 w-7 items-center justify-center rounded-lg"
              style={{ backgroundColor: "var(--color-para-project-fg)" }}
              aria-hidden
            >
              <Bookmark className="h-3.5 w-3.5 text-white" />
            </span>
            <span className="text-sm font-semibold">Save It</span>
            <span className="flex-1" />
            <Button
              type="button"
              size="xs"
              onClick={() => goToAdd(null)}
              className="h-7 gap-1 px-2.5"
            >
              <Plus className="h-3 w-3" />
              추가
            </Button>
            {onClose && (
              <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={onClose}
                aria-label="닫기"
                className="h-7 w-7 text-muted-foreground"
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            )}
          </header>

          {/* Today / Browse 탭 */}
          <nav className="flex border-b">
            <TabButton
              active={mode === "today"}
              onClick={() => setMode("today")}
              icon={<Flame className="h-3 w-3" />}
              label="오늘"
            />
            <TabButton
              active={mode === "browse"}
              onClick={() => setMode("browse")}
              icon={<FolderOpen className="h-3 w-3" />}
              label="둘러보기"
            />
          </nav>
        </>
      ) : (
        <header className="flex items-center gap-2 border-b px-2 py-2.5">
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={() => setMode("browse")}
            aria-label="뒤로"
            className="h-7 w-7 shrink-0"
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm font-semibold">새 링크 추가</span>
          <span className="flex-1" />
          {onClose && (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={onClose}
              aria-label="닫기"
              className="h-7 w-7 shrink-0 text-muted-foreground"
            >
              <X className="h-3.5 w-3.5" />
            </Button>
          )}
        </header>
      )}

      {mode === "today" && <TodayView userName={userName} />}
      {mode === "browse" && <BrowseView userName={userName} onAddLinkToFolder={goToAdd} />}
      {mode === "add" && (
        <SaveView
          userName={userName}
          initialUrl={initialUrl}
          initialTitle={initialTitle}
          initialFolderId={pendingFolderId}
          onSaved={onSaved}
        />
      )}

      <footer className="flex items-center justify-end border-t px-4 py-2">
        <button
          type="button"
          onClick={handleSignOut}
          className="flex items-center gap-1 text-[11px] text-muted-foreground transition-colors hover:text-foreground cursor-pointer"
        >
          로그아웃
          <LogOut className="h-3 w-3" />
        </button>
      </footer>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex flex-1 items-center justify-center gap-1.5 border-b-2 py-2 text-[11px] font-medium transition-colors cursor-pointer",
        active
          ? "border-foreground text-foreground"
          : "border-transparent text-muted-foreground hover:text-foreground",
      )}
    >
      {icon}
      {label}
    </button>
  );
}
