#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Convert INAO parcellaire shapefile to per-appellation GeoJSON map packs.

Produces **two** packs (both kept):

| Pack | R2 key | Typical size | Use |
|------|--------|--------------|-----|
| Full | `appellations-map-fr.zip` | hundreds of Mo | parcellaire detail |
| Mobile | `appellations-map-fr-mobile.zip` | ~10 Mo | app download (default) |

Requires: ogr2ogr (GDAL)

Source: https://www.data.gouv.fr/datasets/delimitation-parcellaire-des-aoc-viticoles-de-linao/
Licence: Licence Ouverte 2.0 — attribution INAO / IGN required in app.

Usage:
  python scripts/catalog/ingest-inao-geo.py --shp path/to/parcellaire.shp
  python scripts/catalog/ingest-inao-geo.py --shp path/to/parcellaire.shp --skip-full
  python scripts/catalog/ingest-inao-geo.py --shp path/to/parcellaire.shp --skip-mobile
"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "scripts" / "catalog" / "out"
GEO_DIR = OUT_DIR / "geojson"
GEO_MOBILE_DIR = OUT_DIR / "geojson-mobile"
ZIP_FULL = OUT_DIR / "appellations-map-fr.zip"
ZIP_MOBILE = OUT_DIR / "appellations-map-fr-mobile.zip"

# Full pack: light simplify in WGS84 degrees (~10 m).
FULL_SIMPLIFY_DEG = "0.0001"
# Mobile pack: dissolve in Lambert 93 then simplify in metres before WGS84.
MOBILE_SIMPLIFY_M = "200"
MOBILE_COORD_DIGITS = 5


def run(cmd: list[str]) -> None:
    print("+", " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True)


def layer_name(shp: Path) -> str:
    return shp.stem


def build_full(shp: Path) -> int:
    """Parcellaire detail: one GeoJSON file per id_app, light simplify."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if GEO_DIR.exists():
        shutil.rmtree(GEO_DIR)
    GEO_DIR.mkdir(parents=True, exist_ok=True)

    combined = OUT_DIR / "inao-parcellaire-all.geojson"
    print("Converting shapefile (full)…", flush=True)
    run(
        [
            "ogr2ogr",
            "-f",
            "GeoJSON",
            str(combined),
            str(shp),
            "-t_srs",
            "EPSG:4326",
            "-simplify",
            FULL_SIMPLIFY_DEG,
        ]
    )
    n = split_by_id_app(combined, GEO_DIR, compact=False, strip_props=False)
    zip_dir(GEO_DIR, ZIP_FULL)
    return n


def build_mobile(shp: Path) -> int:
    """Dissolved + metre-simplify pack sized for on-device download."""
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if GEO_MOBILE_DIR.exists():
        shutil.rmtree(GEO_MOBILE_DIR)
    GEO_MOBILE_DIR.mkdir(parents=True, exist_ok=True)

    lyr = layer_name(shp)
    with tempfile.TemporaryDirectory(prefix="inao-mobile-") as tmp:
        tmp_path = Path(tmp)
        dissolve = tmp_path / "dissolve.gpkg"
        mobile_all = tmp_path / "mobile-all.geojson"
        print("Dissolving by id_app (Lambert)…", flush=True)
        run(
            [
                "ogr2ogr",
                "-f",
                "GPKG",
                str(dissolve),
                str(shp),
                "-nln",
                "apps",
                "-dialect",
                "sqlite",
                "-sql",
                f'SELECT id_app, ST_Union(geometry) AS geometry FROM "{lyr}" GROUP BY id_app',
            ]
        )
        print(f"Simplifying {MOBILE_SIMPLIFY_M} m → WGS84…", flush=True)
        run(
            [
                "ogr2ogr",
                "-f",
                "GeoJSON",
                str(mobile_all),
                str(dissolve),
                "-nln",
                "apps",
                "-simplify",
                MOBILE_SIMPLIFY_M,
                "-t_srs",
                "EPSG:4326",
            ]
        )
        # Copy combined for debugging / reuse.
        combined_out = OUT_DIR / "inao-parcellaire-mobile-all.geojson"
        shutil.copy2(mobile_all, combined_out)
        n = split_by_id_app(
            mobile_all,
            GEO_MOBILE_DIR,
            compact=True,
            strip_props=True,
            coord_digits=MOBILE_COORD_DIGITS,
        )
    zip_dir(GEO_MOBILE_DIR, ZIP_MOBILE, compresslevel=9)
    return n


def round_coords(obj, ndigits: int):
    if isinstance(obj, list):
        if obj and isinstance(obj[0], (int, float)):
            return [round(float(x), ndigits) for x in obj]
        return [round_coords(x, ndigits) for x in obj]
    return obj


def split_by_id_app(
    combined: Path,
    dest: Path,
    *,
    compact: bool,
    strip_props: bool,
    coord_digits: int | None = None,
) -> int:
    data = json.loads(combined.read_text(encoding="utf-8"))
    features = data.get("features") or []
    dest.mkdir(parents=True, exist_ok=True)
    by_id: dict[str, list] = {}
    for f in features:
        props = f.get("properties") or {}
        app_id = str(props.get("id_app") or props.get("ID_APP") or "").strip()
        if not app_id:
            continue
        geom = f.get("geometry")
        if geom and coord_digits is not None and "coordinates" in geom:
            geom = {**geom, "coordinates": round_coords(geom["coordinates"], coord_digits)}
        if strip_props:
            props_out: dict = {"id_app": int(app_id) if app_id.isdigit() else app_id}
        else:
            props_out = props
        by_id.setdefault(app_id, []).append(
            {"type": "Feature", "properties": props_out, "geometry": geom}
        )

    count = 0
    for app_id, feats in by_id.items():
        out = {"type": "FeatureCollection", "features": feats}
        text = (
            json.dumps(out, separators=(",", ":"), ensure_ascii=False)
            if compact
            else json.dumps(out, ensure_ascii=False)
        )
        (dest / f"{app_id}.geojson").write_text(text, encoding="utf-8")
        count += 1
    return count


def zip_dir(src: Path, zip_path: Path, compresslevel: int = 6) -> None:
    with zipfile.ZipFile(
        zip_path,
        "w",
        compression=zipfile.ZIP_DEFLATED,
        compresslevel=compresslevel,
    ) as zf:
        for path in sorted(src.glob("*.geojson")):
            zf.write(path, arcname=path.name)
    print(f"Packed -> {zip_path} ({zip_path.stat().st_size // 1024} KiB)", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shp", type=Path, required=True, help="INAO parcellaire shapefile")
    parser.add_argument("--skip-full", action="store_true", help="Do not rebuild the full pack")
    parser.add_argument("--skip-mobile", action="store_true", help="Do not rebuild the mobile pack")
    args = parser.parse_args()
    if not args.shp.exists():
        print(f"Missing shapefile: {args.shp}", file=sys.stderr)
        sys.exit(1)
    if args.skip_full and args.skip_mobile:
        print("Nothing to do (--skip-full and --skip-mobile).", file=sys.stderr)
        sys.exit(1)

    if not args.skip_full:
        n = build_full(args.shp)
        print(f"Full pack: {n} appellations -> {ZIP_FULL.name}")
    if not args.skip_mobile:
        n = build_mobile(args.shp)
        print(f"Mobile pack: {n} appellations -> {ZIP_MOBILE.name}")


if __name__ == "__main__":
    main()
