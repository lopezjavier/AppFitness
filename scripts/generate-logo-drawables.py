#!/usr/bin/env python3
"""Generate the Android vector drawables for the AppFitness logo.

The logo lives as a single SVG source in ``design/logo/appfitness-logo.svg`` (a white lifter
silhouette on a transparent background). This script bakes its paths into the three Android vector
drawables the app uses:

* ``composeApp/src/commonMain/composeResources/drawable/ic_app_logo.xml`` - Welcome brand mark.
* ``androidApp/src/main/res/drawable/ic_launcher_foreground.xml``     - adaptive icon foreground.
* ``androidApp/src/main/res/drawable/ic_launcher_monochrome.xml``     - adaptive icon monochrome.

Run it from the repository root after editing the SVG::

    python scripts/generate-logo-drawables.py

Every path is treated as a white (``#FFFFFF``) filled shape and its ``transform="translate(x,y)"`` is
baked into the coordinates, so the drawables need no nested groups. The geometry is absolute
``M``/``C``/``Z`` only.

The mark is wide (about 2.2:1), so a square frame would leave a lot of empty height. The Welcome
drawable therefore uses a non-square viewport that hugs the mark, and the launcher draws the whole
mark scaled inside the adaptive icon's safe circle, so no mask ever cuts it. Output files are UTF-8
with LF endings.
"""

from __future__ import annotations

import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "design" / "logo" / "appfitness-logo.svg"

LOGO_DRAWABLE = ROOT / "composeApp/src/commonMain/composeResources/drawable/ic_app_logo.xml"
LAUNCHER_FOREGROUND = ROOT / "androidApp/src/main/res/drawable/ic_launcher_foreground.xml"
LAUNCHER_MONOCHROME = ROOT / "androidApp/src/main/res/drawable/ic_launcher_monochrome.xml"

# Welcome logo: wide viewport that hugs the mark, so no empty height is left around it.
LOGO_SIZE_DP = "256dp"
LOGO_VIEWPORT_WIDTH = 1024.0
LOGO_VIEWPORT_HEIGHT = 466.0
LOGO_PADDING = 1.03  # frame height = content height * this factor.

# Launcher adaptive icon: 108-square viewport. The whole mark is scaled to fit inside the 66dp safe
# circle, so no mask (launcher or splash) ever cuts it.
LAUNCHER_SIZE_DP = "108dp"
LAUNCHER_VIEWPORT = 108.0
LAUNCHER_SAFE_RADIUS = 33.0  # dp, radius of the 66dp adaptive-icon safe circle.

NUMBER = re.compile(r"-?\d+\.?\d*(?:[eE][-+]?\d+)?")
FILL = "#FFFFFFFF"

HEADER = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<!--\n'
    '    GENERATED from design/logo/appfitness-logo.svg; edit the SVG source, not this file\n'
    '    (regenerate with scripts/generate-logo-drawables.py).\n'
    '-->\n'
)


def format_number(value: float) -> str:
    rounded = round(value, 1)
    if rounded == int(rounded):
        return str(int(rounded))
    return ("%.1f" % rounded).rstrip("0").rstrip(".")


def attribute(tag: str, name: str) -> str:
    match = re.search(r'\b%s="([^"]*)"' % name, tag)
    return match.group(1) if match else ""


def translation(tag: str) -> tuple[float, float]:
    match = re.search(r"translate\(\s*([-\d.eE]+)\s*,\s*([-\d.eE]+)\s*\)", attribute(tag, "transform"))
    return (float(match.group(1)), float(match.group(2))) if match else (0.0, 0.0)


def tokenize(data: str) -> list[str]:
    """Split path data into command letters (M/C/Z) and numeric coordinate tokens."""
    tokens: list[str] = []
    buffer = ""
    for char in data:
        if char.isalpha():
            if buffer:
                tokens.append(buffer)
                buffer = ""
            tokens.append(char)
        elif char in " ,\t\r\n":
            if buffer:
                tokens.append(buffer)
                buffer = ""
        else:
            buffer += char
    if buffer:
        tokens.append(buffer)
    return tokens


def parse(svg: str) -> list[tuple[list[str], float, float]]:
    paths = []
    for tag in re.findall(r"<path\b[^>]*?/>", svg, re.S):
        tokens = tokenize(attribute(tag, "d"))
        numbers = sum(1 for token in tokens if not token.isalpha())
        if numbers % 2 != 0:
            raise SystemExit("path with an odd number of coordinates (relative commands?)")
        if any(token.isalpha() and token not in "MCZ" for token in tokens):
            raise SystemExit("unsupported path command; expected absolute M/C/Z only")
        tx, ty = translation(tag)
        paths.append((tokens, tx, ty))
    if not paths:
        raise SystemExit("no <path> elements found in %s" % SOURCE)
    return paths


