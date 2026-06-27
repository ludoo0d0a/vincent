# vin-cent-ai — Gemini proxy Worker

Keeps the Gemini API key off-device. The Android app calls this Worker instead of
Google directly; the Worker holds `GEMINI_API_KEY` as a Cloudflare Secret, verifies
Firebase **App Check** + a Firebase **ID token**, enforces a per-user daily quota and a
response cache in KV, then forwards to Gemini and returns the raw `generateContent` JSON.

## Endpoint

`POST /v1/generate`

Headers:

- `X-Firebase-AppCheck: <token>` — App Check token (blocks non-app callers)
- `Authorization: Bearer <token>` — Firebase ID token (identifies the user)

Body:

```json
{ "prompt": "…", "imageB64": null, "responseMimeType": "application/json", "cacheable": true }
```

Returns the verbatim Gemini response (or Gemini's error status/body on failure).
`GET /health` returns `{ ok: true }`.

## Config

- Non-secret: `worker/wrangler.jsonc` `vars` (`FIREBASE_PROJECT_ID`, `FIREBASE_PROJECT_NUMBER`,
  `WEB_CLIENT_ID`, `GEMINI_MODEL`, `DAILY_QUOTA`, `CACHE_TTL_SECONDS`, `ENFORCE_APP_CHECK`, `ENFORCE_AUTH`).
- Secret: `GEMINI_API_KEY` (set via `wrangler secret put`, done by `setup-ai-proxy.sh`).
- KV: binding `AI_KV` (quota counters + cache).

## Operate (from the app repo root)

```bash
./scripts/setup-ai-proxy.sh    # provision KV + secrets + first deploy (idempotent)
./scripts/deploy-ai-proxy.sh   # redeploy after code changes
npm --prefix worker run tail   # live logs
```

CI deploys on changes under `worker/**` via `.github/workflows/cloudflare-worker.yml`.
