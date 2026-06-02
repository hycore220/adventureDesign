import { ExternalLink, Flame, RefreshCw } from "lucide-react";
import { cn } from "../../lib/utils";
import {
  getTodayRecommendations,
  markLinkRead,
  type RemindCandidate,
} from "../../lib/api";
import { PARA_TOKENS } from "../../lib/para";
import type { ParaCategory } from "../../lib/types";

interface TodayViewProps {
  userName: string;
}

export function TodayView({ userName }: TodayViewProps) {
  const [candidates, setCandidates] = useState<RemindCandidate[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      const list = await getTodayRecommendations(userName, 10);
      setCandidates(list);
    } catch (e) {
      setError(e instanceof Error ? e.message : "추천 로드 실패");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, [userName]);

  async function openLink(c: RemindCandidate) {
    if (browser?.tabs?.create) browser.tabs.create({ url: c.link, active: true });
    else window.open(c.link, "_blank", "noopener,noreferrer");
    if (!c.isRead) {
      try {
        await markLinkRead(c.linkId);
        setCandidates((prev) =>
          prev.map((x) => (x.linkId === c.linkId ? { ...x, isRead: true } : x)),
        );
      } catch {
        /* silent */
      }
    }
  }

  function host(url: string) {
    try {
      return new URL(url).host.replace(/^www\./, "");
    } catch {
      return "";
    }
  }

  return (
    <div className="space-y-3 px-3 py-3">
      <header className="flex items-center gap-2">
        <Flame className="h-3.5 w-3.5 text-orange-500" />
        <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
          오늘의 추천
        </span>
        <span className="flex-1" />
        <button
          type="button"
          onClick={load}
          disabled={loading}
          className="flex h-6 w-6 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-accent hover:text-foreground cursor-pointer disabled:opacity-40"
          title="새로고침"
        >
          <RefreshCw className={cn("h-3 w-3", loading && "animate-spin")} />
        </button>
      </header>

      {loading && candidates.length === 0 && (
        <p className="py-4 text-center text-xs text-muted-foreground">
          벨커브 계산 중…
        </p>
      )}

      {error && (
        <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
          {error}
        </p>
      )}

      {!loading && !error && candidates.length === 0 && (
        <p className="py-6 text-center text-xs italic text-muted-foreground">
          오늘 추천할 링크가 없어요.<br />
          링크를 저장하면 며칠 뒤부터 추천이 시작됩니다.
        </p>
      )}

      <ul className="space-y-1.5">
        {candidates.map((c) => {
          const para = c.paraStatus
            ? (c.paraStatus.toLowerCase() as ParaCategory)
            : null;
          const token = para ? PARA_TOKENS[para] : null;
          return (
            <li key={c.linkId}>
              <button
                type="button"
                onClick={() => openLink(c)}
                className={cn(
                  "group flex w-full items-stretch gap-0 overflow-hidden rounded-xl border bg-card text-left transition-colors active:bg-accent cursor-pointer",
                  c.isRead && "opacity-60",
                )}
              >
                {/* PARA 컬러 사이드바 */}
                <span
                  className="w-1 shrink-0"
                  style={{ backgroundColor: token?.fg ?? "#737373" }}
                  aria-hidden
                />
                <div className="min-w-0 flex-1 px-3 py-2">
                  <div className="flex items-center gap-1.5">
                    {token && (
                      <span
                        className="flex h-4 w-4 shrink-0 items-center justify-center rounded text-[9px] font-bold"
                        style={{ backgroundColor: token.bg, color: token.fg }}
                      >
                        {token.letter}
                      </span>
                    )}
                    <span className="flex-1 truncate text-xs font-medium">
                      {c.title || c.link}
                    </span>
                    <ExternalLink className="h-3 w-3 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
                  </div>
                  <div className="mt-1 flex items-center gap-2 text-[10px] text-muted-foreground">
                    <span className="font-mono truncate">{host(c.link)}</span>
                    <span className="shrink-0">·</span>
                    <span className="truncate">{c.reason}</span>
                    <span className="flex-1" />
                    <span
                      className="shrink-0 tabular-nums"
                      title={`score=${c.remindScore.toFixed(3)}`}
                    >
                      {Math.round(c.remindScore * 100)}점
                    </span>
                  </div>
                </div>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
