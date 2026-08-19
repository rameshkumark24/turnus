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
