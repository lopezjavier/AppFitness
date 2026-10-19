#!/usr/bin/env python3
"""Import the RepDB free exercise bundle into the app's (gitignored) Compose resources.

Usage: python scripts/import-repdb.py <bundle_dir>

Reads <bundle_dir>/free.es.json and <bundle_dir>/images/flat/*.webp and writes, under
composeApp/src/commonMain/composeResources/files/exercises/:
  - library.json: trimmed Spanish texts, {"schema_version":1,"exercises":{"<key>":{...}}}
  - images/<key>-start.webp and images/<key>-peak.webp

Exercises that ship a single pose in the bundle ("main") get that image as <key>-start.webp and
"poses": ["start"] in library.json; the others have "poses": ["start", "peak"].
The output is never committed (RepDB content, see ADR-39). The script is idempotent.
"""
import argparse
import json
import os
import re
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EXERCISES_DIR = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "files", "exercises")
SUPPORTED_SCHEMA_VERSION = 3
KEY_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")


class ImportError_(Exception):
    pass


def load_bundle(bundle_dir):
    path = os.path.join(bundle_dir, "free.es.json")
    if not os.path.isfile(path):
        raise ImportError_(f"{path} not found: pass the Biblioteca_Ejercicios_Free folder")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    if data.get("schema_version") != SUPPORTED_SCHEMA_VERSION:
        raise ImportError_(
            f"unsupported schema_version {data.get('schema_version')!r} (expected {SUPPORTED_SCHEMA_VERSION})"
        )
    return data


def pose_files(exercise):
    """Return [(bundle pose name, app pose name)] for an exercise, from its images.flat list."""
    flat = (exercise.get("images") or {}).get("flat") or []
    if "start" in flat and "peak" in flat:
        return [("start", "start"), ("peak", "peak")]
    if "main" in flat:
        return [("main", "start")]
    if "start" in flat:
        return [("start", "start")]
    return []


def trim(exercise):
    poses = [app for _, app in pose_files(exercise)]
    return {
        "name": exercise["name"],
        "description": exercise.get("description") or "",
        "instructions": list(exercise.get("instructions") or []),
        "tips": list(exercise.get("tips") or []),
        "poses": poses,
    }


def import_bundle(bundle_dir, out_dir=EXERCISES_DIR):
    data = load_bundle(bundle_dir)
    images_out = os.path.join(out_dir, "images")
    os.makedirs(images_out, exist_ok=True)
    library = {}
    copied = 0
    for ex in data["exercises"]:
        key = ex["id"]
        if not KEY_RE.match(key):
            raise ImportError_(f"invalid exercise id {key!r}")
        poses = pose_files(ex)
        if not poses:
            continue
        for src_pose, app_pose in poses:
            src = os.path.join(bundle_dir, "images", "flat", f"{key}-{src_pose}.webp")
            if not os.path.isfile(src):
                raise ImportError_(f"missing image {src}")
            shutil.copyfile(src, os.path.join(images_out, f"{key}-{app_pose}.webp"))
            copied += 1
        library[key] = trim(ex)
    payload = {"schema_version": 1, "exercises": library}
    with open(os.path.join(out_dir, "library.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
        f.write("\n")
    return len(library), copied


def main(argv=None):
    ap = argparse.ArgumentParser(prog="import-repdb.py", description=__doc__.splitlines()[0])
    ap.add_argument("bundle_dir", help="Path to the Biblioteca_Ejercicios_Free folder.")
    args = ap.parse_args(argv)
    try:
        count, images = import_bundle(os.path.abspath(args.bundle_dir))
    except ImportError_ as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    print(f"Imported {count} exercises and {images} images into {EXERCISES_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
