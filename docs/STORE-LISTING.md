# Play listing — Turnus: Shift Work Calendar

Everything Play asks for at upload, drafted. Character counts are checked by
`docs/check-listing.sh` and are current as of the last run recorded at the
bottom of this file.

**The spine of this listing is that nothing is locked.** Competitors in this
category paywall reminders and sell subscriptions to remove their own adverts.
Turnus gives everything away and is funded by one banner. That is the only claim
here a competitor cannot copy without giving up revenue, so it goes first, it is
the short description, and it is the first screenshot.

**Do not name a competitor anywhere.** Play's metadata policy disallows it, and
in a category this small it reads as punching sideways. The comparison is made
by saying what Turnus does, not by saying what anyone else does not.

**Professions are named in the long description only** — a decision taken
deliberately (`PRD.md` §10 Q2). The title and short description stay pattern-led
so the listing speaks to every shift worker; the long description carries the
job titles because Play indexes it and people search their job, not their
rotation. Naming one profession in the title was considered and rejected: there
is no profession-specific feature to make the claim credible yet.

---

## 1. App name — 27 / 30 characters

```
Turnus: Shift Work Calendar
```

`Turnus` is the Nordic and German word for a shift rotation, which makes it a
real word to some of the audience and a short, ownable, unclaimed one to the
rest. The generic phrase after the colon is the search term.

## 2. Short description — 73 / 80 characters

```
Your rota, offline. Reminders before every shift. Nothing is ever locked.
```

Three claims, in the order they matter: it works without a signal, it tells you
before a shift, and it costs nothing. The last is the differentiator and takes
the emphatic position.

## 3. Full description — 3,278 / 4,000 characters

```
Set your rotation once. Turnus fills in your calendar for years ahead, reminds
you before every shift, and never asks you for a penny.

NOTHING IS LOCKED
Every feature is free. Reminders, the year view, hours, backup, sharing — all
of it, permanently. There is no Pro tier, no unlock, no subscription, and no
subscription to remove the adverts either. One banner on the calendar pays for
the app. That is the whole business.

SET IT UP IN A MINUTE
Pick your pattern — 4 on 4 off, DuPont, Panama, Continental, 2-2-3, or build
your own cycle up to 40 days. Answer one question about what you are on today.
That is it. You are never asked for a "cycle start date" you would have to
count back to work out.

REMINDERS BEFORE EVERY SHIFT
Choose how much warning you want, from an hour to a day. Mute the shifts you do
not need telling about. Reminders survive a reboot.

WHEN AM I NEXT OFF?
The calendar answers the question you actually have. See the run you are in and
the day it changes, your whole year at a glance, your longest break, and how
many hours you are rostered this month.

SEND YOUR ROTA TO A WORKMATE
Everyone on your shift works the same rotation. Send them a code in any
messaging app and they have your pattern in seconds instead of typing it in.

A DAY OUT? FIX IT IN ONE TAP
The commonest thing that goes wrong with a rota app is that it lands a day off.
Turnus moves the whole rotation with one button, and keeps every day you have
already changed.

CHANGE ANY DAY
Swapped a shift, picked up overtime, called in sick, booked a holiday — change
the day and the pattern carries on around it. Add a note to any day.

IT STAYS ON YOUR PHONE
No account. No sign-up. No servers. Your rota is not uploaded anywhere, and it
is left out of Android's automatic backup too, so it does not travel to your
Google Drive without you asking. Notes are the most private thing in the app and
they never leave the device — not in the calendar export, not in a share code.
There is no analytics or crash-reporting SDK in this app at all.

Save a backup file wherever you choose, and put everything back on a new phone.

WHO IT IS FOR
People on a repeating cycle: plant and factory operators, nurses and care
staff, police, fire and ambulance crews, security, offshore and rig workers,
rail, drivers and logistics, and anyone else whose rotation repeats.

WHO IT IS NOT FOR
If your rota is posted weekly by a manager with no repeating shape, Turnus will
fight you — you would be typing in every day by hand. It is built for patterns
that repeat, and it is better to say so here than to waste your time.

HOURS, NOT PAY
Turnus shows the hours you are rostered, less unpaid breaks. It does not
calculate pay. It cannot know about overtime, an hour sent home early, or a
shift you covered, and a confident wrong number in a wage dispute helps nobody.

IF REMINDERS STOP ARRIVING
Some phones — and this is worth knowing before you blame the app — shut down
background apps to save battery, which cancels alarms. If reminders stop, open
Settings inside Turnus and follow "Reminders not arriving?" to allow Turnus to
run in the background. Force-stopping any Android app also cancels its alarms
until you open it again. Turnus rebuilds its reminders every time you open it.
```

## 4. Graphics

### App icon — 512 × 512 PNG, 32-bit, no alpha

**Done: `docs/play/icon-512.png`** — 512 × 512, RGBA, alpha fully opaque, 7 KB.
Generated from the same vector the app ships, so the store icon and the launcher
icon cannot drift apart.

The mark is four bars, two worked and two off. The 2-on-2-off motif reads as a
rotation rather than as a generic calendar page, and it is the same language as
the coloured runs in the month grid.

**What the polish pass actually fixed.** The first version ran its fourth bar out
to x=95 on the 108dp canvas so the bar was cut off, to say the pattern keeps
going. An adaptive icon only guarantees the inner **66dp circle**, and a launcher
does not cut artfully: a circular mask deleted that bar outright — leaving two
worked days and one off, which is not the motif — and a squircle left a sliver
that read as a rendering fault. Either way the mark sat visibly left of centre.
The run is now 60dp wide, centred, farthest corner 32.7dp from the middle, so it
survives every mask shape intact. Checked at 48px and 36px, and against the
Android 13+ themed icon on both a light and a dark ground.

