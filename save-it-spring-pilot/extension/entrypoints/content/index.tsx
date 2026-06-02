import "../../lib/styles/globals.css";
import { Bookmark, X } from "lucide-react";
import ReactDOM from "react-dom/client";
import { useAuth } from "../../lib/useAuth";
import { AppShell } from "../popup/AppShell";
import { LoginView } from "../popup/LoginView";

export default defineContentScript({
  matches: ["<all_urls>"],
  // 우리 웹앱은 풀 네이티브 UI 가 있으므로 플로팅 위젯을 띄우지 않는다
  // (기능 중복 + 화면 겹침 방지). 익스텐션은 "다른 사이트" 저장용.
  excludeMatches: [
    "https://save-it-pilot-web.vercel.app/*",
    "http://localhost:3000/*",
  ],
  cssInjectionMode: "ui",

  async main(ctx) {
    const ui = await createShadowRootUi(ctx, {
      name: "save-it-floating",
      position: "inline",
      anchor: "body",
      onMount: (container) => {
        const root = ReactDOM.createRoot(container);
        root.render(<FloatingWidget />);
        return root;
      },
      onRemove: (root) => root?.unmount(),
    });
    ui.mount();
  },
});

const TOGGLE_SHADOW =
  "0 8px 20px rgba(15, 23, 42, 0.10), 0 2px 4px rgba(15, 23, 42, 0.05)";
const POS_KEY = "saveit_widget_pos";
const OPEN_KEY = "saveit_widget_open";
const DEFAULT_POS = { top: 20, right: 20 };
const DRAG_THRESHOLD = 3;

const clamp = (n: number, min: number, max: number) =>
  Math.max(min, Math.min(max, n));

function clampPos(p: { top: number; right: number }) {
  return {
    top: clamp(p.top, 8, window.innerHeight - 50),
    right: clamp(p.right, 8, window.innerWidth - 50),
  };
}

function FloatingWidget() {
  const [pos, setPos] = useState(DEFAULT_POS);
  const [open, setOpen] = useState(false);
  const [dragging, setDragging] = useState(false);
  const dragRef = useRef<{
    startX: number;
    startY: number;
    startTop: number;
    startRight: number;
    moved: boolean;
  } | null>(null);

  // Initial load
  useEffect(() => {
    browser.storage.local.get([POS_KEY, OPEN_KEY]).then((r) => {
      const savedPos = r[POS_KEY] as { top: number; right: number } | undefined;
      if (savedPos && typeof savedPos.top === "number" && typeof savedPos.right === "number") {
        setPos(clampPos(savedPos));
      }
      if (typeof r[OPEN_KEY] === "boolean") {
        setOpen(r[OPEN_KEY] as boolean);
      }
    });
  }, []);

  // Cross-tab sync
  useEffect(() => {
    const listener = (
      changes: Record<string, { newValue?: unknown; oldValue?: unknown }>,
      area: string
    ) => {
      if (area !== "local") return;
      const posChange = changes[POS_KEY];
      if (posChange && !dragRef.current) {
        const v = posChange.newValue as { top: number; right: number } | undefined;
        if (v && typeof v.top === "number" && typeof v.right === "number") {
          setPos(clampPos(v));
        }
      }
      const openChange = changes[OPEN_KEY];
      if (openChange) {
        const v = openChange.newValue;
        if (typeof v === "boolean") setOpen(v);
      }
    };
    browser.storage.onChanged.addListener(listener);
    return () => browser.storage.onChanged.removeListener(listener);
  }, []);

  function setOpenSynced(next: boolean) {
    setOpen(next);
    browser.storage.local.set({ [OPEN_KEY]: next }).catch(() => {});
  }

  function handlePointerDown(e: React.PointerEvent<HTMLButtonElement>) {
    if (e.button !== 0) return;
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      startTop: pos.top,
      startRight: pos.right,
      moved: false,
    };
    setDragging(true);
    e.currentTarget.setPointerCapture(e.pointerId);
  }

  function handlePointerMove(e: React.PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag) return;
    const dx = e.clientX - drag.startX;
    const dy = e.clientY - drag.startY;
    if (!drag.moved && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
      drag.moved = true;
    }
    if (drag.moved) {
      setPos(
        clampPos({
          top: drag.startTop + dy,
          right: drag.startRight - dx,
        })
      );
    }
  }

  function handlePointerUp(e: React.PointerEvent<HTMLButtonElement>) {
    const drag = dragRef.current;
    if (!drag) return;
    const moved = drag.moved;
    dragRef.current = null;
    setDragging(false);
    try {
      e.currentTarget.releasePointerCapture(e.pointerId);
    } catch {}

    if (moved) {
      browser.storage.local.set({ [POS_KEY]: pos }).catch(() => {});
    } else {
      setOpenSynced(!open);
    }
  }

  const buttonStyle: React.CSSProperties = {
    boxShadow: TOGGLE_SHADOW,
    touchAction: "none",
    cursor: dragging ? "grabbing" : "grab",
  };

  return (
    <div
      className="fixed font-sans flex flex-col items-end gap-2"
      style={{ top: pos.top, right: pos.right, zIndex: 2147483647 }}
    >
      {open ? (
        <button
          type="button"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          aria-label="닫기"
          title="드래그하여 이동, 클릭하여 닫기"
          className="flex h-10 w-10 items-center justify-center rounded-full border border-border bg-card text-foreground hover:bg-accent transition-colors select-none"
          style={buttonStyle}
        >
          <X className="h-4 w-4 pointer-events-none" />
        </button>
      ) : (
        <button
          type="button"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          aria-label="Save It"
          title="드래그하여 이동, 클릭하여 열기"
          className="group flex h-10 items-center gap-1.5 rounded-full border border-border bg-card pl-2 pr-3.5 text-foreground hover:bg-accent transition-colors select-none"
          style={buttonStyle}
        >
          <span
            className="flex h-7 w-7 items-center justify-center rounded-full pointer-events-none"
            style={{ backgroundColor: "var(--color-para-project-fg)" }}
          >
            <Bookmark className="h-3.5 w-3.5 text-white" />
          </span>
          <span className="text-[12px] font-semibold pointer-events-none">
            Save It
          </span>
        </button>
      )}

      {open && !dragging && (
        <FloatingPanel onSaved={() => setOpenSynced(false)} />
      )}
    </div>
  );
}

function FloatingPanel({ onSaved }: { onSaved: () => void }) {
  const auth = useAuth();

  return (
    <div
      className="w-[360px] overflow-hidden rounded-2xl border border-border bg-background text-foreground animate-fade-up"
      style={{
        boxShadow:
          "0 24px 60px rgba(15, 23, 42, 0.16), 0 6px 18px rgba(15, 23, 42, 0.08)",
      }}
    >
      {auth.status === "loading" && (
        <div className="flex items-center justify-center px-4 py-6">
          <span className="text-xs text-muted-foreground">불러오는 중…</span>
        </div>
      )}
      {auth.status === "anonymous" && <LoginView />}
      {auth.status === "authenticated" && (
        <AppShell
          userName={auth.session.user.userName}
          initialUrl={location.href}
          initialTitle={document.title}
          onSaved={onSaved}
        />
      )}
    </div>
  );
}
