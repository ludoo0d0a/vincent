// Gemini proxy Worker for the Vincent app.
//
// The Android client POSTs /v1/generate with a small payload and two headers:
//   X-Firebase-AppCheck: <App Check token>      (blocks non-app callers)
//   Authorization: Bearer <Firebase ID token>   (identifies the user → quota)
//
// The Worker verifies both, enforces a per-user daily quota and a response cache
// in KV, then forwards to Gemini using the GEMINI_API_KEY Worker Secret and
// returns the raw Gemini generateContent JSON so the client parsing is unchanged.

export interface Env {
  AI_KV: KVNamespace;
  GEMINI_API_KEY: string;
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  WEB_CLIENT_ID: string;
  GEMINI_MODEL: string;
  DAILY_QUOTA: string;
  CACHE_TTL_SECONDS: string;
  ENFORCE_APP_CHECK: string;
  ENFORCE_AUTH: string;
}

interface GeneratePayload {
  prompt?: string;
  imageB64?: string | null;
  responseMimeType?: string;
  cacheable?: boolean;
}

const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, model: env.GEMINI_MODEL || "gemini-flash-latest" });
    }

    if (request.method !== "POST" || url.pathname !== "/v1/generate") {
      return json({ error: { message: "Not found", status: "NOT_FOUND" } }, 404);
    }

    if (!env.GEMINI_API_KEY) {
      return json({ error: { message: "Server missing GEMINI_API_KEY", status: "FAILED_PRECONDITION" } }, 500);
    }

    // 1. App Check — proves the call comes from a genuine, unmodified app build.
    if (truthy(env.ENFORCE_APP_CHECK)) {
      const appCheck = request.headers.get("X-Firebase-AppCheck");
      if (!appCheck) return json({ error: { message: "Missing App Check token", status: "UNAUTHENTICATED" } }, 401);
      try {
        await verifyAppCheck(appCheck, env);
      } catch (e) {
        return json({ error: { message: `App Check rejected: ${msg(e)}`, status: "UNAUTHENTICATED" } }, 401);
      }
    }

    // 2. Firebase ID token — identifies the signed-in user (uid drives the quota).
    let uid = "anon";
    if (truthy(env.ENFORCE_AUTH)) {
      const authz = request.headers.get("Authorization") || "";
      const token = authz.startsWith("Bearer ") ? authz.slice(7).trim() : "";
      if (!token) return json({ error: { message: "Missing Authorization bearer token", status: "UNAUTHENTICATED" } }, 401);
      try {
        uid = await verifyFirebaseIdToken(token, env);
      } catch (e) {
        return json({ error: { message: `ID token rejected: ${msg(e)}`, status: "UNAUTHENTICATED" } }, 401);
      }
    }

    // 3. Parse the request payload.
    let payload: GeneratePayload;
    try {
      payload = (await request.json()) as GeneratePayload;
    } catch {
      return json({ error: { message: "Invalid JSON body", status: "INVALID_ARGUMENT" } }, 400);
    }
    const prompt = (payload.prompt || "").trim();
    if (!prompt) return json({ error: { message: "Empty prompt", status: "INVALID_ARGUMENT" } }, 400);
    const imageB64 = payload.imageB64 || null;
    const responseMimeType = payload.responseMimeType || "application/json";

    // 4. Per-user daily quota (soft; KV is eventually consistent).
    const quota = parseInt(env.DAILY_QUOTA || "0", 10);
    if (quota > 0 && uid !== "anon") {
      const key = `quota:${uid}:${dayStamp()}`;
      const used = parseInt((await env.AI_KV.get(key)) || "0", 10);
      if (used >= quota) {
        return json({ error: { message: "Daily AI quota exceeded", status: "RESOURCE_EXHAUSTED" } }, 429);
      }
      // Increment first so a slow Gemini call can't be hammered in parallel.
      await env.AI_KV.put(key, String(used + 1), { expirationTtl: 60 * 60 * 48 });
    }

    // 5. Cache (text-only requests are deterministic enough to reuse).
    const cacheTtl = parseInt(env.CACHE_TTL_SECONDS || "0", 10);
    const cacheable = imageB64 === null && payload.cacheable !== false && cacheTtl > 0;
    const model = env.GEMINI_MODEL || "gemini-flash-latest";
    let cacheKey = "";
    if (cacheable) {
      cacheKey = `cache:${model}:${await sha256Hex(`${responseMimeType}\n${prompt}`)}`;
      const hit = await env.AI_KV.get(cacheKey);
      if (hit) return new Response(hit, { headers: { ...JSON_HEADERS, "x-cache": "HIT" } });
    }

    // 6. Forward to Gemini.
    const parts: unknown[] = [{ text: prompt }];
    if (imageB64) parts.push({ inline_data: { mime_type: "image/jpeg", data: imageB64 } });
    const geminiBody = JSON.stringify({
      contents: [{ parts }],
      generationConfig: { responseMimeType },
    });
    const endpoint =
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent` +
      `?key=${env.GEMINI_API_KEY}`;

    let upstream: Response;
    try {
      upstream = await fetch(endpoint, {
        method: "POST",
        headers: JSON_HEADERS,
        body: geminiBody,
      });
    } catch (e) {
      return json({ error: { message: `Upstream fetch failed: ${msg(e)}`, status: "UNAVAILABLE" } }, 502);
    }

    const text = await upstream.text();
    // 7. Forward Gemini's status + body verbatim so the client's existing
    //    httpFailMessage(code, body) mapping (403/404/429/4xx) keeps working.
    if (!upstream.ok) {
      return new Response(text, { status: upstream.status, headers: JSON_HEADERS });
    }
    if (cacheable && cacheKey) {
      await env.AI_KV.put(cacheKey, text, { expirationTtl: cacheTtl });
    }
    return new Response(text, { headers: { ...JSON_HEADERS, "x-cache": "MISS" } });
  },
} satisfies ExportedHandler<Env>;

// ---- helpers ---------------------------------------------------------------

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
}

function truthy(v: string | undefined): boolean {
  return (v || "").toLowerCase() === "true";
}

function msg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function dayStamp(): string {
  return new Date().toISOString().slice(0, 10).replace(/-/g, "");
}

async function sha256Hex(input: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// ---- JWT verification ------------------------------------------------------

interface JwtParts {
  header: Record<string, unknown>;
  payload: Record<string, unknown>;
}

function b64urlToBytes(s: string): Uint8Array {
  let t = s.replace(/-/g, "+").replace(/_/g, "/");
  const pad = t.length % 4;
  if (pad) t += "=".repeat(4 - pad);
  const bin = atob(t);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlToString(s: string): string {
  return new TextDecoder().decode(b64urlToBytes(s));
}

async function verifyRs256(
  token: string,
  resolveKey: (kid: string) => Promise<CryptoKey>,
): Promise<JwtParts> {
  const segments = token.split(".");
  if (segments.length !== 3) throw new Error("malformed token");
  const [h, p, s] = segments;
  let header: Record<string, unknown>;
  let payload: Record<string, unknown>;
  try {
    header = JSON.parse(b64urlToString(h)) as Record<string, unknown>;
    payload = JSON.parse(b64urlToString(p)) as Record<string, unknown>;
  } catch {
    throw new Error("malformed token");
  }
  const kid = String(header.kid || "");
  if (!kid) throw new Error("missing kid");
  const key = await resolveKey(kid);
  const ok = await crypto.subtle.verify(
    { name: "RSASSA-PKCS1-v1_5" },
    key,
    b64urlToBytes(s),
    new TextEncoder().encode(`${h}.${p}`),
  );
  if (!ok) throw new Error("bad signature");
  const now = Math.floor(Date.now() / 1000);
  const exp = Number(payload.exp || 0);
  if (exp && now > exp + 60) throw new Error("expired");
  return { header, payload };
}

// In-memory key caches (per isolate). Respect upstream cache-control max-age.
const jwksCache = new Map<string, { exp: number; keys: JsonWebKey[] }>();
const certCache = new Map<string, { exp: number; certs: Record<string, string> }>();

function maxAgeMs(res: Response, fallback: number): number {
  const m = /max-age=(\d+)/.exec(res.headers.get("cache-control") || "");
  return m ? parseInt(m[1], 10) * 1000 : fallback;
}

async function verifyAppCheck(token: string, env: Env): Promise<void> {
  const url = "https://firebaseappcheck.googleapis.com/v1/jwks";
  const { payload } = await verifyRs256(token, async (kid) => {
    let entry = jwksCache.get(url);
    if (!entry || entry.exp < Date.now()) {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`jwks ${res.status}`);
      const body = (await res.json()) as { keys: JsonWebKey[] };
      entry = { exp: Date.now() + maxAgeMs(res, 3600_000), keys: body.keys };
      jwksCache.set(url, entry);
    }
    const jwk = entry.keys.find((k) => (k as { kid?: string }).kid === kid);
    if (!jwk) throw new Error("appcheck kid not found");
    return crypto.subtle.importKey("jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  });
  const num = env.FIREBASE_PROJECT_NUMBER;
  const iss = String(payload.iss || "");
  if (iss !== `https://firebaseappcheck.googleapis.com/${num}`) throw new Error("bad iss");
  const aud = payload.aud;
  const audList = Array.isArray(aud) ? aud.map(String) : [String(aud)];
  if (!audList.includes(`projects/${num}`) && !audList.includes(`projects/${env.FIREBASE_PROJECT_ID}`)) {
    throw new Error("bad aud");
  }
}

