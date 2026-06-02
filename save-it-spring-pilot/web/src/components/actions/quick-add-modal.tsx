"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Dialog } from "@base-ui/react/dialog";
import {
  ArrowLeft,
  Check,
  ChevronDown,
  ChevronUp,
  FolderPlus,
  Loader2,
  X,
} from "lucide-react";
import {
  createFolder,
  createLink,
  getFlatFolders,
  getUserLinks,
  loadTokens,
  type ParaCategory as SpringPara,
} from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { PARA_ORDER, PARA_TOKENS } from "@/lib/para";
import type { Folder, ParaCategory } from "@/lib/types";

type Step = "url" | "folder";
type ParaTab = ParaCategory;

interface QuickAddModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  userId: string;
}

const PRIORITY_OPTIONS = [
  { value: 0, label: "보통" },
  { value: 1, label: "중요" },
  { value: 2, label: "매우" },
];

function isHttpUrl(value: string): boolean {
  try {
    const u = new URL(value);
    return u.protocol === "http:" || u.protocol === "https:";
  } catch {
    return false;
  }
}

export function QuickAddModal({ open, onOpenChange }: QuickAddModalProps) {
  const router = useRouter();

  const [step, setStep] = useState<Step>("url");
  const [url, setUrl] = useState("");
  const [title, setTitle] = useState("");
  const titleDirtyRef = useRef(false);
  const [metaLoading, setMetaLoading] = useState(false);
  const [description, setDescription] = useState(""); // 백엔드 미저장
  const [priority, setPriority] = useState(0); // 백엔드 미저장
  const [showDetails, setShowDetails] = useState(false);

  const [folders, setFolders] = useState<Folder[]>([]);
  const [paraRoots, setParaRoots] = useState<
    Partial<Record<SpringPara, number>>
  >({});
  const [foldersLoading, setFoldersLoading] = useState(false);
  const [selectedPara, setSelectedPara] = useState<ParaTab>("project");
  const [selectedFolderId, setSelectedFolderId] = useState<number | null>(null);
  const [showNewFolder, setShowNewFolder] = useState(false);
  const [newFolderName, setNewFolderName] = useState("");
  const [creatingFolder, setCreatingFolder] = useState(false);

  const [duplicate, setDuplicate] = useState<{ id: number } | null>(null);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function resetAll() {
    setStep("url");
    setUrl("");
    setTitle("");
    titleDirtyRef.current = false;
    setMetaLoading(false);
    setDescription("");
    setPriority(0);
    setShowDetails(false);
    setFolders([]);
    setParaRoots({});
    setFoldersLoading(false);
    setSelectedPara("project");
    setSelectedFolderId(null);
    setShowNewFolder(false);
    setNewFolderName("");
    setCreatingFolder(false);
    setDuplicate(null);
    setError("");
    setSubmitting(false);
  }

  // 클립보드 자동 입력
  useEffect(() => {
    if (!open) return;
    if (typeof navigator === "undefined" || !navigator.clipboard?.readText) return;
    navigator.clipboard
      .readText()
      .then((text) => {
        const trimmed = text.trim();
        if (trimmed && isHttpUrl(trimmed)) setUrl(trimmed);
      })
      .catch(() => {});
  }, [open]);

  // 메타데이터 (og:title) 자동 가져오기 — /api/metadata 유지
  useEffect(() => {
    if (!open) return;
    if (!url || !isHttpUrl(url)) return;
    const target = url;
    let cancelled = false;
    const handle = setTimeout(() => {
      if (cancelled) return;
      setMetaLoading(true);
      fetch(`/api/metadata?url=${encodeURIComponent(target)}`)
        .then(async (r) => {
          const data = await r.json().catch(() => null);
          if (cancelled) return;
          if (data?.ok && data.title && !titleDirtyRef.current) {
            setTitle(data.title);
          }
        })
        .catch(() => {})
        .finally(() => {
          if (!cancelled) setMetaLoading(false);
        });
    }, 500);
    return () => {
      cancelled = true;
      clearTimeout(handle);
      setMetaLoading(false);
    };
  }, [url, open]);

  async function loadFolders() {
    const tokens = loadTokens();
    if (!tokens) return;
    setFoldersLoading(true);
    try {
      const { folders: flat, paraRoots } = await getFlatFolders(tokens.userName);
      setFolders(
        flat.map((f) => ({
          id: f.id,
          name: f.name,
          para_category: f.para_category,
        })),
      );
      setParaRoots(paraRoots);
    } catch (e) {
      setError(e instanceof Error ? e.message : "폴더 로드 실패");
    } finally {
      setFoldersLoading(false);
    }
  }

  async function handleNext() {
    setError("");
    setDuplicate(null);
    if (!url.trim() || !isHttpUrl(url.trim())) {
      setError("올바른 URL을 입력하세요");
      return;
    }
    const tokens = loadTokens();
    if (!tokens) return;
    try {
      const all = await getUserLinks(tokens.userName);
      const existing = all.find((l) => l.link === url.trim());
      if (existing) {
        setDuplicate({ id: existing.id });
        return;
      }
    } catch {
      // 중복 체크 실패는 무시하고 진행
    }
    setStep("folder");
    void loadFolders();
  }

  async function handleCreateFolder() {
    const name = newFolderName.trim();
    if (!name) return;
    const tokens = loadTokens();
    if (!tokens) return;
    const upperPara = selectedPara.toUpperCase() as SpringPara;
    const parentId = paraRoots[upperPara];
    if (!parentId) {
      setError("PARA 루트 폴더를 찾을 수 없어요");
      return;
    }
    setCreatingFolder(true);
    setError("");
    try {
      await createFolder(tokens.userName, name, parentId);
      // 폴더 재조회 후 새로 만든 것 선택
      const { folders: refreshed } = await getFlatFolders(tokens.userName);
      setFolders(
        refreshed.map((f) => ({
          id: f.id,
          name: f.name,
          para_category: f.para_category,
        })),
      );
      const created = refreshed.find(
        (f) => f.name === name && f.para_category === selectedPara,
      );
      if (created) setSelectedFolderId(created.id);
      setShowNewFolder(false);
      setNewFolderName("");
    } catch (e) {
      setError(e instanceof Error ? e.message : "폴더 생성 실패");
    } finally {
      setCreatingFolder(false);
    }
  }

  async function handleSave() {
    if (!selectedFolderId) {
      setError("폴더를 선택하세요");
      return;
    }
    const tokens = loadTokens();
    if (!tokens) return;
    setSubmitting(true);
    setError("");
    setDuplicate(null);
    try {
      await createLink({
        userName: tokens.userName,
        link: url.trim(),
        title: title.trim() || url.trim(),
        folderId: selectedFolderId,
      });
      onOpenChange(false);
      resetAll();
      router.refresh();
    } catch (e) {
      setError(e instanceof Error ? e.message : "저장 실패");
    } finally {
      setSubmitting(false);
    }
  }

  const filteredFolders = folders.filter(
    (f) => f.para_category === selectedPara,
  );

  const paraChips = PARA_ORDER.map((cat) => ({
    key: cat as ParaTab,
    letter: PARA_TOKENS[cat].letter,
    label: PARA_TOKENS[cat].label,
    fg: PARA_TOKENS[cat].fg,
    bg: PARA_TOKENS[cat].bg,
  }));

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(nextOpen) => {
        onOpenChange(nextOpen);
        if (!nextOpen) {
          setTimeout(resetAll, 200);
        }
      }}
    >
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed inset-x-0 bottom-0 z-50 mx-auto max-h-[92svh] w-full max-w-md overflow-y-auto rounded-t-2xl bg-background p-5 pb-[calc(env(safe-area-inset-bottom)+20px)] shadow-xl">
          <div className="flex items-center justify-between pb-3">
            <div className="flex items-center gap-2">
              {step === "folder" && (
                <button
                  type="button"
                  aria-label="뒤로"
                  onClick={() => setStep("url")}
                  className="flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground hover:bg-accent"
                >
                  <ArrowLeft className="h-4 w-4" />
                </button>
              )}
              <Dialog.Title className="text-base font-semibold">
                {step === "url" ? "링크 붙여넣기" : "폴더에 저장"}
              </Dialog.Title>
            </div>
            <Dialog.Close
              render={
                <button
                  type="button"
                  aria-label="닫기"
                  className="flex h-8 w-8 items-center justify-center rounded-full text-muted-foreground hover:bg-accent"
                >
                  <X className="h-4 w-4" />
                </button>
              }
            />
          </div>

          {step === "url" ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void handleNext();
              }}
              className="space-y-3"
            >
              <Input
                autoFocus
                type="url"
                value={url}
                onChange={(e) => {
                  setUrl(e.target.value);
                  setDuplicate(null);
                }}
                placeholder="https://..."
                required
              />
              {duplicate && (
                <p className="border-l-2 border-amber-500 pl-2 text-xs text-amber-700">
                  이미 저장된 URL이에요. ID #{duplicate.id}
                </p>
              )}
              {error && (
                <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
                  {error}
                </p>
              )}
              <Button
                type="submit"
                disabled={!url.trim() || !isHttpUrl(url.trim())}
                className="w-full"
              >
                다음
              </Button>
            </form>
          ) : (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void handleSave();
              }}
              className="space-y-3"
            >
              <p className="truncate rounded-lg border bg-card/40 px-2.5 py-1.5 text-xs text-muted-foreground">
                {url}
              </p>
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <label className="text-xs text-muted-foreground">제목</label>
                  {metaLoading && (
                    <span className="flex items-center gap-1 text-[10px] text-muted-foreground">
                      <Loader2 className="h-3 w-3 animate-spin" />
                      가져오는 중…
                    </span>
                  )}
                </div>
                <Input
                  value={title}
                  onChange={(e) => {
                    titleDirtyRef.current = true;
                    setTitle(e.target.value);
                  }}
                  placeholder={
                    metaLoading
                      ? "페이지 제목을 가져오는 중…"
                      : "제목을 입력하세요"
                  }
                />
              </div>

              <div className="grid grid-cols-4 gap-1.5">
                {paraChips.map((chip) => {
                  const active = selectedPara === chip.key;
                  return (
                    <button
                      key={chip.key}
                      type="button"
                      onClick={() => {
                        setSelectedPara(chip.key);
                        setSelectedFolderId(null);
                        setShowNewFolder(false);
                      }}
                      title={chip.label}
                      className={cn(
                        "flex flex-col items-center justify-center gap-0.5 rounded-lg border py-1.5 transition-colors cursor-pointer",
                        active
                          ? "border-transparent"
                          : "border-border bg-card hover:bg-accent",
                      )}
                      style={
                        active
                          ? { backgroundColor: chip.bg, borderColor: chip.fg }
                          : undefined
                      }
                    >
                      <span
                        className="text-[13px] font-bold leading-none"
                        style={{
                          color: active
                            ? chip.fg
                            : "var(--muted-foreground)",
                        }}
                      >
                        {chip.letter}
                      </span>
                      <span
                        className={cn(
                          "text-[9px] leading-none tracking-tight",
                          active ? "" : "text-muted-foreground",
                        )}
                        style={active ? { color: chip.fg } : undefined}
                      >
                        {chip.label}
                      </span>
                    </button>
                  );
                })}
              </div>

              <div className="max-h-[200px] space-y-1 overflow-y-auto rounded-xl border bg-card/40 p-1.5">
                {foldersLoading ? (
                  <p className="px-2 py-2 text-xs italic text-muted-foreground">
                    불러오는 중…
                  </p>
                ) : (
                  <>
                    {filteredFolders.length === 0 && !showNewFolder && (
                      <p className="px-2 py-2 text-xs italic text-muted-foreground">
                        이 카테고리에 폴더가 없어요
                      </p>
                    )}
                    {filteredFolders.map((folder) => {
                      const selected = selectedFolderId === folder.id;
                      return (
                        <button
                          key={folder.id}
                          type="button"
                          onClick={() => setSelectedFolderId(folder.id)}
                          className={cn(
                            "flex w-full items-center gap-2 rounded-lg px-2.5 py-2 text-left text-xs transition-colors cursor-pointer",
                            selected
                              ? "bg-primary text-primary-foreground"
                              : "hover:bg-accent",
                          )}
                        >
                          <span className="flex-1 truncate font-medium">
                            {folder.name}
                          </span>
                          {selected && <Check className="h-3 w-3 shrink-0" />}
                        </button>
                      );
                    })}
                    {showNewFolder ? (
                      <div className="flex gap-1 pt-1">
                        <Input
                          value={newFolderName}
                          onChange={(e) => setNewFolderName(e.target.value)}
                          placeholder="새 폴더 이름"
                          autoFocus
                          className="h-7 text-xs"
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              e.preventDefault();
                              void handleCreateFolder();
                            }
                            if (e.key === "Escape") {
                              setShowNewFolder(false);
                              setNewFolderName("");
                            }
                          }}
                        />
                        <Button
                          type="button"
                          size="xs"
                          onClick={() => void handleCreateFolder()}
                          disabled={creatingFolder || !newFolderName.trim()}
                        >
                          {creatingFolder ? "…" : "생성"}
                        </Button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setShowNewFolder(true)}
                        className="flex w-full items-center gap-1.5 rounded-lg px-2.5 py-2 text-xs text-muted-foreground transition-colors hover:bg-accent hover:text-foreground cursor-pointer"
                      >
                        <FolderPlus className="h-3 w-3" />
                        <span>새 폴더 만들기</span>
                      </button>
                    )}
                  </>
                )}
              </div>

              <button
                type="button"
                onClick={() => setShowDetails((v) => !v)}
                className="flex w-full items-center justify-between rounded-lg px-2.5 py-2 text-xs text-muted-foreground hover:bg-accent"
              >
                <span>메모 · 우선순위 추가</span>
                {showDetails ? (
                  <ChevronUp className="h-3 w-3" />
                ) : (
                  <ChevronDown className="h-3 w-3" />
                )}
              </button>
              {showDetails && (
                <div className="space-y-2">
                  <Input
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="메모 (선택)"
                  />
                  <div className="flex gap-1.5">
                    {PRIORITY_OPTIONS.map((opt) => (
                      <button
                        type="button"
                        key={opt.value}
                        onClick={() => setPriority(opt.value)}
                        className={cn(
                          "flex-1 rounded-md border py-2 text-xs",
                          priority === opt.value
                            ? "border-primary bg-primary text-primary-foreground"
                            : "border-border bg-card",
                        )}
                      >
                        {opt.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {duplicate && (
                <p className="border-l-2 border-amber-500 pl-2 text-xs text-amber-700">
                  이미 저장된 URL이에요. ID #{duplicate.id}
                </p>
              )}
              {error && (
                <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
                  {error}
                </p>
              )}

              <Button
                type="submit"
                disabled={
                  submitting || !title.trim() || !selectedFolderId
                }
                className="w-full"
              >
                {submitting ? "저장 중…" : "저장"}
              </Button>
            </form>
          )}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
