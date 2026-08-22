#!/usr/bin/env python3
"""Load Kaggle winemag-data-130k-v2.csv into Cloudflare D1 (via wrangler).

Prerequisites:
  - CSV at scripts/catalog/data/winemag-data-130k-v2.csv (download from Kaggle)
  - wrangler logged in; CATALOG_DB created and bound as CATALOG_DB in wrangler.jsonc

Usage:
  python scripts/catalog/ingest-winemag.py
  python scripts/catalog/ingest-winemag.py --csv /path/to/winemag-data-130k-v2.csv --db vincent-catalog
"""
from __future__ import annotations

import argparse
import csv
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CSV = ROOT / "scripts" / "catalog" / "data" / "winemag-data-130k-v2.csv"
SCHEMA = """
DROP TABLE IF EXISTS winemag;
CREATE TABLE winemag (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  title TEXT NOT NULL,
  winery TEXT,
  region_1 TEXT,
  province TEXT,
  country TEXT,
  variety TEXT,
  color TEXT,
  points INTEGER,
  price REAL,
  description TEXT
);
CREATE INDEX idx_winemag_title ON winemag(title);
CREATE INDEX idx_winemag_winery ON winemag(winery);
CREATE INDEX idx_winemag_region ON winemag(region_1);
"""


def dedupe_rows(rows: list[dict]) -> list[dict]:
    seen: set[tuple] = set()
    out: list[dict] = []
    for r in rows:
        key = (r["title"], r["winery"], r["region_1"], r["variety"])
        if key in seen:
            continue
        seen.add(key)
        out.append(r)
    return out


def load_csv(path: Path) -> list[dict]:
    rows: list[dict] = []
    with path.open(encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        for row in reader:
            title = (row.get("title") or row.get("wine") or "").strip()
            if not title:
                continue
            color = "red"
            variety = (row.get("variety") or "").strip()
            vlow = variety.lower()
            if "white" in vlow or variety in {"Chardonnay", "Sauvignon Blanc", "Riesling"}:
                color = "white"
            rows.append(
                {
                    "title": title,
                    "winery": (row.get("winery") or "").strip(),
                    "region_1": (row.get("region_1") or row.get("region") or "").strip(),
                    "province": (row.get("province") or "").strip(),
                    "country": (row.get("country") or "").strip(),
                    "variety": variety,
                    "points": int(float(row.get("points") or 0)),
                    "price": float(row.get("price") or 0),
                    "description": (row.get("description") or "").strip(),
                    "color": color,
                }
            )
    return dedupe_rows(rows)


def sql_escape(s: str) -> str:
    return s.replace("'", "''")


def write_batches(rows: list[dict], batch_size: int = 200) -> list[Path]:
    files: list[Path] = []
    tmp = Path(tempfile.mkdtemp(prefix="winemag-d1-"))
    for i in range(0, len(rows), batch_size):
        chunk = rows[i : i + batch_size]
        values = []
        for r in chunk:
            values.append(
                "('{title}','{winery}','{region_1}','{province}','{country}','{variety}','{color}',{points},{price},'{description}')".format(
                    title=sql_escape(r["title"]),
                    winery=sql_escape(r["winery"]),
                    region_1=sql_escape(r["region_1"]),
                    province=sql_escape(r["province"]),
                    country=sql_escape(r["country"]),
                    variety=sql_escape(r["variety"]),
                    points=r["points"],
                    price=r["price"],
                    description=sql_escape(r["description"]),
                    color=sql_escape(r["color"]),
                )
            )
        sql = (
            "INSERT INTO winemag (title, winery, region_1, province, country, variety, color, points, price, description) VALUES "
            + ",".join(values)
            + ";"
        )
        p = tmp / f"batch-{i // batch_size}.sql"
        p.write_text(sql, encoding="utf-8")
        files.append(p)
    return files


def wrangler(args: list[str]) -> None:
    subprocess.run(["wrangler", *args], cwd=ROOT / "worker", check=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    parser.add_argument("--db", default="vincent-catalog")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.csv.exists():
        print(f"Missing CSV: {args.csv}", file=sys.stderr)
        print("Download winemag-data-130k-v2.csv from Kaggle into scripts/catalog/data/", file=sys.stderr)
        sys.exit(1)

    rows = load_csv(args.csv)
    print(f"Loaded {len(rows)} unique rows after dedup")

    if args.dry_run:
        print(json.dumps(rows[:3], indent=2))
        return

    schema_file = Path(tempfile.mkdtemp()) / "schema.sql"
    schema_file.write_text(SCHEMA, encoding="utf-8")
    wrangler(["d1", "execute", args.db, "--remote", "--file", str(schema_file)])

    for batch in write_batches(rows):
        wrangler(["d1", "execute", args.db, "--remote", "--file", str(batch)])

    print("D1 ingest complete")


if __name__ == "__main__":
    main()
