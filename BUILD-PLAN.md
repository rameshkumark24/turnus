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
NOTE: The picker thumbnail did not render on Funtouch or Pixel launchers
despite three configurations tried. **Fixed in the store-submission pass** —
`widget_preview.xml` used `<View>`, which RemoteViews does not permit. See §13.

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
STATUS: DONE
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
STATUS: DONE
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
VERIFIED: On the vivo V2307 (Android 15), all four.

- The mangled code imported. Typed into the real field across four lines, and
  the vivo keyboard added a space after `v1.` unprompted — a better test than the
  one intended. Preview read *"Wrapped nights — 6-day cycle"* and the import
  landed.
- A real reminder, from a real exact alarm at 21:00, tapped from the Settings
  screen, landed on the calendar **and moved the grid from December back to
  September**. Firing the same intent for 15 November steered it to November, so
  the day in the intent drives the month rather than it merely resetting.
- Rotating the phone on the Settings screen did **not** drag the user back to the
  calendar, which is what the `savedInstanceState == null` guard is for.
- Ordering: a restored backup with every shift tied on `sort_order` read five
  times in a row gave one order, ascending by id.

NOT VERIFIED: the new *"could not be opened"* message has never been seen on a
screen. Every `openInputStream` failure now reaches it and the branch compiles,
but the trigger — a cloud placeholder that has not downloaded — could not be
manufactured on this device: deleting the file also removed the provider row, and
this picker offers no *Recent* root. What was checked instead is that the branch
it replaces still behaves: a picked PDF still says *"That file is not a Turnus
backup"*, and a real backup still previews. **Worth one look with a genuine
undownloaded Drive file before release.**

## Phase 14 — Verify the two things I reasoned about but never ran
STATUS: DONE
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
VERIFIED: On the vivo V2307. **One of the two was wrong**, which is the entire
argument for this phase existing.

- **Midnight, half by luck.** The app process was left running for 12h50m across
  a real midnight, and on return to the foreground the ring was correctly on the
  new day — so the "refreshes on return to foreground" half was true, and
  observed rather than staged. The other half was not: with the app sitting in
  the foreground and the civil date moved underneath it, the grid went on ringing
  the old day and naming its shift. `today` was re-read on every state emission,
  and nothing about midnight produces one.
- **Fixed, and re-measured.** The month and year grids now register a receiver
  for `ACTION_DATE_CHANGED` (plus the clock and timezone actions) for exactly as
  long as they are on screen. With the app untouched in the foreground the ring
  moved on its own, and moved back. `dumpsys activity broadcasts` shows the
  receiver present on the month and year screens, absent on Settings, and back
  again on return — so the `DisposableEffect` really does tear it down.
- **The widget after "delete everything"** falls back to *"Tap to set up your
  rota"* and stays tappable — tapping it opened the setup wizard. The alarm
  window is emptied at the same moment.

HOW THE CLOCK WAS MOVED: not by setting it. `adb` cannot set the clock without
root, and the alternative — turning off automatic time on someone's daily phone —
makes every timestamp on it wrong for as long as the test runs. Moving the
**timezone** across the date line changes the civil date while leaving the epoch
clock correct, so nothing else on the phone was affected. `cmd
time_zone_detector set_time_zone_state_for_tests` does it from the shell.
Timezone, auto-detection and geo-detection were recorded before and restored
after.

**What that method does not cover, corrected after review.** Before the fix the
app listened for no date, time or timezone broadcast, so the two routes did reach
identical code and the substitution was sound. The fix puts all three actions in
a filter, and from that point they differ until they meet in `refreshToday()`:
`dumpsys activity broadcasts` shows 49 `TIMEZONE_CHANGED` broadcasts across this
testing and **no `DATE_CHANGED` at all**, and shell cannot send a protected
broadcast to fake one. The refresh path is measured; the midnight trigger is
Android's documented behaviour and is not. Worth one look at a real midnight.

NOTE: production code was written after all, which this phase said not to expect.
That is the phase working as intended rather than against it — the code exists
because a measurement contradicted a written claim.

