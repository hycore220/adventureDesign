/* eslint-disable no-restricted-globals */

self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

async function broadcast(msg) {
  const clients = await self.clients.matchAll({ type: "window", includeUncontrolled: true });
  for (const c of clients) c.postMessage(msg);
}

self.addEventListener("push", (event) => {
  let payload = { title: "save-it", body: "(no body)", url: "/today" };
  let rawText = null;
  try {
    if (event.data) {
      rawText = event.data.text();
      try {
        payload = { ...payload, ...JSON.parse(rawText) };
      } catch (parseErr) {
        // JSON 아니면 본문에 그대로 사용
        payload.body = rawText;
      }
    }
  } catch (e) {
    payload.body = "[parse err] " + String(e);
  }

  event.waitUntil((async () => {
    await broadcast({ type: "push-received", payload, rawText });
    await self.registration.showNotification(payload.title, {
      body: payload.body,
      icon: "/icon-192.png",
      badge: "/icon-192.png",
      data: { url: payload.url },
    });
  })());
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const targetUrl = (event.notification.data && event.notification.data.url) || "/today";
  event.waitUntil(
    self.clients.matchAll({ type: "window" }).then((clientList) => {
      const existing = clientList.find((c) => c.url.includes(targetUrl));
      if (existing) return existing.focus();
      return self.clients.openWindow(targetUrl);
    }),
  );
});