### Feature graphic — 1024 × 500 PNG or JPG, no alpha, no transparency

**Done: `docs/play/feature-graphic.png`** — 1024 × 500, RGB with no alpha
channel at all, 37 KB. Regenerate with `python docs/play/render-feature.py`.

The one asset that must work with no text read at all, because it is shown
cropped and often behind a play button.

- **Left two-thirds:** September 2026 on a real 4-on-4-off rotation — the same
  month as the screenshots, so the store images agree with each other. It bleeds
  off the left, top and bottom, which is what makes the blocks of four read as a
  *pattern* rather than as a photograph of a calendar.
- **Right third:** the four-bar mark, the wordmark, and **Nothing is ever
  locked.** Nothing sits in the outer 5% or the middle.
- Colours are the app's own, taken from the source rather than matched by eye:
  `COLOR_DAY` `#E0A33C` from `RotaRepository`, and `GroundD` / `SurfaceAltD` /
  `InkD` / `AccentD` from `Theme.kt`.
- No screenshot chrome, no phone bezel, no "Download now".

## 5. Screenshot sequence — 8 phone screenshots, in this order

Play shows the first two or three without scrolling, so the argument is made by
then. Every caption below is an overlay, not part of the app.

| # | Screen | Overlay caption | Why it is here |
|---|---|---|---|
| 1 | Month grid, a run of colour, today ringed | **Every feature free. Forever.** | The claim no competitor can copy, made first |
| 2 | Reminder notification on a lock screen | **Reminded before every shift — free** | The proof point for #1: this is what others charge for |
| 3 | Paste-a-code dialog with a rota preview | **Send your rota to a workmate** | The share loop, and the second-most distinctive thing |
| 4 | Year view, longest break highlighted | **Your whole year at a glance** | Answers "when can I book a holiday" |
| 5 | The "out by a day" nudge in Settings | **Out by a day? One tap.** | Directly answers the commonest complaint in the category |
| 6 | Day sheet with a changed shift and a note | **Change any day. Add a note.** | Shows the app bends to real life |
| 7 | Home-screen widget beside the app icon | **Today's shift, without opening anything** | The most-used surface |
| 8 | Hours card, "Rostered — not a record of what you worked" | **Hours, honestly counted** | Sets the expectation that stops a bad review |

### What is captured and what is not

Six of the eight are in `docs/screenshots/`, taken on a vivo V2307, cropped to
**1080 x 1920** — the device is 1080 x 2400, which is 2.22:1 and more elongated
than Play accepts, so a 16:9 window is cut from the content rather than the image
being squashed. Confirm the current requirement in Console at upload rather than
trusting a number written here.

| File | Shot |
|---|---|
| `01-calendar.png` | the month grid, with the reminder offer |
| `03-sharecode.png` | a pasted code previewing someone else's rota |
| `04-year.png` | the year, with working days, hours and longest break |
| `05-nudge.png` | out by a day, and the two buttons that fix it |
| `06-day.png` | changing a day, with a note |
| `08-hours.png` | the hours card and its "rostered" caveat |

**Still to capture: 02 (a reminder on the lock screen)** — it needs an alarm to
actually fire, so it is a wait rather than a tap — **and 07 (the widget)**, which
has to be placed on a home screen by hand.

**Three things that had to be worked around, worth knowing before redoing these:**

- **The status bar is cropped off, not cleaned up.** SystemUI demo mode, which
  normally freezes the clock and hides notification icons, is stripped out of
  this vendor's ROM, so the choice was a real clock and a carrier name in every
  shot or no status bar at all.
- **Ads were turned off at the kill switch during capture**, because a debug
  build renders a "Test Ad" placeholder across the bottom and a release build
  would put a stranger's advert in the listing. Turned back on immediately.
- **The day sheet cannot be screenshotted at all** in a normal build: it carries
  `FLAG_SECURE`, which blocks `adb screencap` as well as the recents thumbnail.
  Shot 06 was taken with the flag temporarily lifted and the flag put straight
  back. Anyone redoing this shot has to do the same, and should check
  `git diff` afterwards.

**Do not** show a shift named after a real employer, and do not show a note that
reads like a real person's medical appointment. The note in shot 6 should be
something like *"swapped with Dave"*.

## 6. Everything else Play asks for

| Field | Value | Status |
|---|---|---|
| Package | `com.turnus.rota` | Done |
| Category | Productivity | Decided |
| Tags | shift work, rota, roster, calendar, work schedule | Decided |
| Content rating | Everyone. No user-generated content leaves the device, no ads targeted to children | Questionnaire not yet filled |
| Target audience | 18+ — declared an adult audience (`PRD.md` §9) | Decided |
| Ads | Yes, contains ads | Decided |
| In-app purchases | **No** — and this must stay No | Decided |
| Data safety | `docs/DATA_SAFETY.md` | Drafted, form not filled |
| Privacy policy URL | Live, and linked in Settings | Done — reachability checked by `docs/check-listing.sh` |
| Contact email | Needed | **Not decided** |
| Trader status | Required in the EU | **Not decided** |

## 7. What this listing deliberately does not say

Worth keeping, because each of these is a thing it would be easy to add later
and each would be a lie or a trap.

- **No pay or earnings claim.** `CLAUDE.md` rule 8.
- **No "scan your rota from a photo".** The app cannot, and will not.
- **No "syncs across your devices".** There is no server; that is the point.
- **No promise that reminders always arrive.** Battery management is real and
  §6 tells the truth about it instead. A listing that over-promises here earns
  exactly the one-star reviews this app is trying to avoid.
- **No competitor named**, per the note at the top.

---

*Counts verified by `docs/check-listing.sh` — 27/30, 73/80, 3,278/4,000. Two of
the three numbers first written here were wrong, which is why the script exists.*
