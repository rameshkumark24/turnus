#!/usr/bin/env python3
"""Render the Play Store icon from the vector the app actually ships.

Play wants a 512 x 512 PNG that the launcher icon never sees, so the two can
drift: someone edits the vector, the store keeps showing last year's mark. This
reads `ic_launcher_foreground.xml` and `ic_launcher_background.xml` and draws
from them, so the only way to change the store icon is to change the app's.

It also re-checks the thing that was wrong with the first icon. An adaptive icon
guarantees only the inner 66dp circle of its 108dp canvas; anything outside is
cut by the launcher's mask, and the first version lost a whole bar that way. The
check below fails the render rather than letting that ship again.

    python docs/play/render-icon.py          # verify and write the PNG
    python docs/play/render-icon.py --check  # verify only

Needs Pillow. Nothing in the app depends on this; it is a release-asset tool.
"""
from __future__ import annotations

import math
import os
import re
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

AND = "{http://schemas.android.com/apk/res/android}"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
FG = os.path.join(ROOT, "app/src/main/res/drawable/ic_launcher_foreground.xml")
BG = os.path.join(ROOT, "app/src/main/res/values/ic_launcher_background.xml")
OUT = os.path.join(ROOT, "docs/play/icon-512.png")

VIEWPORT = 108          # the adaptive-icon canvas, in dp
SAFE_RADIUS = 33.0      # half of the 66dp circle every mask shape guarantees
SUPERSAMPLE = 16        # drawn large and reduced, because Pillow does not antialias fills


def background() -> tuple[int, int, int, int]:
    m = re.search(r'name="ic_launcher_background">\s*(#[0-9A-Fa-f]{6})\s*<', open(BG).read())
    if not m:
        sys.exit(f"no ic_launcher_background colour in {BG}")
    h = m.group(1)
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) + (255,)


def bars() -> list[dict]:
    """Read the foreground's rounded rectangles.

    Deliberately narrow: it understands the one path shape this icon uses
    (`M x,y h.. a r,r .. v.. ..`) and nothing else, so a path it cannot read is
    an error rather than a silently wrong picture.
    """
    out = []
    for path in ET.parse(FG).getroot():
        d = path.get(AND + "pathData") or ""
        m = re.match(r"M([-\d.]+),([-\d.]+)", d)
        h = re.search(r"\sh([-\d.]+)", d)
        v = re.search(r"\sv([-\d.]+)", d)
        a = re.search(r"\sa([-\d.]+),", d)
        if not (m and h and v and a):
            sys.exit(f"unrecognised pathData, this script only draws rounded rects:\n  {d}")
        mx, my = float(m.group(1)), float(m.group(2))
        r = float(a.group(1))
        out.append({
            "x": mx - r, "y": my,
            "w": float(h.group(1)) + 2 * r, "h": float(v.group(1)) + 2 * r,
            "r": r, "a": float(path.get(AND + "fillAlpha") or 1.0),
        })
    if not out:
        sys.exit(f"no paths in {FG}")
    return out


def check(shapes: list[dict]) -> None:
    centre = VIEWPORT / 2
    worst = max(
        math.hypot(cx - centre, cy - centre)
        for b in shapes
        for cx in (b["x"], b["x"] + b["w"])
        for cy in (b["y"], b["y"] + b["h"])
    )
    lo = min(b["x"] for b in shapes)
    hi = max(b["x"] + b["w"] for b in shapes)
    mid = (lo + hi) / 2
    print(f"{len(shapes)} shapes, span {lo:g}..{hi:g}, centre {mid:g}")
    print(f"farthest corner {worst:.2f}dp from centre (66dp safe zone allows {SAFE_RADIUS:g})")
    if worst > SAFE_RADIUS:
        sys.exit(
            f"FAIL: the mark reaches {worst:.2f}dp, outside the 66dp safe zone.\n"
            "A circular launcher mask will cut it. Shrink the run or lose a bar."
        )
    if abs(mid - centre) > 0.5:
        sys.exit(f"FAIL: the mark is centred on {mid:g}, not {centre:g}; it will look crooked.")
    print("OK: inside the safe zone on every mask shape, and centred.")


def render(shapes: list[dict], px: int) -> Image.Image:
    n = VIEWPORT * SUPERSAMPLE
    img = Image.new("RGBA", (n, n), background())
    layer = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for b in shapes:
        draw.rounded_rectangle(
            [b["x"] * SUPERSAMPLE, b["y"] * SUPERSAMPLE,
             (b["x"] + b["w"]) * SUPERSAMPLE, (b["y"] + b["h"]) * SUPERSAMPLE],
            radius=b["r"] * SUPERSAMPLE,
            fill=(255, 255, 255, round(255 * b["a"])),
        )
    return Image.alpha_composite(img, layer).resize((px, px), Image.LANCZOS)


def main() -> None:
    shapes = bars()
    check(shapes)
    if "--check" in sys.argv:
        return
    # RGBA with a fully opaque alpha: Play asks for a 32-bit PNG and rejects
    # transparency, which are not the same requirement.
    render(shapes, 512).convert("RGBA").save(OUT, "PNG")
    print(f"wrote {os.path.relpath(OUT, ROOT)} ({os.path.getsize(OUT)} bytes, Play allows 1048576)")


if __name__ == "__main__":
    main()