## Phase 15 — The store listing
STATUS: DONE
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
DECIDED: **Professions are named in the long description only.** The title and
short description stay pattern-led so the listing speaks to every shift worker;
the long description carries the job titles, because Play indexes it and people
search their job rather than their rotation. Naming one profession in the title
was rejected — there is no profession-specific feature to make it credible.
That closes `PRD.md` §10 Q2 *for the listing*, not for the product.
DELIVERED: `docs/STORE-LISTING.md` — name, short and full description, feature
graphic brief, an eight-shot screenshot sequence with the reason each shot
exists, and the rest of the Play form. `docs/check-listing.sh` checks the copy
against Play's field limits; two of the three counts written by hand were wrong,
which is why it exists.
STILL YOURS: the privacy policy is written but **not hosted**, and that blocks
release. Contact email and EU trader status are undecided. The icon is done
(`docs/play/icon-512.png`); the **feature graphic** is not, and it is the only
asset a browsing user judges before reading a word of the listing.

## Phase 16 — Release candidate
STATUS: **BLOCKED ON YOU** — the code side is done and proven; the upload is not
mine to do.
GOAL: A signed bundle uploaded to internal testing.
BUILD: Real AdMob IDs in `local.properties`; a real upload keystore; privacy
policy placeholders filled and hosted; Data Safety form completed from
`docs/DATA_SAFETY.md`; trader status; final full test pass; `bundleRelease`.
EDGE CASES: §16 verify the migration path once more against the previous
release; §7 storage-full during a backup write (still untested).
DONE WHEN: A signed `.aab` is accepted by Play and installs from internal
testing on the vivo. **Not reachable without your keystore and Play account, so
this phase stops one step short of its own definition and says so.**
DEPENDS ON: 12, 13, 15
PLAN MODE: **yes — signing material and a public release.**
NEEDS FROM YOU: AdMob IDs, the keystore, policy hosting, Play access. **None of
these can be done for you.**

DONE, AND PROVEN:

- **A release bundle can no longer ship Google's test ad units.** `adId()` fell
  back to them silently in a *release* build, and the only guard was a
  `logger.lifecycle` line that scrolls past. Every other release mistake
  announces itself — an unsigned bundle is refused at the upload screen, broken
  R8 crashes on launch. This one installs, runs, fills 100% of its requests with
  demo creatives for no money, and says nothing until the dashboard reads zero a
  week after going live. With one banner as the whole business it costs
  everything and looks like success, so `bundleRelease` now refuses.
  `assembleRelease` keeps the fallback, because it is the R8 check and has to
  work on a fresh clone. Verified both ways: refuses without, builds an 8.8 MB
  signed bundle with.
- **The minified build was run, not just assembled**, on the vivo at `1.0.0`,
  with **zero crashes**: first run to a populated grid; reminders granted and
  **15 alarms actually scheduled** (Room, the engine and AlarmManager together —
  the biggest R8 risk); the Glance widget provider registered, so resource
  shrinking did not eat it; a share code decoded; a backup written at 2,010
  bytes; and `FLAG_SECURE` raised and cleared across the day sheet. The previous
  minified build predated Phases 12–16, so none of this had been exercised.
- **Version** `0.1.0` → `1.0.0`. `versionCode` stays 1 for a first upload.
- **`docs/RELEASING.md`** now carries the bundle gate, the six on-device checks
  with what each one proves, and an ordered pre-flight. Updated rather than
  duplicated into a checklist, so there is one file to keep true.
- **§7 storage-full** now says something a person can act on instead of
  `ENOSPC (No space left on device)`.

NOT DONE, AND WHY:

- **The upgrade path has no previous release to test against.** `versionCode` is
  1: this has never shipped, so schema v1 has never existed on a stranger's
  phone. `MIGRATION_1_2` is still live for pre-release testers and still covered
  by the instrumented test, but the first *real* migration check happens at the
  second release, not this one. Do it then.
- **The storage-full trigger is still unexercised.** Filling a real phone's
  storage to prove a snackbar is not a reasonable thing to do to it.
- **A test-signed build must be uninstalled before installing from Play** — the
  signatures differ and the upgrade is refused. The one used for this smoke test
  has already been removed from the vivo, and its key destroyed.

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

