// End-to-end check against the LIVE Cloudflare Worker (vin-cent-ai).
//
// Unlike gemini-e2e.mjs (which calls Gemini directly), this hits the deployed
// Worker so it catches a bad *deploy*: a Worker that's down, or one whose
// GEMINI_API_KEY Worker Secret is missing/blank (the exact cause of the
// "expected OAuth 2 access token" / "Server missing GEMINI_API_KEY" failures).
//
// It needs no Firebase credentials: the Worker checks GEMINI_API_KEY presence
// BEFORE App Check / auth, so an unauthenticated /v1/generate tells us whether
// the key is configured server-side (401 = key present + auth enforced;
// 500 "Server missing GEMINI_API_KEY" = key absent on the Worker).
//
// Run:   npm run test:e2e            (uses the default URL below)
//        WORKER_URL=https://... npm run test:e2e

import { test } from "node:test";
import assert from "node:assert/strict";

const WORKER_URL = (
  process.env.WORKER_URL || "https://vin-cent-ai.ludovic-valente.workers.dev"
).replace(/\/+$/, "");

test(`live Worker /health is reachable (${WORKER_URL})`, async () => {
  let res;
  try {
    res = await fetch(`${WORKER_URL}/health`);
  } catch (e) {
    assert.fail(`Could not reach the Worker at ${WORKER_URL}: ${e instanceof Error ? e.message : e}`);
  }
  assert.equal(res.status, 200, `GET /health returned HTTP ${res.status} — Worker not deployed/healthy.`);

  const body = await res.json().catch(() => ({}));
  assert.equal(body.ok, true, `GET /health unexpected body: ${JSON.stringify(body)}`);
});

test("live Worker has a GEMINI_API_KEY configured", async () => {
  const res = await fetch(`${WORKER_URL}/v1/generate`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ prompt: "ping" }),
  });
  const text = await res.text();

  // 500 + this message is the Worker telling us its GEMINI_API_KEY secret is
  // missing/blank — i.e. the deploy that should have set it did not run/succeed.
  if (res.status === 500 || /missing\s+GEMINI_API_KEY/i.test(text)) {
    assert.fail(
      `Deployed Worker is missing its GEMINI_API_KEY secret (HTTP ${res.status}).\n` +
        `Redeploy the Worker so the secret is (re)uploaded.\n${text}`,
    );
  }

  // Auth is enforced, so an unauthenticated call is rejected *after* the key
  // check passes — proving the Worker is live and has a key. A 200 (auth off)
  // or an upstream 4xx/429 also means the key check was cleared.
  assert.ok(
    [200, 401, 403, 429].includes(res.status),
    `Unexpected status ${res.status} from /v1/generate: ${text.slice(0, 300)}`,
  );
});
