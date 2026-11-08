"""Play phone screenshots with a caption above each, 1080 x 1920.

Regenerates `docs/play/screenshots/` from the raw captures in
`docs/screenshots/`. The raw captures went to the store as they were, and a
calendar with no words on it does not say why it is better than the calendar
already on the phone. Play shows the first two or three without scrolling, so
each now carries one line saying what the screen is for.

The captions are not the ones first planned in `docs/STORE-LISTING.md`. Those
led with "Every feature free", and Google's preview-asset guidelines rule out
price and promotional information in screenshots and name "Free" outright for
store graphics. A listing update rejected over a caption costs a review cycle,
so every caption here describes what the screen does instead. The claim that
nothing is locked stays in the text of the listing, where it is allowed.

Rules these follow, from the same guidelines: the caption band is under 20% of
the image (it is 15%), the screenshot underneath is the real app, unretouched,
and nothing is a call to action.

    python docs/play/render-screenshots.py

Needs Pillow and a Segoe UI install (Windows), like render-feature.py.
"""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "docs" / "screenshots"
OUT = ROOT / "docs" / "play" / "screenshots"

W, H = 1080, 1920
BAND = 288                         # 15% of the height; Play allows up to 20%
GROUND = (13, 20, 24)              # GroundD #0D1418, the app's dark background
INK = (229, 236, 240)              # InkD #E5ECF0
ACCENT = (82, 191, 209)            # AccentD #52BFD1
BOLD = r"C:\Windows\Fonts\segoeuib.ttf"

# file -> caption. Order is the upload order; the first two carry the argument.
SHOTS = [
    ("01-calendar.png", "Every shift, years ahead"),
    ("02-reminder.png", "A reminder before every shift"),
    ("03-sharecode.png", "Send your rota to a workmate"),
    ("04-year.png", "Your whole year at a glance"),
    ("05-nudge.png", "Out by a day? One tap."),
    ("06-day.png", "Change any day. Add a note."),
    ("07-widget.png", "Today's shift on your home screen"),
    ("08-hours.png", "Hours, honestly counted"),
]


def fit_font(draw, text, max_width, start=78, smallest=54):
    """The largest size up to [start] at which the caption fits on one line."""
    size = start
    while size > smallest:
        font = ImageFont.truetype(BOLD, size)
        if draw.textlength(text, font=font) <= max_width:
            return font
        size -= 2
    return ImageFont.truetype(BOLD, smallest)


def render(source: Path, caption: str, target: Path):
    shot = Image.open(source).convert("RGB")
    canvas = Image.new("RGB", (W, H), GROUND)
    draw = ImageDraw.Draw(canvas)

    # Caption, centred in the band, with a short accent rule above it.
    font = fit_font(draw, caption, W - 2 * 72)
    width = draw.textlength(caption, font=font)
    ascent, descent = font.getmetrics()
    y = (BAND - (ascent + descent)) // 2 + 18
    draw.rounded_rectangle(((W - 56) // 2, y - 34, (W + 56) // 2, y - 28), radius=3, fill=ACCENT)
    draw.text(((W - width) / 2, y), caption, font=font, fill=INK)

    # The real screen below it, scaled to fit, corners rounded like a phone's.
    room_h = H - BAND - 40
    scale = room_h / shot.height
    size = (round(shot.width * scale), room_h)
    shot = shot.resize(size, Image.LANCZOS)
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=40, fill=255)
    canvas.paste(shot, ((W - size[0]) // 2, BAND), mask)

    canvas.save(target, "PNG", optimize=True)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for name, caption in SHOTS:
        source = SOURCE / name
        if Image.open(source).size != (1080, 1920):
            raise SystemExit(f"{name}: expected a 1080 x 1920 capture")
        render(source, caption, OUT / name)
        print(f"{name}: {caption}")


if __name__ == "__main__":
    main()