**Last updated:** after Phase 16, which stops at the upload.

## What is built

The app is **feature-complete for v1** and verified on a physical device (vivo
V2307) and an emulator.

- **`:engine`** — 138 tests, pure JVM, property-based over thousands of
  generated cases per run.
- **`:data`** — 39 instrumented tests against real SQLite, including a
  migration test that proves an upgrade preserves existing rows.
- **`:app`** — 22 JVM tests: what a messaging app does to a pasted share code,
  the shared day-of-the-month clock, and the hours caption at a month rollover.
  The last two exist only because Phase 15 made the clock injectable.
- **`:app`** — setup wizard, month grid, day editor, next-shift card, year view,
  reminders, shift editor, rota editor, hours, backup and restore, share and
  import by code, calendar export, delete-everything, adverts and consent.
- **`:widget`** — home-screen widget, working.
- **Release path proven**: a signed bundle builds via the CI environment-variable
  path, and the v1→v2 schema migration has been verified as a real upgrade over
  the previous release in a minified build, with data surviving.

## What is not built

- Phases 12–17 above.
- ~~The widget **picker thumbnail** does not render~~ — **fixed**, root cause in
  `EDGE-CASES.md` §13. It had been written off three times as a launcher quirk on
  the evidence that two launchers both failed. Every launcher fails; the layout
  was invalid for all of them, and one line of `logcat` named the file and line.
- The **feature graphic** (1024x500) has not been made. The listing cannot go
  up without it.

## Store-submission pass — DONE (code), BLOCKED ON YOU (the Console)

A full submission checklist was walked. Four gaps were code and are closed; two
more defects were found by finally running the `LowEnd_A9` emulator, which had
been created and never started.

**Privacy policy, linked in-app.** Play wants it reachable and it was not: the
only URL in the app was the ad-config endpoint. The bigger half of this is that
Settings' whole *Privacy* heading sat inside
`if (AdGate.privacyOptionsRequired(activity))` — true essentially only in the
EEA and UK — so every user outside that had no privacy section at all. The
policy card is now unconditional and the ad-choices card keeps the condition.
`docs/check-listing.sh` now reads `PRIVACY_POLICY_URL` out of the Kotlin and
curls it, so the link in the app and the link on the listing cannot drift and a
404 fails the check rather than the review.

**A force-update mechanism.** `min_version` in `config/ads.json`, compared
against `BuildConfig.VERSION_CODE`, showing a dialog that cannot be dismissed.
It is the only lever that exists on the day a broken build is live and review is
three days away. It fails open on every path — absent key, bad JSON, no network,
negative value — and `config/README.md` says plainly that it is a live grenade,
because undoing a mistaken push needs the blocked user to be online.

Writing it found a hole in something already shipped. `AdGate.onConsentResolved`
returned early when consent did not permit ads, and `onConfigNeeded()` sat below
that return — so **a user who declined consent never fetched the config at all.**
The ad switches did not care, because their answer was already "off". The kill
switch cared entirely: it would have been missing exactly the people who had
already said no to something. The fetch now happens before the branch.

**Portrait, deliberately.** No `screenOrientation` was set, so the app rotated
into a landscape layout nobody had looked at. Locked, with the API 36 caveat
written into the manifest: Android ignores this on displays 600dp and wider, so
a tablet still rotates.

**`YearUiState.loading` was a dead field** — set, never read. It now does what
`MonthUiState.loading` does on the month screen: a caption while the database
answers, rather than a silent gap where two numbers are about to appear.

### Found by running it, not by reading it

- **The month title truncated to "Sep 2..." on a 360dp phone, at normal font
  size.** The header chose between its row and stacked layouts on font scale
  alone; width was never considered. 360dp is an ordinary phone, not an edge
  case, and the year was hidden on the one control whose job is saying which
  month you are looking at.

  Worth keeping for the method rather than the bug: the first fix computed
  whether the title *ought* to fit, by measuring it and subtracting the
  controls. It said it fitted, by 4dp. It still truncated on screen, because
  five Material buttons' real widths depend on glyph metrics and minimum sizes
  that the arithmetic got slightly wrong — and being 4dp optimistic is
  indistinguishable from being right until it truncates. The shipped fix asks
  the `Text` whether it actually overflowed and latches on that, so no number
  here has to stay true for any locale, font or screen. Verified stacked at
  360dp and *not* stacked at 411dp, which is the regression that matters.

