#!/usr/bin/env python3
"""Render the screen gallery (T-279) and write composeApp/build/gallery/index.html.

Usage: python scripts/gallery.py [--index-only] [Area]

Runs the gallery capture tests on the host (Robolectric + Roborazzi, no device, no backend) with -Pgallery, then
builds a static index from the per-image sidecar JSON. Images are never compared: they are for review rounds.
With an Area (e.g. Entreno, "Design system") only that area is captured and indexed; without one, everything (CI).
"""
import argparse
import html
import importlib.util
import json
import os
import shutil
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# Loaded by path: CI runs `python3 -I`, which drops this folder from sys.path.
_spec = importlib.util.spec_from_file_location("gitbash", os.path.join(os.path.dirname(__file__), "gitbash.py"))
gitbash = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gitbash)
GALLERY_DIR = os.path.join(ROOT, "composeApp", "build", "gallery")
AREAS = ["Inicio", "Diario", "Entreno", "Trainer", "Perfil", "Compartido", "Design system"]


class GalleryError(Exception):
    """The gallery folder is inconsistent (an image without its sidecar, or the other way round)."""


def load_entries(gallery_dir):
    """Read every sidecar next to its PNG. Raises GalleryError on an orphan of either kind."""
    entries = []
    problems = []
    for folder, _dirs, files in os.walk(gallery_dir):
        names = set(files)
        for name in sorted(names):
            stem, ext = os.path.splitext(name)
            rel = os.path.relpath(os.path.join(folder, name), gallery_dir).replace(os.sep, "/")
            if ext == ".json" and name != "manifest.json":
                if stem + ".png" not in names:
                    problems.append("sidecar without image: " + rel)
                    continue
                with open(os.path.join(folder, name), encoding="utf-8") as handle:
                    data = json.load(handle)
                data["image"] = rel[: -len(".json")] + ".png"
                entries.append(data)
            elif ext == ".png" and stem + ".json" not in names:
                problems.append("image without sidecar: " + rel)
    if problems:
        raise GalleryError("; ".join(sorted(problems)))
    return entries


def load_manifest(gallery_dir):
    path = os.path.join(gallery_dir, "manifest.json")
    if not os.path.exists(path):
        return {"exempt": {}}
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def _figure(entry):
    alt = "{} / {} / {}".format(entry["area"], entry["screen"], entry["state"])
    return (
        '<figure><a href="{src}"><img src="{src}" alt="{alt}" width="180" loading="lazy"></a>'
        "<figcaption>{state}</figcaption></figure>"
    ).format(src=html.escape(entry["image"], quote=True), alt=html.escape(alt, quote=True), state=html.escape(entry["state"]))


def _screen_section(screen, entries):
    also = sorted({a for e in entries for a in e.get("alsoOpenedFrom", [])})
    parts = ["<h3>{}</h3>".format(html.escape(screen))]
    if also:
        parts.append("<p>Also opened from: {}</p>".format(html.escape(", ".join(also))))
    screens = [e for e in entries if e.get("kind", "screen") == "screen"]
    overlays = [e for e in entries if e.get("kind", "screen") != "screen"]
    if screens:
        parts.append('<div class="grid">' + "".join(_figure(e) for e in screens) + "</div>")
    if overlays:
        parts.append("<h4>Sheets and dialogs</h4>")
        parts.append('<div class="grid">' + "".join(_figure(e) for e in overlays) + "</div>")
    return "\n".join(parts)


