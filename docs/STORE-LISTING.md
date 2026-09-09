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

Exists. **Has had no polish pass** (`BUILD-PLAN.md`), and this is the last
moment to do one. Everything else on this page is words; the icon is the only
asset a browsing user judges before reading any of them.

### Feature graphic — 1024 × 500 PNG or JPG, no alpha, no transparency

The one asset that must work with no text read at all, because it is shown
cropped and often behind a play button.

- **Left two-thirds:** a month grid, cropped so the coloured cells read as a
  *pattern* — the blocks of four are the recognisable thing, not the dates.
  Real data, not lorem: use a 4-on-4-off September.
- **Right third:** the wordmark and one line: **Nothing is ever locked.**
- **No screenshot chrome, no phone bezel, no "Download now".**
- Dark ground with the app's own palette, so it matches the screenshots below.
- Nothing important in the outer 5% — Play crops it at some sizes.

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

**Producing them.** Set up a 4-on-4-off rota anchored so the visible month shows
two full runs; add one changed day and one note for shot 6. Capture on a device
with a clean status bar (no personal notification icons — silence notifications
first). Crop to a ratio Play accepts and confirm the current requirement in Play
Console at upload rather than trusting a number written down here.

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
| Privacy policy URL | `docs/privacy-policy.md` | **Written but not hosted — blocks release** |
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