- **The kill switch is only as prompt as the ads SDK.** `AdConfig.refresh` is
  reached through `AdGate.start`, so on the emulator — with out-of-date Play
  services — the block took **26 seconds** to appear after launch. It arrives on
  every path including a consent failure, so it does arrive; it is not instant.
  Written down rather than fixed, because the alternative is a second fetch site
  and the mechanism is a last resort, not a gate.

### Measured on `LowEnd_A9` (API 28, 720x1280 @ 320dpi = 360 x 640dp)

- First run, setup wizard and the populated grid, all correct at 360dp.
- **Portrait lock proven with a control**: with `user_rotation=1` the system
  Settings app went to `ROTATION_90` and Turnus stayed at `ROTATION_0`.
- The privacy policy button launched Chrome on the right URL.
- **The force-update dialog, seen on a screen.** Airplane mode on, a cached
  `min_version=2` against `versionCode=1`: the fetch failed with
  `UnknownHostException`, the cached value applied, and the dialog appeared over
  a visible calendar. Two back presses and a tap outside did not shift it.
  Network back on, the real config read `minVersion=0`, and the block lifted.
- **Offline is no longer only reasoned about** — that airplane-mode run is the
  first time §2's fail-open path has actually been exercised.
- The live `ads.json` still has no `min_version` key, and the app read it as `0`:
  an old config against a new app fails open, confirmed rather than assumed.

**Not verified:** the declined-consent branch of that fix. UMP on this emulator
reported `canRequestAds() == true` even after *Manage options → Confirm choices*
with nothing enabled, so `consent does not permit ads` was never reached. The
change is an unconditional call moved above a return and is plain to read, but it
has not been watched happening.

**Still blocked on you:** the Console forms (Data safety, content rating), the
Pre-Launch Report, a staged rollout, a second copy of the upload keystore, the
feature graphic, and screenshots 02 and 07.

## Known-wrong, deliberately not yet fixed

Both items that stood here were fixed in Phase 15 and are recorded in
`EDGE-CASES.md` with what was measured. What is left is smaller and honest:

- **`ACTION_DATE_CHANGED` has never been delivered in any test.** Everything the
  app does in response to it is measured, through the timezone and clock actions
  that share the same handler; the midnight *trigger* itself rests on Android
  broadcasting it, which is documented rather than observed here. Shell cannot
  send a protected broadcast and neither device available has root. One look at a
  real midnight would close it.
- **`TIME_SET` is declared but may never arrive.** It is not on Android's
  implicit-broadcast exception list, unlike `TIMEZONE_CHANGED`. Declared because
  it costs a line and is harmless if unused. The zone case — the one that happens
  to real people — is measured.
- **The month-rollover caption is covered by a test, not by a device run.** A
  timezone shift moves the date by a day and cannot reach a month boundary.

## Blocked on you, not on code

AdMob IDs · an upload keystore · hosting the privacy policy · Play production
access · the decision in Phase 15 about naming a profession.

## Carried forward from Phase 15

- **The clock is one object now, and that is why there are tests.** The previous
  accept — "no automated test, because injecting a clock into two ViewModels is
  larger than the fix" — was a cost manufactured by holding the date per
  ViewModel. `TodayClock` takes its reader as a parameter, and nine tests exist
  that could not have been written before.
- **Two of three character counts written by hand into the listing were wrong.**
  `docs/check-listing.sh` now checks them. Worth remembering the next time a
  number is typed into a document rather than measured.
- **`uiautomator dump` does not capture snackbars.** Two error messages this
  session looked absent and were on screen the whole time. Screenshot instead.
- **A snapshot of one screen is not a snapshot of the app.** The receiver-scope
  check in Phase 14 exercised navigation and not backgrounding, and missed that
  the receiver ran the whole time the app was away. Ask what the *other* axis is.

