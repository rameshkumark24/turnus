# Turnus — Build plan

Read `PRD.md`, `TRD.md` and `EDGE-CASES.md` before starting a phase.

**How to use this file.** When told *"build the next phase"*, find the first
phase whose `STATUS` is not `DONE`, build exactly that, then update its status
and the **CURRENT STATE** section at the bottom. Do not skip ahead: the order
keeps the app usable after every phase.

**This plan is a record as well as a road map.** Phases 1–11 were built before
this file existed and are marked `DONE` from an audit of the repository, not
from memory. Where something was built and later found wrong, the phase says
so.

---

## Phase 1 — The rota engine, provable without a device
STATUS: DONE
GOAL: The date arithmetic that everything else depends on is correct and proven.
BUILD: Pure-JVM module — civil day numbers, cycle resolution, named presets,
anchor solving, sparse overrides. No Android dependency.
EDGE CASES: §5 negative offsets before the anchor (floor-modulo, not
remainder — the single most likely bug in this codebase); §5 zero and boundary
cycle lengths; §10 year and leap boundaries.
DONE WHEN: `./gradlew :engine:test` passes with property tests over thousands
of generated cases, in seconds, with no emulator.
DEPENDS ON: none
PLAN MODE: no

## Phase 2 — The vertical slice: a real month on screen
STATUS: DONE
GOAL: A rota set up once shows a correct, coloured month grid on a real phone.
BUILD: Room schema and repository; the month grid; the app shell. Screen
through repository through database, installed and running.
**This was the architecture proof** — it established that generated days are
never stored and every cell is recomputed on read (`PRD.md` §7).
EDGE CASES: §1 first launch with no data; §1 blank first frame rather than a
spinner; §7 smallest screen and largest system font.
DONE WHEN: The month grid renders today's shift correctly on a device, and
nothing in the database resembles a generated day.
DEPENDS ON: 1
PLAN MODE: no

## Phase 3 — Setup, so a stranger can reach a populated calendar
STATUS: DONE
GOAL: A new user goes from nothing to a correct calendar without being asked
for a date.
BUILD: The wizard — presets, a custom cycle builder, *"what are you on
today?"*, ambiguity resolution with previews, a confirmation preview.
EDGE CASES: §1 first run; §5 all-off cycle refused; §5 one-day cycle;
§6 empty and whitespace-only names.
DONE WHEN: A fresh install reaches a correct calendar in four or five taps
without the words "anchor" or "cycle start" appearing.
DEPENDS ON: 2
PLAN MODE: no

## Phase 4 — Changing a day
STATUS: DONE
GOAL: Swaps, sickness, overtime and notes are all expressible, and reversible.
BUILD: Day sheet; override storage; undo; the note-only row that does *not*
pin the day's shift.
EDGE CASES: §5 a note must not pin a shift, or correcting a misaligned rota
strands every annotated day; §6 apostrophes and pasted text.
DONE WHEN: A day can be changed, annotated, undone and put back to what the
rota says.
DEPENDS ON: 2
PLAN MODE: no

## Phase 5 — The two questions the calendar cannot answer by itself
STATUS: DONE
GOAL: *"When am I next off?"* and *"What does my year look like?"*
BUILD: The next-shift card; the year view with working-day count and longest
break.
EDGE CASES: §10 locale first-day-of-week; §10 crossing midnight with the app
open.
DONE WHEN: The card names the day the run changes, and the year view finds the
longest break.
DEPENDS ON: 2
PLAN MODE: no

## Phase 6 — Reminders
STATUS: DONE
GOAL: A notification arrives before every shift, surviving reboot, without the
app being opened.
BUILD: Reminder planning in the engine (wall-clock, resolved at scheduling
time); a 30-day rolling alarm window; boot and package-replaced recovery; a
daily top-up job; the Settings section.
EDGE CASES: §11 reboot; §11 app updated; §11 exact-alarm permission absent →
degrade and say so; §11 notification permission revoked → check at fire time
with the API-level guard; §11 duplicate alarms; §4 an alarm firing for a shift
that no longer exists → re-read at fire time; §10 DST gap clamping.
DONE WHEN: `dumpsys alarm` shows an alarm at the right minute for the next
working day, and it survives a reboot without opening the app.
DEPENDS ON: 2, 3
PLAN MODE: no

## Phase 7 — The widget
STATUS: DONE
GOAL: Today's shift on the home screen without opening anything.
BUILD: Glance widget; a single shared repository instance so the app and
widget cannot go blind to each other's writes.
EDGE CASES: §13 stale after a rota change; §13 two widgets; §13 midnight
rollover.
DONE WHEN: The widget agrees with the app, and updates after an edit.
DEPENDS ON: 2
PLAN MODE: no
NOTE: The **picker thumbnail does not render** on Funtouch or Pixel launchers
despite three configurations tried. The placed widget works. Parked — §13.

