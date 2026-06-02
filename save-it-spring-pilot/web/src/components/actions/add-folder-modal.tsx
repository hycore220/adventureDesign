"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Dialog } from "@base-ui/react/dialog";
import { Plus, X } from "lucide-react";
import { createFolder, loadTokens } from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import type { ParaCategory } from "@/lib/types";

interface AddFolderModalProps {
  category: ParaCategory | null;
  userId: string;
  /** Spring 의 PARA 루트 폴더 id (parent). category 가 unassigned 가 아닐 때 필요. */
  parentId?: number;
}

export function AddFolderModal({ category, parentId }: AddFolderModalProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    if (!parentId || !category) {
      setError("PARA 루트 폴더 정보가 없어요");
      return;
    }
    const tokens = loadTokens();
    if (!tokens) {
      setError("로그인 정보가 만료됐어요");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      await createFolder(tokens.userName, name.trim(), parentId);
      setName("");
      setOpen(false);
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "생성 실패");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Trigger
        render={
          <button
            type="button"
            className="flex w-full items-center justify-center gap-2 rounded-xl border border-dashed bg-card/50 px-4 py-3 text-sm text-muted-foreground transition-colors hover:bg-accent"
          >
            <Plus className="h-4 w-4" />
            새 폴더 만들기
          </button>
        }
      />
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed inset-x-0 bottom-0 z-50 rounded-t-2xl bg-background p-5 pb-[calc(env(safe-area-inset-bottom)+20px)] shadow-xl sm:inset-x-auto sm:bottom-auto sm:left-1/2 sm:top-1/2 sm:w-full sm:max-w-sm sm:-translate-x-1/2 sm:-translate-y-1/2 sm:rounded-2xl">
          <div className="flex items-center justify-between pb-3">
            <Dialog.Title className="text-base font-semibold">
              새 폴더
            </Dialog.Title>
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
          <form onSubmit={handleSubmit} className="space-y-3">
            <Input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="폴더 이름"
              required
            />
            {error && (
              <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
                {error}
              </p>
            )}
            <Button
              type="submit"
              disabled={submitting || !name.trim()}
              className="w-full"
            >
              {submitting ? "생성 중…" : "생성"}
            </Button>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
