export default defineBackground(() => {
  // 콘텐츠 스크립트는 임의 페이지 오리진(example.com 등)에서 실행되어
  // fetch 가 그 오리진을 Origin 으로 붙여 CORS 에 걸린다.
  // 백그라운드 SW 의 fetch 는 Origin 헤더가 없어 CORS 무관 + host_permissions 로 허용됨.
  // → 모든 API 요청을 여기로 라우팅한다 (MV3 정석).
  browser.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
    if (msg?.type !== "API_FETCH") return false;
    (async () => {
      try {
        const res = await fetch(msg.url, msg.init ?? {});
        const body = await res.text();
        const headers: Record<string, string> = {};
        res.headers.forEach((v, k) => {
          headers[k] = v;
        });
        sendResponse({ ok: true, status: res.status, headers, body });
      } catch (e) {
        sendResponse({ ok: false, error: e instanceof Error ? e.message : String(e) });
      }
    })();
    return true; // async sendResponse 사용
  });
});