def points(paths: list[tuple[list[str], float, float]]) -> list[tuple[float, float]]:
    """Every coordinate pair with its path translation applied (control points included)."""
    result: list[tuple[float, float]] = []
    for tokens, tx, ty in paths:
        index = 0
        x = 0.0
        for token in tokens:
            if token.isalpha():
                index = 0
                continue
            value = float(token)
            if index % 2 == 0:
                x = value + tx
            else:
                result.append((x, value + ty))
            index += 1
    return result


def convert(tokens: list[str], tx: float, ty: float, *, scale: float, offset_x: float,
            offset_y: float, centre_x: float, centre_y: float) -> str:
    """Bake the translation and map source coordinates into the target viewport."""
    out: list[str] = []
    index = 0
    for token in tokens:
        if token.isalpha():
            out.append(token)
            index = 0
            continue
        value = float(token)
        if index % 2 == 0:
            mapped = (value + tx - offset_x) * scale + centre_x
            out.append(" " + format_number(mapped))
        else:
            mapped = (value + ty - offset_y) * scale + centre_y
            out.append(" " + format_number(mapped))
        index += 1
    return "".join(out)


def vector(width_dp: str, height_dp: str, viewport_width: float, viewport_height: float,
           path_data: list[str]) -> str:
    body = "\n".join(
        '    <path\n        android:fillColor="%s"\n        android:pathData="%s" />' % (FILL, data)
        for data in path_data
    )
    return (
        HEADER
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    android:width="%s"\n' % width_dp
        + '    android:height="%s"\n' % height_dp
        + '    android:viewportWidth="%s"\n' % format_number(viewport_width)
        + '    android:viewportHeight="%s">\n' % format_number(viewport_height)
        + body
        + "\n</vector>\n"
    )


def main() -> int:
    paths = parse(SOURCE.read_text(encoding="utf-8"))
    pts = points(paths)
    min_x = min(p[0] for p in pts)
    max_x = max(p[0] for p in pts)
    min_y = min(p[1] for p in pts)
    max_y = max(p[1] for p in pts)
    width, height = max_x - min_x, max_y - min_y
    cx, cy = (min_x + max_x) / 2, (min_y + max_y) / 2

    # Welcome: hug the height, keep the wide aspect (no empty height around the mark).
    logo_frame_height = height * LOGO_PADDING
    logo_frame_width = logo_frame_height * (LOGO_VIEWPORT_WIDTH / LOGO_VIEWPORT_HEIGHT)
    if logo_frame_width < width:
        logo_frame_width = width * LOGO_PADDING
        logo_frame_height = logo_frame_width * (LOGO_VIEWPORT_HEIGHT / LOGO_VIEWPORT_WIDTH)
    logo_scale = LOGO_VIEWPORT_HEIGHT / logo_frame_height
    logo_offset_x = cx - logo_frame_width / 2
    logo_offset_y = cy - logo_frame_height / 2
    logo_height_dp = round(float(LOGO_SIZE_DP[:-2]) * LOGO_VIEWPORT_HEIGHT / LOGO_VIEWPORT_WIDTH)

    # Launcher: fit the whole mark inside the safe circle (never cut by a mask).
    max_radius = max(math.hypot(p[0] - cx, p[1] - cy) for p in pts)
    launcher_scale = LAUNCHER_SAFE_RADIUS / max_radius
    launcher_centre = LAUNCHER_VIEWPORT / 2

    logo_path_data = [
        convert(tokens, tx, ty, scale=logo_scale, offset_x=logo_offset_x, offset_y=logo_offset_y,
                centre_x=0.0, centre_y=0.0)
        for tokens, tx, ty in paths
    ]
    launcher_path_data = [
        convert(tokens, tx, ty, scale=launcher_scale, offset_x=cx, offset_y=cy,
                centre_x=launcher_centre, centre_y=launcher_centre)
        for tokens, tx, ty in paths
    ]

    print("logo frame    = %.0f x %.0f (aspect %.3f)" % (logo_frame_width, logo_frame_height,
                                                         logo_frame_width / logo_frame_height))
    print("launcher mark = %.1f x %.1f of %d (fits the %.0fdp safe circle)" % (
        width * launcher_scale, height * launcher_scale, int(LAUNCHER_VIEWPORT),
        2 * LAUNCHER_SAFE_RADIUS))

    outputs = [
        (LOGO_DRAWABLE, vector(LOGO_SIZE_DP, "%ddp" % logo_height_dp, LOGO_VIEWPORT_WIDTH,
                               LOGO_VIEWPORT_HEIGHT, logo_path_data)),
        (LAUNCHER_FOREGROUND, vector(LAUNCHER_SIZE_DP, LAUNCHER_SIZE_DP, LAUNCHER_VIEWPORT,
                                     LAUNCHER_VIEWPORT, launcher_path_data)),
        (LAUNCHER_MONOCHROME, vector(LAUNCHER_SIZE_DP, LAUNCHER_SIZE_DP, LAUNCHER_VIEWPORT,
                                     LAUNCHER_VIEWPORT, launcher_path_data)),
    ]
    for path, content in outputs:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")
        print("wrote %s (%d bytes)" % (path.relative_to(ROOT), len(content.encode("utf-8"))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
