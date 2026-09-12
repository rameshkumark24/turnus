"""Play feature graphic, 1024 x 500, from the app's own palette and a real month.

Regenerates `docs/play/feature-graphic.png`. Kept in the repo for the same
reason as `render-icon.py`: the asset is derived from values that live in the
code -- the shift colours in `RotaRepository` and the dark palette in
`Theme.kt` -- and a store image that drifts from the app is worse than none.

The grid is September 2026 on a real 4-on-4-off rotation, the same month the
screenshots show, cropped so the blocks of four read as a pattern rather than
as a photograph of a calendar. Play crops this asset at some sizes and often
puts a play button over the middle, so nothing that has to be read sits in the
outer 5% or the centre.

    python docs/play/render-feature.py

Needs Pillow and a Segoe UI install (Windows). Substitute any humanist sans if
running elsewhere; Roboto is the closest to what the app itself renders.
"""

from datetime import date, timedelta
from PIL import Image, ImageDraw, ImageFont

W, H, S = 1024, 500, 3            # S = supersample

GROUND   = (13, 20, 24)           # GroundD  #0D1418
OFF_CELL = (27, 38, 45)           # SurfaceAltD #1B262D
DAY      = (224, 163, 60)         # COLOR_DAY #E0A33C
INK      = (229, 236, 240)        # InkD  #E5ECF0
INK2     = (161, 176, 186)        # Ink2D #A1B0BA
ACCENT   = (82, 191, 209)         # AccentD #52BFD1

BOLD = r"C:\Windows\Fonts\segoeuib.ttf"
REG  = r"C:\Windows\Fonts\segoeui.ttf"

# 4-on-4-off, anchored on the run that contains 12 Sep 2026 -- the same month
# the screenshots show, so the listing and the store images agree.
ANCHOR = date(2026, 9, 12)
def working(d): return (d - ANCHOR).days % 8 < 4

img = Image.new("RGB", (W * S, H * S), GROUND)
d = ImageDraw.Draw(img)

# --- the grid, bleeding off the left, top and bottom ---------------------------
CELL, GAP = 74 * S, 11 * S
PITCH = CELL + GAP
X0, Y0 = -26 * S, -52 * S         # cropped, so it reads as pattern not screenshot
COLS, ROWS = 7, 7

first = date(2026, 8, 30)         # the Sunday that starts September's grid
for r in range(ROWS):
    for c in range(COLS):
        day = first + timedelta(days=r * COLS + c)
        x, y = X0 + c * PITCH, Y0 + r * PITCH
        d.rounded_rectangle(
            [x, y, x + CELL, y + CELL],
            radius=14 * S,
            fill=DAY if working(day) else OFF_CELL,
        )

# --- fade the grid into the ground so the type has clean air ------------------
fade = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
fd = ImageDraw.Draw(fade)
x_start, x_end = 470 * S, 650 * S
for x in range(x_start, x_end):
    a = int(255 * (x - x_start) / (x_end - x_start))
    fd.line([(x, 0), (x, H * S)], fill=GROUND + (a,))
fd.rectangle([x_end, 0, W * S, H * S], fill=GROUND + (255,))
img = Image.alpha_composite(img.convert("RGBA"), fade).convert("RGB")
d = ImageDraw.Draw(img)

# --- the icon mark, four bars: two worked, two off ----------------------------
TX = 648 * S                      # left edge of the type column
bar_w, bar_h, bar_gap = 15 * S, 34 * S, 6 * S
by = 150 * S
for i in range(4):
    bx = TX + i * (bar_w + bar_gap)
    d.rounded_rectangle(
        [bx, by, bx + bar_w, by + bar_h],
        radius=4 * S,
        # The off bars are the icon's 38% white, lifted onto this darker ground
        # so they stay a pair of bars rather than fading into the background.
        fill=INK if i < 2 else (96, 116, 128),
    )

# --- wordmark and the one claim ----------------------------------------------
word = ImageFont.truetype(BOLD, 76 * S)
line = ImageFont.truetype(REG, 28 * S)
d.text((TX, 205 * S), "Turnus", font=word, fill=INK)
d.text((TX, 306 * S), "Nothing is ever locked.", font=line, fill=ACCENT)

img = img.resize((W, H), Image.LANCZOS)
out = r"C:\App04\docs\play\feature-graphic.png"
img.save(out, "PNG")
print("wrote", out, img.size, img.mode)

import os
print("bytes:", os.path.getsize(out))
# Play rejects alpha on this asset; prove there is none.
print("bands:", Image.open(out).getbands())