async function verifyFirebaseIdToken(token: string, env: Env): Promise<string> {
  const url = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
  const { payload } = await verifyRs256(token, async (kid) => {
    let entry = certCache.get(url);
    if (!entry || entry.exp < Date.now()) {
      const res = await fetch(url);
      if (!res.ok) throw new Error(`certs ${res.status}`);
      const certs = (await res.json()) as Record<string, string>;
      entry = { exp: Date.now() + maxAgeMs(res, 3600_000), certs };
      certCache.set(url, entry);
    }
    const pem = entry.certs[kid];
    if (!pem) throw new Error("id kid not found");
    return crypto.subtle.importKey("spki", spkiFromPem(pem), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  });
  const pid = env.FIREBASE_PROJECT_ID;
  if (String(payload.iss || "") !== `https://securetoken.google.com/${pid}`) throw new Error("bad iss");
  if (String(payload.aud || "") !== pid) throw new Error("bad aud");
  const sub = String(payload.sub || "");
  if (!sub) throw new Error("missing sub");
  return sub;
}

// ---- minimal X.509 → SPKI extraction (for securetoken certs) ---------------
// Web Crypto cannot import an X.509 certificate directly, so we walk the DER and
// pull out the SubjectPublicKeyInfo (the SEQUENCE whose algorithm OID is rsaEncryption).

function spkiFromPem(pem: string): Uint8Array {
  const b64 = pem.replace(/-----BEGIN CERTIFICATE-----/g, "")
    .replace(/-----END CERTIFICATE-----/g, "")
    .replace(/\s+/g, "");
  // b64urlToBytes also accepts standard base64 (its +// chars are left untouched).
  const der = b64urlToBytes(b64);
  const spki = findSpki(der);
  if (!spki) throw new Error("SPKI not found in certificate");
  return spki;
}

interface Tlv { tag: number; valueStart: number; end: number; start: number; }

function readTlv(buf: Uint8Array, offset: number): Tlv {
  const tag = buf[offset];
  let len = buf[offset + 1];
  let cursor = offset + 2;
  if (len & 0x80) {
    const n = len & 0x7f;
    len = 0;
    for (let i = 0; i < n; i++) len = (len << 8) | buf[cursor++];
  }
  return { tag, start: offset, valueStart: cursor, end: cursor + len };
}

const RSA_OID = [0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01];

function findSpki(der: Uint8Array): Uint8Array | null {
  const walk = (start: number, end: number): Uint8Array | null => {
    let off = start;
    while (off < end) {
      const t = readTlv(der, off);
      if (t.tag === 0x30) {
        if (isSpki(der, t)) return der.slice(t.start, t.end);
        const found = walk(t.valueStart, t.end);
        if (found) return found;
      }
      off = t.end;
    }
    return null;
  };
  return walk(0, der.length);
}

function isSpki(der: Uint8Array, seq: Tlv): boolean {
  const algid = readTlv(der, seq.valueStart);
  if (algid.tag !== 0x30) return false;
  const oid = readTlv(der, algid.valueStart);
  if (oid.tag !== 0x06 || oid.end - oid.valueStart !== RSA_OID.length) return false;
  for (let i = 0; i < RSA_OID.length; i++) if (der[oid.valueStart + i] !== RSA_OID[i]) return false;
  const bitstr = readTlv(der, algid.end);
  return bitstr.tag === 0x03;
}
