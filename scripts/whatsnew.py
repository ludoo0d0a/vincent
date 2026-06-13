#!/usr/bin/env python3
"""Generate Play Store release-note files from playstore/whatsnew.xml.

Writes playstore/whatsnew/whatsnew-<locale> (plain text, <=500 chars) which the
release workflow passes to r0adkll/upload-google-play (whatsNewDirectory).

Usage: scripts/whatsnew.py [versionCode]
  - with a versionCode: emit that release's notes
  - without (or no match): emit the latest (first) <release>'s notes
"""
import os
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "playstore", "whatsnew.xml")
OUT = os.path.join(ROOT, "playstore", "whatsnew")


def main() -> None:
    want = sys.argv[1] if len(sys.argv) > 1 else None
    releases = ET.parse(SRC).getroot().findall("release")
    if not releases:
        sys.exit("whatsnew.xml: no <release> found")

    rel = None
    if want:
        rel = next((r for r in releases if r.get("versionCode") == str(want)), None)
    if rel is None:
        rel = releases[0]  # latest

    os.makedirs(OUT, exist_ok=True)
    for loc in rel.findall("locale"):
        code = loc.get("code")
        text = (loc.text or "").strip()
        if len(text) > 500:
            print(f"WARN: {code} notes are {len(text)} chars (>500) — truncating.", file=sys.stderr)
            text = text[:500]
        path = os.path.join(OUT, f"whatsnew-{code}")
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text + "\n")
        print(f"wrote {os.path.relpath(path, ROOT)} ({len(text)} chars) "
              f"for v{rel.get('versionName')} ({rel.get('versionCode')})")


if __name__ == "__main__":
    main()
