#!/usr/bin/env python3
"""Fetch VIVC passport rows and emit grapes-full.json + optional grapes-popular.json.

Requires: pip install vivcpy

Usage:
  python scripts/catalog/ingest-vivc.py
  python scripts/catalog/ingest-vivc.py --popular-only --out composeApp/src/commonMain/composeResources/files/grapes-popular.json

Citation: Röckel et al., Vitis International Variety Catalogue — https://www.vivc.de/
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "scripts" / "catalog" / "out"
DEFAULT_FULL = OUT_DIR / "grapes-full.json"
DEFAULT_POPULAR = ROOT / "composeApp" / "src" / "commonMain" / "composeResources" / "files" / "grapes-popular.json"

POPULAR_NAMES = {
    "Cabernet Sauvignon", "Merlot", "Pinot Noir", "Syrah", "Grenache Noir", "Grenache",
    "Chardonnay", "Sauvignon Blanc", "Chenin Blanc", "Chenin", "Riesling", "Gamay", "Viognier",
    "Carignan", "Mourvèdre", "Cinsault", "Sémillon", "Semillon", "Muscat Blanc à Petits Grains",
    "Malbec", "Tempranillo", "Nebbiolo", "Sangiovese", "Vermentino", "Cabernet Franc",
}


def normalize_color(raw: str) -> str:
    v = (raw or "").lower()
    if "white" in v or "blanc" in v:
        return "white"
    if "rose" in v or "ros" in v or "pink" in v:
        return "rose"
    if "red" in v or "noir" in v:
        return "red"
    return v.strip()


def row_to_grape(v) -> dict:
    return {
        "id": f"vivc-{v.variety_number_vivc}",
        "name": v.prime_name.strip(),
        "color": normalize_color(getattr(v, "color_of_berry_skin", "") or ""),
        "vivcNumber": int(v.variety_number_vivc),
        "country": (getattr(v, "country_or_region_of_origin_of_the_variety", "") or "").strip(),
        "aliases": [],
    }


def fetch_all() -> list[dict]:
    try:
        from vivcpy.search import PassportDataSearch, PassportDataSearchParams
        from vivcpy.enums import Species
    except ImportError:
        print("Install vivcpy: pip install vivcpy", file=sys.stderr)
        sys.exit(1)

    params = PassportDataSearchParams(species=Species.VITIS_VINIFERA_SUBSP_SATIVA)
    return [row_to_grape(v) for v in PassportDataSearch(params)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=DEFAULT_FULL)
    parser.add_argument("--popular-only", action="store_true")
    parser.add_argument("--popular-out", type=Path, default=DEFAULT_POPULAR)
    args = parser.parse_args()

    print("Fetching VIVC passport data…")
    grapes = fetch_all()
    print(f"Fetched {len(grapes)} varieties")

    if args.popular_only:
        popular = [g for g in grapes if g["name"] in POPULAR_NAMES][:500]
        payload = {"grapes": popular}
        args.popular_out.parent.mkdir(parents=True, exist_ok=True)
        args.popular_out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Wrote {len(popular)} popular grapes ? {args.popular_out}")
        return

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps({"grapes": grapes}, ensure_ascii=False), encoding="utf-8")
    print(f"Wrote full dump ? {args.out}")

    popular = [g for g in grapes if g["name"] in POPULAR_NAMES][:500]
    if popular:
        args.popular_out.parent.mkdir(parents=True, exist_ok=True)
        args.popular_out.write_text(json.dumps({"grapes": popular}, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Wrote {len(popular)} popular grapes ? {args.popular_out}")


if __name__ == "__main__":
    main()
