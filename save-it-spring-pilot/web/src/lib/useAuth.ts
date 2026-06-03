"use client";

import { useEffect, useState } from "react";
import { loadTokens, onAuthChange, type AuthTokens } from "./api";

export interface PortableSession {
  user: { id: string; userName: string };
  accessToken: string;
}

type AuthState =
  | { status: "loading"; session: null }
  | { status: "authenticated"; session: PortableSession }
  | { status: "anonymous"; session: null };

function toSession(t: AuthTokens): PortableSession {
  return {
    user: { id: String(t.userId), userName: t.userName },
    accessToken: t.accessToken,
  };
}

/**
 * JWT 기반 인증 상태 훅 — Supabase useAuth 시그니처 호환.
 * SSR 에선 항상 loading 으로 시작, 클라 mount 후 실제 상태 결정.
 */
export function useAuth(): AuthState {
  const [state, setState] = useState<AuthState>({
    status: "loading",
    session: null,
  });

  useEffect(() => {
    const t = loadTokens();
    setState(
      t
        ? { status: "authenticated", session: toSession(t) }
        : { status: "anonymous", session: null },
    );

    const unsub = onAuthChange((t) => {
      setState(
        t
          ? { status: "authenticated", session: toSession(t) }
          : { status: "anonymous", session: null },
      );
    });

    return () => {
      unsub();
    };
  }, []);

  return state;
}
