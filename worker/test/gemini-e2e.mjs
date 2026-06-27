// End-to-end check that the Gemini API key is real and accepted by Google.
//
// This hits the live generativelanguage endpoint exactly the way the Worker
// does (key as the ?key= query param). A blank or wrong key makes Gemini reply
// with "expected OAuth 2 access token" — the very failure we want CI to catch
// before deploying the Worker (or before shipping a stale GitHub secret).
//
// Run:   GEMINI_API_KEY=... npm run test:e2e   (from worker/)
// The key is read from env first, then worker/.dev.vars, then ../local.properties
// so it also works from a plain local checkout.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));

function fromPropsFile(path, key) {
  try {
    const text = readFileSync(path, "utf8");
    for (const raw of text.split("\n")) {
      const line = raw.trim();
      if (!line || line.startsWith("#")) continue;
      const eq = line.indexOf("=");
      if (eq === -1) continue;
      if (line.slice(0, eq).trim() === key) {
        return line.slice(eq + 1).trim();
      }
    }
  } catch {
    // file absent — ignore
  }
  return "";
}

function resolveKey() {
  return (
    process.env.GEMINI_API_KEY?.trim() ||
    fromPropsFile(join(HERE, "..", ".dev.vars"), "GEMINI_API_KEY") ||
    fromPropsFile(join(HERE, "..", "..", "local.properties"), "GEMINI_API_KEY") ||
    ""
  );
}

const MODEL = process.env.GEMINI_MODEL?.trim() || "gemini-flash-latest";
const API_KEY = resolveKey();

test("GEMINI_API_KEY is present", () => {
  assert.notEqual(
    API_KEY,
    "",
    "No Gemini key found. Set GEMINI_API_KEY env var, or add it to worker/.dev.vars or local.properties.",
  );
});

test("Gemini accepts the key on a live generateContent call", async (t) => {
  if (!API_KEY) {
    t.skip("no key resolved (see previous test)");
    return;
  }

  const endpoint =
    `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent` +
    `?key=${encodeURIComponent(API_KEY)}`;

  const body = JSON.stringify({
    contents: [{ parts: [{ text: "Reply with the single word: ok" }] }],
    generationConfig: { responseMimeType: "text/plain" },
  });

  const res = await fetch(endpoint, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body,
  });

  const raw = await res.text();

  if (!res.ok) {
    let detail = raw;
    try {
      detail = JSON.stringify(JSON.parse(raw).error ?? JSON.parse(raw), null, 2);
    } catch {
      // keep raw text
    }
    assert.fail(
      `Gemini rejected the key (HTTP ${res.status}, model ${MODEL}).\n` +
        `A 400/403 mentioning "expected OAuth 2 access token" or "API key not valid" ` +
        `means the key is blank, wrong, or lacks the Generative Language API.\n${detail}`,
    );
  }

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    assert.fail(`Gemini returned non-JSON 200 body: ${raw.slice(0, 300)}`);
  }

  const text = parsed?.candidates?.[0]?.content?.parts?.[0]?.text;
  assert.ok(
    typeof text === "string" && text.length > 0,
    `Gemini 200 but no candidate text returned: ${raw.slice(0, 300)}`,
  );
});