## Phase 8 — Adverts and consent
STATUS: DONE
GOAL: The app earns, without the first thing a new user sees being a legal wall.
BUILD: Banner on the month view, native slot in the year view; the consent
flow, deferred until the calendar exists; a remote kill switch that fails open.
EDGE CASES: §15 consent withdrawn mid-session (the config re-application must
re-check consent — this was a real bug found by re-reading, not by running);
§15 SDK fails to initialise → reserved height, no reflow; §15 kill switch
unreachable → fails open; §2 captive portal returning HTML.
DONE WHEN: Adverts fill on a real device, the app is fully usable with consent
refused, and blocking the config host leaves adverts on.
DEPENDS ON: 2, 5
PLAN MODE: **yes — it touches user data and consent.**

## Phase 9 — Making the rota yours: shifts, and the rota itself
STATUS: DONE
GOAL: The seeded guesses can be corrected, and the rota can be changed after
setup.
BUILD: Shift editor — letter, name, colour, times, unpaid break; the rota
editor re-entering the wizard while **keeping the pattern id** so changed days
and notes survive; the ±1-day nudge.
EDGE CASES: §5 duplicate shift letters; §5 break must be shorter than the
shift; §12 deleting a shift still in use → refuse, naming what uses it;
§12 rota out by a day → one field, keeping every changed day.
DONE WHEN: A day shift moved from 07:00 to 06:00 changes the reminders and the
export; the nudge moves the rota and the ±buttons return it exactly.
DEPENDS ON: 3, 6
PLAN MODE: no

## Phase 10 — Getting the rota out, and back
STATUS: DONE
GOAL: A new phone is not a lost rota, and a workmate can have your rotation in
one message.
BUILD: Calendar export; backup and restore through the system file picker with
a pre-restore undo copy; share-by-code carrying **letters, not this phone's
ids**; paste-a-code import with a preview.
EDGE CASES: §14 backup from a newer version refused by format version;
§14 older backup restores with defaults; §14 file changed between picking and
confirming; §14 code from a newer version reported, not half-parsed;
§6 picking a photo at the restore prompt; §8 notes never enter the export.
DONE WHEN: A backup saved on one install restores identically on another; a
code pasted from WhatsApp reproduces the cycle.
DEPENDS ON: 4, 9
PLAN MODE: **yes — it moves all user data across a trust boundary.**

## Phase 11 — Hours, and shipping infrastructure
STATUS: DONE
GOAL: *"How many hours am I on this month?"* answered honestly, and a bundle
that can actually be uploaded.
BUILD: Rostered-hours totals less unpaid breaks, on the month and year;
the *"Rostered — not a record of what you worked"* label; the first schema
migration; release signing from `local.properties` or CI environment;
`docs/RELEASING.md`; in-app delete-everything including the undo remnant.
EDGE CASES: §10 DST — rostered, never elapsed; §5 shifts with no times counted
but contributing nothing, and said out loud; §16 migration with the
pre-migration file copy; §9 delete leaves no remnant.
DONE WHEN: **Verified as an upgrade, not a fresh install** — build the previous
release, put a rota and a note in it, install the new one over the top, and see
the data survive with `Backed up database before migrating v1 -> v2` in the log.
DEPENDS ON: 9, 10
PLAN MODE: no

---

## Phase 12 — Make reminders discoverable
STATUS: NOT STARTED
GOAL: A new user is offered reminders once, on the calendar, instead of never
finding them.
BUILD: A dismissible card on the month view shown after setup completes, when
reminders have never been enabled: *"Want a reminder before every shift?"* with
one button that turns them on and triggers the permission ask. Remember the
dismissal. **Do not** default reminders to on — that would spend Android's one
permission prompt at a moment the user has asked for nothing.
EDGE CASES: §1 reminders off by default and buried — **the highest-ranked item
in the whole catalogue**; §11 the permission ask stays tied to an explicit yes.
DONE WHEN: A fresh install that completes setup sees the card, tapping it
grants and schedules, and dismissing it never shows it again.
DEPENDS ON: 6
PLAN MODE: no
WHY FIRST: The feature the product's positioning rests on currently ships
switched off behind a scroll. Everything else on this list is smaller.

## Phase 13 — The sharp edges
STATUS: NOT STARTED
GOAL: The four small failures that make the app look unreliable are gone.
BUILD:
- Strip whitespace, line breaks and zero-width characters from a pasted share
  code before decoding — messaging apps mangle them, and this is the flow that
  acquires users for free.
- Distinguish *"could not be read — it may still be downloading"* from *"not a
  Turnus backup"* at the restore prompt. Today a cloud file that has not
  finished downloading is reported as corrupt.
- Route a notification tap to the month view. Today `CLEAR_TOP|SINGLE_TOP` with
  no extras lands the user wherever they last were.
- Add the `id` tiebreaker to shift ordering, so a hand-edited backup cannot
  make the shift list shuffle between reads.
EDGE CASES: §14 mangled codes; §14 undownloaded cloud file; §3 notification
landing; §5 shift ordering ties.
DONE WHEN: A code with embedded newlines imports; a notification tapped from
the Settings screen lands on the calendar.
DEPENDS ON: 10
PLAN MODE: no

