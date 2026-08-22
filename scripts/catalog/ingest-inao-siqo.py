#!/usr/bin/env python3
"""Build appellations-fr.json from INAO SIQO referentiel (data.gouv.fr).

Filters wine-related AOC/AOP/IGP rows. Output is imported via Appellations management screen.

Licence: Licence Ouverte 2.0 (INAO / data.gouv.fr)

Usage:
  python scripts/catalog/ingest-inao-siqo.py
  python scripts/catalog/ingest-inao-siqo.py --csv /path/to/siqo.csv
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "scripts" / "catalog" / "out" / "appellations-fr.json"

# Resource page — update URL if data.gouv publishes a new CSV attachment.
SIQO_DATASET_PAGE = "https://www.data.gouv.fr/api/1/datasets/referentiel-des-produits-sous-signe-officiel-didentification-de-la-qualite-et-de-lorigine-siqo/"

WINE_KEYWORDS = re.compile(
    r"\b(vin|wine|vitic|champagne|crémant|cremant|mousseux|liquoreux|muté|mute)\b",
    re.I,
)


def slug(name: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")


def find_csv_url() -> str | None:
    try:
        with urllib.request.urlopen(SIQO_DATASET_PAGE, timeout=60) as resp:
            data = json.loads(resp.read().decode())
        for res in data.get("resources", []):
            title = (res.get("title") or "").lower()
            fmt = (res.get("format") or "").lower()
            if "csv" in fmt or title.endswith(".csv") or "csv" in title:
                return res.get("url")
    except Exception as e:
        print(f"Could not resolve SIQO CSV from data.gouv: {e}", file=sys.stderr)
    return None


def parse_csv(text: str) -> list[dict]:
    reader = csv.DictReader(text.splitlines())
    out: list[dict] = []
    for row in reader:
        joined = " ".join(str(v) for v in row.values() if v)
        if not WINE_KEYWORDS.search(joined):
            continue
        name = (
            row.get("nom_officiel")
            or row.get("Nom officiel")
            or row.get("appellation")
            or row.get("libelle")
            or row.get("nom")
            or ""
        ).strip()
        if not name:
            continue
        sign = (row.get("signe") or row.get("Signe") or row.get("type") or "").strip()
        category = (row.get("categorie") or row.get("Catégorie") or row.get("famille") or "").strip()
        dept = (row.get("departement") or row.get("Département") or row.get("dt") or "").strip()
        inao_id_raw = row.get("id") or row.get("id_app") or row.get("identifiant") or "0"
        try:
            inao_id = int(str(inao_id_raw).strip() or "0")
        except ValueError:
            inao_id = 0
        stable = f"inao-{inao_id}" if inao_id > 0 else f"inao-{slug(name)}"
        geo = f"{inao_id}.geojson" if inao_id > 0 else ""
        out.append(
            {
                "id": stable,
                "name": name,
                "sign": sign,
                "category": category,
                "department": dept,
                "inaoId": inao_id,
                "geoAsset": geo,
            }
        )
    # Dedup by id
    seen: set[str] = set()
    deduped: list[dict] = []
    for a in out:
        if a["id"] in seen:
            continue
        seen.add(a["id"])
        deduped.append(a)
    return deduped


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", type=Path, help="Local SIQO CSV export")
    parser.add_argument("--out", type=Path, default=OUT)
    args = parser.parse_args()

    if args.csv:
        text = args.csv.read_text(encoding="utf-8", errors="replace")
    else:
        url = find_csv_url()
        if not url:
            print("Provide --csv or fix SIQO_DATASET_PAGE resolver", file=sys.stderr)
            sys.exit(1)
        print(f"Downloading {url}")
        with urllib.request.urlopen(url, timeout=120) as resp:
            text = resp.read().decode("utf-8", errors="replace")

    appellations = parse_csv(text)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps({"appellations": appellations}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {len(appellations)} appellations ? {args.out}")


if __name__ == "__main__":
    main()
