# Turnus

`Turnus: Shift Work Calendar` — an offline rota planner for shift workers.
Set a rotation once, see the whole year, share it, get reminded.

Android. No backend, no accounts, no subscription. Free, funded by ads.

## Status

Pre-alpha. `:engine` is complete and green — 63 tests, 0 failures. The Android
modules do not exist yet.

## Modules

| Module    | State       | What it is |
|-----------|-------------|------------|
| `:engine` | **present** | Pure Kotlin/JVM. Date arithmetic, patterns, presets, `.ics` export, share-link encoding. No Android dependency, so its tests run in milliseconds without an emulator. |
| `:data`   | planned     | Room entities, DAOs, repositories, backup/restore, migrations. |
| `:app`    | planned     | Compose UI, notification scheduling, ad slots. |
| `:widget` | planned     | Glance home-screen widget. |

## Build

```bash
./gradlew :engine:test
```

Requires a JDK 17 toolchain. `:engine` needs no Android SDK, so its 63 tests
compile and run in about a second — which is what makes property tests over
thousands of random inputs practical on every commit.

Gradle 9.7.1 via the committed wrapper. Kotlin version is pinned in
`gradle/libs.versions.toml`.

## The one thing that must be right

A rota is a **calendar** concept, not an instant-in-time concept. `DayNumber` is
a count of civil days since 1970-01-01 with no zone and no clock, and every
cycle calculation goes through it. That is why DST, leap years and year
boundaries are not special cases anywhere in this codebase.

The two rules that follow from it, both enforced by tests:

- `Math.floorMod`, never `%` — Kotlin's `%` returns `-1` for `-1 % 8`, and users
  routinely anchor their pattern to a future date and then scroll backwards.
- Generated shifts are never stored. The database holds the pattern and the
  sparse exceptions; the calendar is recomputed on every read.

See [CLAUDE.md](CLAUDE.md) for the full working rules.
