import { defineConfig } from "wxt";

export default defineConfig({
  modules: ["@wxt-dev/module-react"],
  manifest: {
    permissions: ["storage", "alarms", "activeTab"],
    // Spring 백엔드로 직접 호출 — 로컬 개발은 localhost:8080,
    // 운영은 Fly.io / 본인 도메인을 추가 등록.
    host_permissions: [
      "http://localhost:8080/*",
      "http://127.0.0.1:8080/*",
      "https://save-it-pilot.fly.dev/*",
    ],
  },
});
