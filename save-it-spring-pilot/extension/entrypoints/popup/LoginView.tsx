import { Bookmark } from "lucide-react";
import { Button } from "../../components/ui/button";
import { Input } from "../../components/ui/input";
import { Label } from "../../components/ui/label";
import { login, signup } from "../../lib/api";

type Mode = "login" | "signup";

export function LoginView() {
  const [mode, setMode] = useState<Mode>("login");
  const [userName, setUserName] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      if (mode === "signup") {
        await signup(userName, password);
      } else {
        await login(userName, password);
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "알 수 없는 오류";
      // Spring 응답이 한글 메시지를 직접 담아 돌려주므로 그대로 표시
      setError(stripJsonNoise(msg));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="px-5 pt-6 pb-6 space-y-5">
      <header className="space-y-3">
        <div className="flex items-center gap-2">
          <span
            className="flex h-9 w-9 items-center justify-center rounded-xl"
            style={{ backgroundColor: "var(--color-para-project-fg)" }}
          >
            <Bookmark className="h-4 w-4 text-white" />
          </span>
          <div>
            <div className="text-base font-semibold leading-none">Save It</div>
            <div className="mt-1 text-[11px] text-muted-foreground">
              저장한 링크를 다시 보게 만드는 서비스
            </div>
          </div>
        </div>
      </header>

      <form onSubmit={handleSubmit} className="space-y-3">
        <div className="space-y-1.5">
          <Label htmlFor="userName">아이디</Label>
          <Input
            id="userName"
            type="text"
            value={userName}
            onChange={(e) => setUserName(e.target.value)}
            placeholder="username"
            required
            autoFocus
            autoComplete="username"
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="password">비밀번호</Label>
          <Input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="••••••••"
            required
            autoComplete={mode === "signup" ? "new-password" : "current-password"}
          />
        </div>

        {error && (
          <p className="border-l-2 border-destructive pl-2 text-xs text-destructive">
            {error}
          </p>
        )}

        <Button type="submit" disabled={loading} className="w-full">
          {loading
            ? mode === "signup"
              ? "가입 중…"
              : "로그인 중…"
            : mode === "signup"
              ? "회원가입"
              : "로그인"}
        </Button>
      </form>

      <p className="text-[11px] leading-snug text-muted-foreground text-center">
        {mode === "login" ? (
          <>
            계정이 없나요?{" "}
            <button
              type="button"
              className="text-foreground underline underline-offset-2 cursor-pointer"
              onClick={() => {
                setMode("signup");
                setError("");
              }}
            >
              회원가입
            </button>
          </>
        ) : (
          <>
            이미 계정이 있나요?{" "}
            <button
              type="button"
              className="text-foreground underline underline-offset-2 cursor-pointer"
              onClick={() => {
                setMode("login");
                setError("");
              }}
            >
              로그인
            </button>
          </>
        )}
      </p>
    </div>
  );
}

/** Spring ErrorAdvice 가 JSON 으로 메시지를 돌려줄 때 한 줄로 정리. */
function stripJsonNoise(raw: string): string {
  try {
    const obj = JSON.parse(raw);
    if (typeof obj?.message === "string") return obj.message;
    if (typeof obj?.error === "string") return obj.error;
  } catch {
    /* fall through */
  }
  return raw;
}
