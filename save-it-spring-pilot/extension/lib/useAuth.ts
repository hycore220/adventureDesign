import { loadTokens, onAuthChange, type AuthTokens } from "./api";

/**
 * JWT 기반 인증 상태 훅.
 *
 * Supabase 의 `useAuth` 시그니처와 호환되도록 동일한 union 형태를 유지.
 * `session` 필드는 호환 목적으로 `{ user: { id, userName } }` 모양만 제공.
 */
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

export function useAuth(): AuthState {
  const [state, setState] = useState<AuthState>({
    status: "loading",
    session: null,
  });

  useEffect(() => {
    let mounted = true;

    loadTokens().then((t) => {
      if (!mounted) return;
      setState(
        t
          ? { status: "authenticated", session: toSession(t) }
          : { status: "anonymous", session: null },
      );
    });

    const unsub = onAuthChange((t) => {
      setState(
        t
          ? { status: "authenticated", session: toSession(t) }
          : { status: "anonymous", session: null },
      );
    });

    return () => {
      mounted = false;
      unsub();
    };
  }, []);

  return state;
}