## Carried forward from Phase 14

- **A written claim was wrong, and only running it found that out.** The midnight
  row had reasoned its way to "believed correct" and was half right, which is the
  worst kind: the half that was true (foreground return) hid the half that was
  not (staying in the foreground). Worth remembering when reading the rows still
  marked *believed correct* elsewhere in `EDGE-CASES.md` — two remain, on RTL
  layout and on a storage-full backup write.
- **The date-change fix has no automated test**, and adding a fair one means
  injecting a clock into two ViewModels — a larger change than the fix. It is
  covered instead by a device measurement in both directions plus a
  receiver-lifecycle check. Recorded so the gap is a decision rather than an
  oversight.
- **The settings screen is still deliberately stale** across midnight; it already
  says so in its own comment, and it captions a nudge button rather than
  answering "what am I on today".
- **An AdMob debug overlay** ("native ad validator — no implementation issues
  found") appears over the UI after visiting the year view in a debug build, and
  silently swallows scrolling. Harmless, debug-only, and worth knowing before it
  wastes ten minutes again.

## Carried forward from Phase 13

- **The "could not be opened" message is written but unseen.** It is reached by
  every read failure and the wording is deliberate, but a real undownloaded cloud
  file was not reproducible on the vivo. This is the one thing in Phase 13 taken
  on reasoning rather than observation.
- **Reading a pasted code is now deliberately lenient**, and leniency has a
  ceiling. Whitespace and the whole `Cf` category are stripped, and a blank line
  is treated as the boundary between chatter and code. What still fails is a
  token hard-wrapped *and* surrounded by prose with no blank line between them —
  strip the spaces there and the sentence runs into the token. That is a real
  gap, left open on purpose: closing it means guessing where prose ends, and a
  wrong guess silently imports the wrong rota.
- **The vivo was withholding `SCHEDULE_EXACT_ALARM`** on a fresh install, and the
  app said so in Settings and offered the way to grant it. Granting it made the
  notice disappear and the alarm fired at 21:00:00 to the second. That is the
  §11 degrade-and-say-so path working on the handset it was written for — the
  first time it has been seen on real hardware rather than an emulator.
- **The widget agreed with an imported rota** without being asked to. Noted as a
  free observation, not a test: it is Phase 14's second item.

## Carried forward from Phase 12

- **Dismissal is permanent.** "Not now" retires the card for good, and the only
  way back is the Settings switch. That is deliberate — asking twice is nagging,
  and Android suppresses a second permission prompt anyway — but it means a
  mis-tap costs the user the feature until they go looking. Watch for it in
  reviews; the cheap mitigation, if it bites, is to re-offer once after a month
  rather than never.
- **On a first run in a consent region the user answers two things in a row:**
  the advert consent form, then the reminder card behind it. Correct in order —
  the card is inline, not a dialog, so it waits — but it is two decisions in the
  first ten seconds. Verified working; noted in case it reads as heavy.
- **Phase 12 was verified on the emulator only.** The vivo was not connected.
  The permission dialog and alarm scheduling behave the same, but the OEM
  battery behaviour in `EDGE-CASES.md` §11 is exactly what an emulator cannot
  show. Worth one run on the vivo before release.

## Next

**Yours, not mine.** The code is a release candidate: a minified 1.0.0 build was
run end to end on the vivo with no crashes, and the bundle now refuses to build
with test ad units. What is left cannot be done from here, in the order
`docs/RELEASING.md` §6 sets out:

1. An upload keystore, and real AdMob ids in `local.properties`.
2. The privacy policy hosted somewhere that resolves — **this blocks release**.
3. The Data Safety form from `docs/DATA_SAFETY.md`, trader status, contact email.
4. Screenshots and a feature graphic from the brief in `docs/STORE-LISTING.md`.
5. `bundleRelease`, upload to **internal testing**, install from Play on the vivo.

**Phase 17 (multiple rotas) is v1.1 and deliberately after the release.**

Small things still owed, none blocking: the *"could not be opened"* message
against a real undownloaded cloud file (Phase 13), a real midnight (Phase 14),
and the storage-full trigger (Phase 16).
