import { useAuth } from "../../lib/useAuth";
import { AppShell } from "./AppShell";
import { LoginView } from "./LoginView";

export default function Popup() {
  const auth = useAuth();
  const [tab, setTab] = useState<{ url: string; title: string } | null>(null);

  useEffect(() => {
    browser.tabs.query({ active: true, currentWindow: true }).then((tabs) => {
      const t = tabs[0];
      setTab({ url: t?.url ?? "", title: t?.title ?? "" });
    });
  }, []);

  return (
    <div className="w-[360px] bg-background text-foreground animate-fade-up">
      {(auth.status === "loading" || tab === null) && (
        <div className="flex items-center justify-center px-4 py-6">
          <span className="text-xs text-muted-foreground">불러오는 중…</span>
        </div>
      )}
      {auth.status === "anonymous" && tab !== null && <LoginView />}
      {auth.status === "authenticated" && tab !== null && (
        <AppShell
          userName={auth.session.user.userName}
          initialUrl={tab.url}
          initialTitle={tab.title}
          onSaved={() => window.close()}
        />
      )}
    </div>
  );
}
