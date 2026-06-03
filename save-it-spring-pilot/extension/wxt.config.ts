import { defineConfig } from "wxt";

export default defineConfig({
  modules: ["@wxt-dev/module-react"],
  manifest: {
    permissions: ["storage", "alarms", "activeTab"],
    // 팝업 없는 액션 — 클릭 시 background 의 onClicked 가 위젯 표시/숨김을 토글.
    action: { default_title: "Save It 위젯 켜기/끄기" },
    // Spring 백엔드로 직접 호출 — 로컬 개발은 localhost:8080,
    // 운영은 Fly.io / 본인 도메인을 추가 등록.
    host_permissions: [
      "http://localhost:8080/*",
      "http://127.0.0.1:8080/*",
      "https://save-it-pilot.fly.dev/*",
    ],
  },
});