def build_index(entries, manifest, area=None):
    """Return the index.html text: one h2 per area, one h3 per screen, the exemptions table at the end."""
    known = [a for a in AREAS if any(e["area"] == a for e in entries)]
    extra = sorted({e["area"] for e in entries} - set(AREAS))
    body = ["<h1>Screen gallery</h1>"]
    if area:
        body.append("<p>Area: {}</p>".format(html.escape(area)))
    body.append("<p>{} images. Not compared with anything: review aid only.</p>".format(len(entries)))
    for area in known + extra:
        body.append("<h2>{}</h2>".format(html.escape(area)))
        screens = []
        for entry in entries:
            if entry["area"] == area and entry["screen"] not in screens:
                screens.append(entry["screen"])
        for screen in screens:
            body.append(_screen_section(screen, [e for e in entries if e["area"] == area and e["screen"] == screen]))
    exempt = manifest.get("exempt", {})
    body.append("<h2>Exemptions</h2>")
    body.append(
        "<table><tr><th>Name</th><th>Reason</th></tr>"
        + "".join("<tr><td>{}</td><td>{}</td></tr>".format(html.escape(k), html.escape(v)) for k, v in sorted(exempt.items()))
        + "</table>"
    )
    style = (
        # Dark theme of the app (designsystem/AppColors.kt, dark scheme): surfaceBase, surfaceRaised, surfaceOverlay,
        # textPrimary, textSecondary and the primary NeonLime for links.
        "body{font-family:sans-serif;margin:24px;color:#E0E2EA;background:#0B0F14}"
        "h2{border-bottom:2px solid #1A222D;margin-top:40px;color:#E0E2EA}"
        "h3{color:#C2CAB0}"
        "a{color:#A3E635}"
        ".grid{display:flex;flex-wrap:wrap;gap:12px}"
        "figure{margin:0;width:180px}figcaption{font-size:13px;color:#C2CAB0}"
        "img{display:block;background:#121820;border:1px solid rgba(255,255,255,0.08);border-radius:8px}"
        "table{border-collapse:collapse}td,th{border:1px solid #1A222D;padding:4px 8px;text-align:left}"
    )
    return (
        '<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Screen gallery</title>'
        "<style>{}</style></head><body>{}</body></html>"
    ).format(style, "\n".join(body))


def resolve_area(text):
    """The canonical area for `text` (case, spaces, '-' and '_' are ignored). Raises ValueError listing the valid ones."""
    def norm(value):
        return value.lower().replace("-", "").replace("_", "").replace(" ", "")

    for area in AREAS:
        if norm(area) == norm(text):
            return area
    raise ValueError("unknown area '{}'. Valid areas: {}".format(text, ", ".join(AREAS)))


def gradle_command(bash, area):
    command = [bash, "scripts/gradle-quiet.sh", ":composeApp:testAndroidHostTest", "-Pgallery"]
    if area:
        command.append("-Pgallery.area=" + area)
    return command + ["--tests", "*.gallery.*"]


def write_index(gallery_dir=GALLERY_DIR, area=None):
    entries = load_entries(gallery_dir)
    if area:
        entries = [e for e in entries if e["area"] == area]
    text = build_index(entries, load_manifest(gallery_dir), area)
    path = os.path.join(gallery_dir, "index.html")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    return path


def main(argv):
    parser = argparse.ArgumentParser(prog="gallery.py", description=__doc__)
    parser.add_argument("area", nargs="?", help="Only this area: " + ", ".join(AREAS))
    parser.add_argument("--index-only", action="store_true", help="Rebuild the index from the existing images.")
    args = parser.parse_args(argv)
    area = None
    if args.area:
        try:
            area = resolve_area(args.area)
        except ValueError as error:
            print("error: " + str(error), file=sys.stderr)
            return 2
    if not args.index_only:
        try:
            # Never plain "bash": on Windows that is the WSL launcher, which has no JAVA_HOME.
            bash = gitbash.find_bash()
        except gitbash.BashNotFound as error:
            print("error: " + str(error), file=sys.stderr)
            return 1
        # Drop images of states that no longer exist, so the index never lists stale ones.
        shutil.rmtree(GALLERY_DIR, ignore_errors=True)
        result = subprocess.run(gradle_command(bash, area), cwd=ROOT, env=dict(os.environ, **gitbash.java_env()))
        if result.returncode != 0:
            return result.returncode
    try:
        path = write_index(area=area)
        count = len([e for e in load_entries(GALLERY_DIR) if not area or e["area"] == area])
    except GalleryError as error:
        print("Gallery inconsistent: " + str(error), file=sys.stderr)
        return 1
    print(path)
    if area and count == 0:
        print("warning: no images for area '{}'".format(area), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
