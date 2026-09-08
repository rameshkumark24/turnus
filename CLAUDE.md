# Turnus — agent rules

`Turnus: Shift Work Calendar` — an offline, ad-funded rota planner for shift workers.
Package: `com.turnus.rota`. Android only. No backend, no accounts, no in-app purchases.

## Hard rules — violating these is a bug, not a style choice

1. **Never compute cycle position from timestamps.** No `System.currentTimeMillis()`,
   no `Date`, no `Instant`, no local-time conversion in rota arithmetic. All rota
   dates go through `DayNumber`, which is a civil day count with no zone and no clock.

2. **Never use `%` on a day delta.** Kotlin's `%` is a remainder: `-1 % 8 == -1`.
   A user browsing before their anchor date will index out of bounds. Use
   `Math.floorMod`. This is the single most likely bug in the app.

3. **Never persist a generated shift.** The database stores the `pattern` and the
   sparse `day_override` exceptions. Every calendar cell is recomputed on read.
   Storing generated days makes "my rota shifted by a day" a data migration
   instead of a one-field edit.

4. **`DayNumber` and epoch millis are different units.** Rota dates are days since
   1970-01-01. Audit timestamps (`created_at`, `updated_at`) are epoch milliseconds.
   `DayNumber` is a value class specifically so the compiler catches the mix-up.

5. **No destructive Room migrations outside debug.** `fallbackToDestructiveMigration`
   wipes user data. Copy the database file before every migration.

## Product commitments — these are load-bearing, not preferences

These came out of competitor research and a privacy audit. Each one is the
reason something else in the app is the way it is, so breaking one quietly
breaks the case for the app.

6. **Nothing is ever locked behind a payment.** No Pro tier, no unlock, no
   subscription — including a subscription to remove the ads. Competitors
   paywall reminders and sell ad-removal subscriptions; "everything is free,
   funded by one banner" is the only thing this app has that they cannot copy
   without giving up revenue. It is also why there is no entitlement to
   enforce, and therefore why this app needs no server. Adding a paid tier is
   not a pricing change, it is an architecture change.

7. **A day's note never leaves the device.** Not in the `.ics` export, not in
   a share code, not in a log. Notes are free text and users record sickness,
   hospital appointments and bereavements in them. `Overrides` deliberately
   carries shift ids and not notes, which makes this structural; `IcsWriterTest`
   holds it that way. The backup file is the single exception, because the user
   chose where it goes and it is the only copy that can bring the notes back.

8. **No pay or earnings calculation.** The hours figure is rostered time less
   unpaid breaks, and the UI says so. The app cannot know about overtime, an
   hour sent home early, or a shift someone covered, so a pay figure would be
   confidently wrong in the direction of a wage dispute. Reviewers of other
   apps ask for this; the answer is no.

9. **No analytics SDK and no crash-reporting SDK.** The published privacy
   policy states there are none. Adding one later silently falsifies a document
   users were invited to trust, and changes the Play Data Safety answers.
   Crash reporting is the tempting one at launch — it still needs an explicit
   decision and a policy update, never a quiet dependency bump.

10. **No rota scanning from photos or PDFs.** It needs a server and a per-scan
    AI cost, and it serves people whose rota has no repeating cycle — who are
    not this app's user. The pattern engine is the answer for the people this
    app is for.

## Module layout

```
:engine   pure Kotlin/JVM, zero Android dependencies. The date engine, presets,
          .ics generation, share-link encoding. Tested with property tests that
          run in milliseconds without an emulator.
:data     Room entities, DAOs, repositories, backup/restore, migrations.
:app      Compose UI, ViewModels, navigation, ad slots, notification scheduling.
:widget   Glance widget.
```

`:engine` must never gain an Android dependency. That constraint is what keeps its
tests fast and what makes the module portable if iOS ever happens.

## Dependency policy

The complete allowed list:

- AndroidX: Compose, Room, WorkManager, Glance
- `play-services-ads` (AdMob)
- `user-messaging-platform` (UMP consent)
- `kotlinx.serialization`

Do not add anything else without being asked. No DI framework, no networking
library, no date library, no image loader. Every dependency is a supply-chain
surface and an update obligation for a solo maintainer.

**Verify any package you suggest actually exists** before writing it into a build
file. Do not invent artifact coordinates.

## Conventions

- Kotlin, coroutines + Flow. No RxJava.
- Compose + Material 3, with design-system tokens replacing the default palette.
- `:engine` is pure functions and immutable data. No mutable state, no singletons
  holding data, no Android imports.
- Public API in `:engine` gets KDoc explaining *why*, not what.
- Tests: property tests over thousands of random inputs for the engine, not
  a handful of examples. Example tests miss the year-boundary and pre-anchor cases.

## Behaviour that is decided — do not redesign

- Notification permission is requested **after** the grid is populated, never on
  launch. Android allows one ask.
- Alarms do not survive reboot. A `BOOT_COMPLETED` receiver that reschedules is
  mandatory.
- Rolling notification window of ~30 days, topped up on app open, on boot, and by
  a daily WorkManager job.
- Ads: anchored adaptive banner on the month view, native slot in the year view,
  rewarded only for opt-in actions. **No interstitials, no app-open ads, ever.**
- Every ad slot sits behind a remote kill switch read from a static JSON config
  that fails open.
- Debug builds use AdMob **test** ad unit IDs. Never test against live units.

## Never commit

Keystores, `key.properties`, `local.properties`, or any signing material.
Check `.gitignore` before the first commit of any new file type.