## Phase 14 — Verify the two things I reasoned about but never ran
STATUS: NOT STARTED
GOAL: Two `unverified` rows in `EDGE-CASES.md` become facts.
BUILD: No production code expected. Test the **midnight rollover** with the app
left open overnight (set the device clock forward rather than waiting), and the
**widget after a full delete-everything**. Fix whatever they expose; if nothing
breaks, remove the `unverified` markers.
EDGE CASES: §10 crossing midnight with the app open; §13 widget after data
deletion.
DONE WHEN: Both behaviours are observed on a device and the markers are gone.
DEPENDS ON: 7, 11
PLAN MODE: no

## Phase 15 — The store listing
STATUS: NOT STARTED
GOAL: A listing that says the true thing that no competitor can copy.
BUILD: Title, short and long description, screenshot sequence, feature graphic.
Built on **"nothing is locked"** with **free reminders** as the proof point and
the **share code** as the second screenshot. Must also carry the battery-and
force-stop explanation from §11 — the only place it can reach people before
they stop trusting the app. Do **not** name competitors: Play's metadata policy
disallows it and it reads badly.
EDGE CASES: §11 OEM battery management and force-stop, explained where people
read it.
DONE WHEN: Every asset Play requires is drafted and sitting in `docs/`.
DEPENDS ON: 12
PLAN MODE: no
NEEDS FROM YOU: whether to target a profession by name, per the research.

## Phase 16 — Release candidate
STATUS: NOT STARTED
GOAL: A signed bundle uploaded to internal testing.
BUILD: Real AdMob IDs in `local.properties`; a real upload keystore; privacy
policy placeholders filled and hosted; Data Safety form completed from
`docs/DATA_SAFETY.md`; trader status; final full test pass; `bundleRelease`.
EDGE CASES: §16 verify the migration path once more against the previous
release; §7 storage-full during a backup write (still untested).
DONE WHEN: A signed `.aab` is accepted by Play and installs from internal
testing on the vivo.
DEPENDS ON: 12, 13, 15
PLAN MODE: **yes — signing material and a public release.**
NEEDS FROM YOU: AdMob IDs, the keystore, policy hosting, Play access. **None of
these can be done for you.**

## Phase 17 — Multiple rotas *(v1.1, not v1)*
STATUS: NOT STARTED
GOAL: Someone with two jobs can see both.
BUILD: **Decide the shape first** — this is `PRD.md` §10 question 1 and it is
genuinely open. Switching between rotas is easy and answers less than half the
need; overlaying them changes what a calendar day *is*, and with it the grid,
the hours total and the reminders together. The schema already anticipates this:
`day_override`'s key includes `pattern_id`, so no migration is needed for the
data.
EDGE CASES: §5 two rotas claiming one day; §12 hours and reminders across two
rotas; §14 the share code format, which currently assumes one rota.
DONE WHEN: Two rotas coexist and the answer to *"am I free on Saturday?"* is
correct.
DEPENDS ON: 16
PLAN MODE: **yes — it changes the data model's central assumption.**

---

## On the number of phases

Seventeen, of which **eleven are already built**. The forward plan is five
phases to launch (12–16) plus one deliberately after it (17).

Nothing here should be cut. Phases 12 and 13 are each a single sitting and both
fix things that are currently wrong. Phase 14 is testing, not building. Phase 15
is writing. Phase 16 is mostly yours, not mine.

**If you wanted to launch sooner**, the only honest cut is Phase 14 — but it is
the cheapest phase on the list and it converts two guesses into facts, so
cutting it saves an hour and keeps two unknowns in a shipping product.

---

# CURRENT STATE

*Update this section at the end of every phase.*

**Last updated:** after Phase 11, before Phase 12.

## What is built

The app is **feature-complete for v1** and verified on a physical device (vivo
V2307) and an emulator.

- **`:engine`** — 130 tests, pure JVM, property-based over thousands of
  generated cases per run.
- **`:data`** — 35 instrumented tests against real SQLite, including a
  migration test that proves an upgrade preserves existing rows.
- **`:app`** — setup wizard, month grid, day editor, next-shift card, year view,
  reminders, shift editor, rota editor, hours, backup and restore, share and
  import by code, calendar export, delete-everything, adverts and consent.
- **`:widget`** — home-screen widget, working.
- **Release path proven**: a signed bundle builds via the CI environment-variable
  path, and the v1→v2 schema migration has been verified as a real upgrade over
  the previous release in a minified build, with data surviving.

## What is not built

- Phases 12–17 above.
- The widget **picker thumbnail** does not render (parked, `EDGE-CASES.md` §13).
- The app icon is a real design, not a placeholder, but has had no polish pass.

## Known-wrong, deliberately not yet fixed

- **Reminders default to off** and are only reachable through Settings. This is
  Phase 12 and it is the most important thing on this list.
- A pasted share code broken across lines by a messaging app fails to decode.
- A cloud backup file that has not downloaded is reported as *"not a Turnus
  backup"*.
- A notification tapped while the app is open lands on the wrong screen.
- Shift ordering has no tiebreaker.

## Blocked on you, not on code

AdMob IDs · an upload keystore · hosting the privacy policy · Play production
access · the decision in Phase 15 about naming a profession.

## Next

**Phase 12 — Make reminders discoverable.**
