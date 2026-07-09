import { spawnSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import assert from "node:assert/strict";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "../..");

test("WikipediaRegionsParser Kotlin unit tests", () => {
  const result = spawnSync(
    join(repoRoot, "gradlew"),
    [
      ":composeApp:testDebugUnitTest",
      "--tests",
      "fr.geoking.vincent.data.WikipediaRegionsParserTest",
    ],
    { cwd: repoRoot, encoding: "utf8" },
  );

  if (result.status !== 0) {
    process.stdout.write(result.stdout ?? "");
    process.stderr.write(result.stderr ?? "");
  }

  assert.equal(result.status, 0, "WikipediaRegionsParserTest failed");
});
