"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { AddLinkModal } from "./add-link-modal";

interface AddLinkFabProps {
  folderId: number;
  userId: string;
}

export function AddLinkFab({ folderId, userId }: AddLinkFabProps) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <div
        className="pointer-events-none fixed inset-x-0 z-30"
        style={{ bottom: `calc(env(safe-area-inset-bottom) + 80px)` }}
      >
        <div className="relative mx-auto w-full max-w-md">
          <button
            type="button"
            onClick={() => setOpen(true)}
            aria-label="새 링크 추가"
            className="pointer-events-auto absolute right-5 bottom-0 flex h-14 w-14 items-center justify-center rounded-full bg-[var(--color-para-project-fg)] text-white shadow-lg active:scale-95 transition-transform"
          >
            <Plus className="h-6 w-6" />
          </button>
        </div>
      </div>
      <AddLinkModal
        open={open}
        onOpenChange={setOpen}
        folderId={folderId}
        userId={userId}
      />
    </>
  );
}
