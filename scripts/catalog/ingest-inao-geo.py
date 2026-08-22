#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convert INAO parcellaire shapefile to per-appellation GeoJSON + map pack zip.

Requires: ogr2ogr (GDAL), zip

Source: https://www.data.gouv.fr/datasets/delimitation-parcellaire-des-aoc-viticoles-de-linao/
Licence: Licence Ouverte 2.0 - attribution INAO / IGN required in app.

Usage:
  python scripts/catalog/ingest-inao-geo.py --shp /path/to/parcellaire.shp
  python scripts/catalog/ingest-inao-geo.py --shp parcellaire.shp --upload-r2  # optional

Output:
  scripts/catalog/out/geojson/{id_app}.geojson
  scripts/catalog/out/appellations-map-fr.zip  (upload to R2 key appellations-map-fr.zip)
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "scripts" / "catalog" / "out"
GEO_DIR = OUT_DIR / "geojson"
ZIP_OUT = OUT_DIR / "appellations-map-fr.zip"


def run_ogr2ogr(shp: Path, geojson: Path) -> None:
    geojson.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ogr2ogr",
        "-f",
        "GeoJSON",
        str(geojson),
        str(shp),
        "-simplify",
        "0.0001",
    ]
    subprocess.run(cmd, check=True)


def split_by_id_app(combined: Path) -> int:
    data = json.loads(combined.read_text(encoding="utf-8"))
    features = data.get("features") or []
    GEO_DIR.mkdir(parents=True, exist_ok=True)
    count = 0
    by_id: dict[str, list] = {}
    for f in features:
        props = f.get("properties") or {}
        app_id = str(props.get("id_app") or props.get("ID_APP") or "").strip()
        if not app_id:
            continue
        by_id.setdefault(app_id, []).append(f)
    for app_id, feats in by_id.items():
        out = {"type": "FeatureCollection", "features": feats}
        (GEO_DIR / f"{app_id}.geojson").write_text(json.dumps(out), encoding="utf-8")
        count += 1
    return count


def zip_pack() -> None:
    with zipfile.ZipFile(ZIP_OUT, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(GEO_DIR.glob("*.geojson")):
            zf.write(path, arcname=path.name)
    print(f"Packed -> {ZIP_OUT} ({ZIP_OUT.stat().st_size // 1024} KiB)")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--shp", type=Path, required=True, help="INAO parcellaire shapefile")
    args = parser.parse_args()
    if not args.shp.exists():
        print(f"Missing shapefile: {args.shp}", file=sys.stderr)
        sys.exit(1)

    combined = OUT_DIR / "inao-parcellaire-all.geojson"
    print("Converting shapefile...")
    run_ogr2ogr(args.shp, combined)
    n = split_by_id_app(combined)
    print(f"Split {n} appellations")
    zip_pack()


if __name__ == "__main__":
    main()
