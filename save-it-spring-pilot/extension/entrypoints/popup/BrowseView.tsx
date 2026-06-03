import {
  ChevronDown,
  ChevronRight,
  ExternalLink,
  FolderOpen,
  FolderPlus,
  Plus,
  X,
} from "lucide-react";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { cn } from "../../lib/utils";
import {
  createFolder,
  getFlatFolders,
  getLinksByFolder,
  markLinkRead,
  type ParaCategory as SpringPara,
} from "../../lib/api";
import { useSyncedState } from "../../lib/useSyncedState";
import { readCache, writeCache } from "../../lib/viewCache";
import { PARA_ORDER, PARA_TOKENS } from "../../lib/para";
import type { Folder, Link, ParaCategory } from "../../lib/types";

/** BrowseView 캐시 스냅샷 — 폴더/루트/폴더별 링크. */
interface BrowseSnapshot {
  folders: Folder[];
  paraRoots: Partial<Record<SpringPara, number>>;
  folderLinks: Record<number, Link[]>;
}

type ParaFilter = ParaCategory;

interface BrowseViewProps {
  userName: string;
  onAddLinkToFolder?: (folderId: number) => void;
}

export function BrowseView({ userName, onAddLinkToFolder }: BrowseViewProps) {
  const [folders, setFolders] = useState<Folder[]>([]);
  // 폴더별 링크 캐시 — 펼친 폴더에 대해서만 fetch
  const [folderLinks, setFolderLinks] = useState<Record<number, Link[]>>({});
  const [paraRoots, setParaRoots] = useState<Partial<Record<SpringPara, number>>>({});
  const [loading, setLoading] = useState(true);
  const [hydrated, setHydrated] = useState(false); // 캐시 읽기 완료 여부
  const [error, setError] = useState("");
  const cacheKey = `saveit_browse_${userName}`;

  const [filter, setFilter] = useSyncedState<ParaFilter | null>(
    "saveit_browse_filter",
    null,
  );
  const [expandedIds, setExpandedIds] = useSyncedState<number[]>(
    "saveit_expanded_folders",
    [],
  );
  const expanded = useMemo(() => new Set(expandedIds), [expandedIds]);

  const [showNewFolder, setShowNewFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");
  const [creatingFolder, setCreatingFolder] = useState(false);
  const [folderError, setFolderError] = useState("");

  useEffect(() => {
    setShowNewFolder(false);
    setNewFolderName("");
    setFolderError("");
  }, [filter]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      // 1) 캐시 시드 → 새로고침해도 직전 폴더/링크 즉시 표시 (깜빡임 제거)
      const cached = await readCache<BrowseSnapshot>(cacheKey);
      if (!cancelled && cached) {
        setFolders(cached.folders);
        setParaRoots(cached.paraRoots);
        setFolderLinks(cached.folderLinks);
        setLoading(false);
      }
      if (!cancelled) setHydrated(true);

      // 2) 백그라운드 갱신
      try {
        const { folders: list, paraRoots } = await getFlatFolders(userName);
        if (cancelled) return;
        const mapped: Folder[] = list.map((f) => ({
          id: f.id,
          name: f.name,
          para_category: f.para_category as ParaCategory | null,
        }));
        setFolders(mapped);
        setParaRoots(paraRoots);

        // 모든 폴더의 링크를 병렬로 prefetch.
        // - expandedIds 가 storage 에서 복원될 때 즉시 데이터가 있어야 click-twice 문제가 사라짐
        // - 카운트 배지를 펼치기 전에도 정확하게 보여줄 수 있음
        // - 폴더 수가 매우 많아지면 페이지네이션 / on-demand 로 바꿔야 함 (베타 OK)
        const results = await Promise.all(
          mapped.map((f) =>
            getLinksByFolder(f.id)
              .then((links) => ({ id: f.id, links }))
              .catch(() => ({ id: f.id, links: [] as Awaited<ReturnType<typeof getLinksByFolder>> })),
          ),
        );
        if (cancelled) return;
        const next: Record<number, Link[]> = {};
        for (const r of results) {
          next[r.id] = r.links.map((l) => ({
            id: l.id,
            folder_id: r.id,
            url: l.link,
            title: l.title,
            description: null,
            priority: 0,
            is_read: l.read ?? false,
            created_at: l.createdAt ?? l.lastUpdate,
            read_at: l.readAt ?? null,
          }));
        }
        setFolderLinks(next);
        writeCache<BrowseSnapshot>(cacheKey, { folders: mapped, paraRoots, folderLinks: next });
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "로드 실패");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userName]);

  const visibleFolders = folders.filter((f) => {
    if (filter === null) return true;
    return f.para_category === filter;
  });

  async function toggleFolder(id: number) {
    const isOpen = expanded.has(id);
    setExpandedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
    // lazy 로드
    if (!isOpen && !folderLinks[id]) {
      try {
        const links = await getLinksByFolder(id);
        const adapted: Link[] = links.map((l) => ({
          id: l.id,
          folder_id: id,
          url: l.link,
          title: l.title,
          description: null,
          priority: 0,
          // Jackson 이 boolean isRead 를 JSON `read` 로 직렬화함
          is_read: l.read ?? false,
          created_at: l.createdAt ?? l.lastUpdate,
          read_at: l.readAt ?? null,
        }));
        setFolderLinks((prev) => ({ ...prev, [id]: adapted }));
      } catch (e) {
        setError(e instanceof Error ? e.message : "링크 로드 실패");
      }
    }
  }

  async function openLink(link: Link) {
    if (browser?.tabs?.create) browser.tabs.create({ url: link.url, active: true });
    else window.open(link.url, "_blank", "noopener,noreferrer");
    if (!link.is_read) {
      try {
        await markLinkRead(link.id);
        setFolderLinks((prev) => {
          const updated = { ...prev };
          if (link.folder_id && updated[link.folder_id]) {
            updated[link.folder_id] = updated[link.folder_id].map((l) =>
              l.id === link.id ? { ...l, is_read: true } : l,
            );
          }
          return updated;
        });
      } catch {
        // 읽음 처리 실패는 UX 에 치명적이지 않으므로 silent
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

  function openNewFolder() {
    setNewFolderName("");
    setFolderError("");
    setShowNewFolder(true);
  }

  function cancelNewFolder() {
    setShowNewFolder(false);
    setNewFolderName("");
    setFolderError("");
  }

  async function handleCreateFolder() {
    if (!filter || !newFolderName.trim()) return;
    const parentId = paraRoots[filter.toUpperCase() as SpringPara];
    if (!parentId) {
      setFolderError("PARA 루트 폴더를 찾을 수 없습니다");
      return;
    }
    setCreatingFolder(true);
    setFolderError("");
    try {
      await createFolder(userName, newFolderName.trim(), parentId);
      const { folders: refreshed } = await getFlatFolders(userName);
      setFolders(
        refreshed.map((f) => ({
          id: f.id,
          name: f.name,
          para_category: f.para_category as ParaCategory | null,
        })),
      );
      setNewFolderName("");
      setShowNewFolder(false);
    } catch (err) {
      setFolderError(err instanceof Error ? err.message : "폴더 생성 실패");
    } finally {
      setCreatingFolder(false);
    }
  }

  const filterChips = PARA_ORDER.map((cat) => ({
    key: cat as ParaFilter,
    letter: PARA_TOKENS[cat].letter,
    label: PARA_TOKENS[cat].label,
    fg: PARA_TOKENS[cat].fg,
    bg: PARA_TOKENS[cat].bg,
  }));

  return (
    <div className="space-y-3 px-3 py-3">
      {/* Filter row */}
      <div className="grid grid-cols-4 gap-1.5">
        {filterChips.map((opt) => {
          const active = filter === opt.key;
          return (
            <button
              key={opt.key}
              type="button"
              onClick={() => setFilter(active ? null : opt.key)}
              title={opt.label}
              className={cn(
                "flex flex-col items-center justify-center gap-0.5 rounded-lg border py-1.5 transition-colors cursor-pointer",
                active ? "border-transparent" : "border-border bg-card hover:bg-accent",
              )}
              style={
                active
                  ? { backgroundColor: opt.bg, borderColor: opt.fg }
                  : undefined
              }
            >
              <span
                className="text-[13px] font-bold leading-none"
                style={{ color: active ? opt.fg : "var(--muted-foreground)" }}
              >
                {opt.letter}
              </span>
              <span
                className={cn(
                  "text-[9px] leading-none tracking-tight",
                  active ? "" : "text-muted-foreground",
                )}
                style={active ? { color: opt.fg } : undefined}
              >
                {opt.label}
              </span>
            </button>
          );
        })}
      </div>

      {/* New folder shortcut — only when filtering by a real PARA category */}
      {filter &&
        (showNewFolder ? (
          <div className="space-y-2 rounded-xl border bg-card/60 p-2.5">
            <div className="flex items-center gap-2">
              <span className="text-[11px] font-medium">새 폴더</span>
              <span className="text-[10px] text-muted-foreground">
                {PARA_TOKENS[filter].label}
              </span>
              <span className="flex-1" />
              <button
                type="button"
                onClick={cancelNewFolder}
                aria-label="취소"
                className="flex h-5 w-5 items-center justify-center rounded text-muted-foreground transition-colors hover:bg-accent hover:text-foreground cursor-pointer"
              >
                <X className="h-3 w-3" />
              </button>
            </div>
            <div className="flex gap-1.5">
              <Input
                autoFocus
                value={newFolderName}
                onChange={(e) => setNewFolderName(e.target.value)}
                placeholder="폴더 이름"
                className="h-8 text-xs"
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleCreateFolder();
                  if (e.key === "Escape") cancelNewFolder();
                }}
              />
              <Button
                type="button"
                size="sm"
                onClick={handleCreateFolder}
                disabled={creatingFolder || !newFolderName.trim()}
              >
                {creatingFolder ? "…" : "생성"}
              </Button>
            </div>
            {folderError && (
              <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
                {folderError}
              </p>
            )}
          </div>
        ) : (
          <button
            type="button"
            onClick={openNewFolder}
            className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-dashed bg-card/40 py-2 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-foreground cursor-pointer"
          >
            <FolderPlus className="h-3 w-3" />
            <span>
              <span className="text-muted-foreground/80">
                {PARA_TOKENS[filter].label}
              </span>
              <span className="ml-1">폴더 추가</span>
            </span>
          </button>
        ))}

      {hydrated && loading && folders.length === 0 && (
        <p className="py-4 text-center text-xs text-muted-foreground">
          불러오는 중…
        </p>
      )}

      {error && (
        <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
          {error}
        </p>
      )}

      {hydrated && !error && (folders.length > 0 || !loading) && (
        <div>
          {visibleFolders.length === 0 ? (
            <p className="py-6 text-center text-xs italic text-muted-foreground">
              폴더가 없어요
            </p>
          ) : (
            <ul className="space-y-1.5">
              {visibleFolders.map((folder) => {
                const linksInFolder = folderLinks[folder.id] ?? [];
                const isOpen = expanded.has(folder.id);
                return (
                  <li
                    key={folder.id}
                    className="overflow-hidden rounded-xl border bg-card"
                  >
                    <div className="group flex items-stretch">
                      <button
                        type="button"
                        onClick={() => toggleFolder(folder.id)}
                        aria-expanded={isOpen}
                        className="flex flex-1 items-center gap-2 px-3 py-2.5 text-left transition-colors active:bg-accent cursor-pointer"
                      >
                        {isOpen ? (
                          <ChevronDown className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                        ) : (
                          <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                        )}
                        <FolderOpen className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                        <span className="flex-1 truncate text-xs font-medium">
                          {folder.name}
                        </span>
                        <span className="shrink-0 text-[10px] tabular-nums text-muted-foreground">
                          {linksInFolder.length}
                        </span>
                      </button>
                      {onAddLinkToFolder && (
                        <button
                          type="button"
                          onClick={() => onAddLinkToFolder(folder.id)}
                          aria-label={`${folder.name}에 링크 추가`}
                          title="이 폴더에 링크 추가"
                          className="flex w-8 items-center justify-center border-l text-muted-foreground opacity-0 transition-all group-hover:opacity-100 hover:text-foreground active:bg-accent cursor-pointer"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      )}
                    </div>
                    {isOpen && (
                      <div className="border-t bg-background/40 p-1.5">
                        {linksInFolder.length === 0 ? (
                          <p className="px-2 py-1.5 text-[11px] italic text-muted-foreground">
                            비어있음
                          </p>
                        ) : (
                          <ul className="space-y-1">
                            {linksInFolder.map((link) => (
                              <li key={link.id}>
                                <LinkRow
                                  title={link.title}
                                  host={host(link.url)}
                                  isRead={link.is_read}
                                  onClick={() => openLink(link)}
                                />
                              </li>
                            ))}
                          </ul>
                        )}
                      </div>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

function LinkRow({
  title,
  host,
  isRead,
  onClick,
}: {
  title: string;
  host: string;
  isRead: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "group flex w-full items-center gap-2 rounded-lg border bg-card px-2.5 py-2 text-left transition-colors active:bg-accent cursor-pointer",
        isRead && "opacity-70",
      )}
    >
      <div className="min-w-0 flex-1">
        <div className="truncate text-xs font-medium">{title}</div>
        <div className="mt-0.5 flex items-center gap-1.5 text-[10px] text-muted-foreground">
          {host && <span className="truncate font-mono">{host}</span>}
        </div>
      </div>
      <ExternalLink className="h-3 w-3 shrink-0 text-muted-foreground opacity-0 transition-opacity group-hover:opacity-100" />
    </button>
  );
}
