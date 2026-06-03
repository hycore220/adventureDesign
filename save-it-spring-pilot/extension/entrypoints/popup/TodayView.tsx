import { ExternalLink, Flame, Play, RefreshCw } from "lucide-react";
import { cn } from "../../lib/utils";
import {
  getTodayRecommendations,
  markLinkRead,
  type RemindCandidate,
} from "../../lib/api";
import { readCache, writeCache } from "../../lib/viewCache";
import { PARA_TOKENS } from "../../lib/para";
import type { ParaCategory } from "../../lib/types";

interface TodayViewProps {
  userName: string;
}

// 위젯은 페이지 안에서 동작하므로 location 이 현재 사이트. 유튜브면 컨텍스트 모드.
function isYoutubePage(): boolean {
  try {
    const h = location.hostname.toLowerCase();
    return /(^|\.)youtube\.com$/.test(h) || h === "youtu.be";
  } catch {
    return false;
  }
}

export function TodayView({ userName }: TodayViewProps) {
  const onYoutube = isYoutubePage();
  const cacheKey = `saveit_today_${userName}_${onYoutube ? "yt" : "all"}`;

  const [candidates, setCandidates] = useState<RemindCandidate[]>([]);
  const [hydrated, setHydrated] = useState(false); // 캐시 읽기 완료 여부
  const [revalidating, setRevalidating] = useState(false); // 실제 fetch 중
  const [error, setError] = useState("");

  async function load() {
    setRevalidating(true);
    setError("");
    try {
      // 유튜브 watch/일반 페이지에선 "내가 저장한 유튜브 영상"만 (youtube_ctx)
      const list = await getTodayRecommendations(
        userName,
        10,
        onYoutube ? "youtube_ctx" : undefined,
      );
      setCandidates(list);
      writeCache(cacheKey, list);
    } catch (e) {
      setError(e instanceof Error ? e.message : "추천 로드 실패");
    } finally {
      setRevalidating(false);
    }
  }

  useEffect(() => {
    let alive = true;
    (async () => {
      // 1) 캐시 시드 → 새로고침해도 직전 추천 즉시 표시 (깜빡임 제거)
      const cached = await readCache<RemindCandidate[]>(cacheKey);
      if (alive && cached) setCandidates(cached);
      if (alive) setHydrated(true);
      // 2) 백그라운드 갱신
      load();
    })();
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
        {onYoutube ? (
          <Play className="h-3.5 w-3.5 text-red-600" />
        ) : (
          <Flame className="h-3.5 w-3.5 text-orange-500" />
        )}
        <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
          {onYoutube ? "내가 저장한 유튜브" : "오늘의 추천"}
        </span>
        <span className="flex-1" />
        <button
          type="button"
          onClick={load}
          disabled={revalidating}
          className="flex h-6 w-6 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-accent hover:text-foreground cursor-pointer disabled:opacity-40"
          title="새로고침"
        >
          <RefreshCw className={cn("h-3 w-3", revalidating && "animate-spin")} />
        </button>
      </header>

      {hydrated && revalidating && candidates.length === 0 && (
        <p className="py-4 text-center text-xs text-muted-foreground">
          벨커브 계산 중…
        </p>
      )}

      {error && (
        <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
          {error}
        </p>
      )}

      {hydrated && !revalidating && !error && candidates.length === 0 && (
        <p className="py-6 text-center text-xs italic text-muted-foreground">
          {onYoutube ? (
            <>저장한 유튜브 영상이 없어요.<br />유튜브 영상을 저장하면 여기 모여요.</>
          ) : (
            <>오늘 추천할 링크가 없어요.<br />링크를 저장하면 며칠 뒤부터 추천이 시작됩니다.</>
          )}
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
