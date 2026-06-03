import { NextResponse } from "next/server";
import { lookup as dnsLookup } from "node:dns/promises";
import { isIP } from "node:net";

export const runtime = "nodejs";

const FETCH_TIMEOUT_MS = 6000;
const MAX_BYTES = 1024 * 1024;

type SuccessBody = { ok: true; title: string; host: string };
type FailureBody = { ok: false; reason: FailureReason };
type FailureReason =
  | "invalid_url"
  | "blocked_host"
  | "fetch_failed"
  | "timeout"
  | "not_html"
  | "no_title";

function fail(reason: FailureReason) {
  return NextResponse.json<FailureBody>({ ok: false, reason });
}

function isPrivateIp(ip: string): boolean {
  const v = isIP(ip);
  if (v === 4) {
    const [a, b] = ip.split(".").map(Number);
    if (a === 10) return true;
    if (a === 127) return true;
    if (a === 169 && b === 254) return true;
    if (a === 172 && b >= 16 && b <= 31) return true;
    if (a === 192 && b === 168) return true;
    if (a === 0) return true;
    return false;
  }
  if (v === 6) {
    const lower = ip.toLowerCase();
    if (lower === "::1") return true;
    if (lower === "::") return true;
    if (lower.startsWith("fe80:")) return true;
    if (lower.startsWith("fc") || lower.startsWith("fd")) return true;
    if (lower.startsWith("::ffff:")) {
      return isPrivateIp(lower.slice("::ffff:".length));
    }
    return false;
  }
  return true;
}

const HTML_ENTITY_MAP: Record<string, string> = {
  amp: "&",
  lt: "<",
  gt: ">",
  quot: '"',
  apos: "'",
  nbsp: " ",
};

function decodeHtmlEntities(s: string): string {
  return s.replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (m, body) => {
    const lower = body.toLowerCase();
    if (lower.startsWith("#x")) {
      const code = parseInt(lower.slice(2), 16);
      return Number.isFinite(code) ? String.fromCodePoint(code) : m;
    }
    if (lower.startsWith("#")) {
      const code = parseInt(lower.slice(1), 10);
      return Number.isFinite(code) ? String.fromCodePoint(code) : m;
    }
    return HTML_ENTITY_MAP[lower] ?? m;
  });
}

function extractTitle(html: string): string | null {
  const og = html.match(
    /<meta[^>]+property\s*=\s*["']og:title["'][^>]*content\s*=\s*["']([^"']+)["']/i
  );
  if (og?.[1]) return decodeHtmlEntities(og[1]).trim();

  const ogReverse = html.match(
    /<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]*property\s*=\s*["']og:title["']/i
  );
  if (ogReverse?.[1]) return decodeHtmlEntities(ogReverse[1]).trim();

  const title = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i);
  if (title?.[1]) {
    const cleaned = decodeHtmlEntities(title[1]).replace(/\s+/g, " ").trim();
    if (cleaned) return cleaned;
  }
  return null;
}

export async function GET(request: Request) {
  try {
    return await handle(request);
  } catch (err) {
    console.error("[/api/metadata] unexpected error:", err);
    return fail("fetch_failed");
  }
}

async function handle(request: Request) {
  // 인증 검증은 Spring 백엔드 측에서 처리. 이 라우트는 og:title 만 뽑는 공개 유틸.
  // (악용 우려가 있으면 호출자가 Authorization 헤더 동봉 후 우리 Spring 측 metadata 엔드포인트로 옮기는 게 정석)
  const { searchParams } = new URL(request.url);
  const raw = searchParams.get("url");
  if (!raw) return fail("invalid_url");

  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    return fail("invalid_url");
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    return fail("invalid_url");
  }

  const hostname = parsed.hostname;
  if (!hostname) return fail("invalid_url");
  if (isIP(hostname)) {
    if (isPrivateIp(hostname)) return fail("blocked_host");
  } else {
    try {
      const { address } = await dnsLookup(hostname);
      if (isPrivateIp(address)) return fail("blocked_host");
    } catch {
      return fail("fetch_failed");
    }
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);

  try {
    let response: Response;
    try {
      response = await fetch(parsed.toString(), {
        method: "GET",
        redirect: "follow",
        signal: controller.signal,
        headers: {
          "User-Agent": "SaveItBot/1.0",
          Accept: "text/html,application/xhtml+xml",
        },
      });
    } catch (err) {
      if (err instanceof Error && err.name === "AbortError") return fail("timeout");
      return fail("fetch_failed");
    }

    if (!response.ok) return fail("fetch_failed");
    const contentType = response.headers.get("content-type") ?? "";
    if (!contentType.toLowerCase().includes("text/html")) return fail("not_html");
    if (!response.body) return fail("fetch_failed");

    const reader = response.body.getReader();
    const decoder = new TextDecoder("utf-8", { fatal: false });
    let html = "";
    let received = 0;
    try {
      while (received < MAX_BYTES) {
        const { value, done } = await reader.read();
        if (done) break;
        received += value.byteLength;
        html += decoder.decode(value, { stream: true });
        if (html.includes("</head>") || html.includes("</HEAD>")) break;
        if (received >= MAX_BYTES) break;
      }
    } catch (err) {
      if (err instanceof Error && err.name === "AbortError") return fail("timeout");
      return fail("fetch_failed");
    } finally {
      try {
        await reader.cancel();
      } catch {
        /* ignore */
      }
    }
    html += decoder.decode();

    const title = extractTitle(html);
    if (!title) return fail("no_title");

    const body: SuccessBody = { ok: true, title, host: parsed.hostname };
    return NextResponse.json(body, {
      headers: { "Cache-Control": "private, max-age=3600" },
    });
  } finally {
    clearTimeout(timer);
  }
}
