# `ads.json` — the live remote config

Every installed copy of Turnus reads this file on launch. Editing it changes the
behaviour of apps already on people's phones, with no release and no review.
There is nothing else in this project that can do that.

It is served raw from this repository's `main` branch, so **a commit here is a
deploy.** There is no staging copy.

Read by [`AdConfig`](../app/src/main/kotlin/com/turnus/rota/ads/AdConfig.kt).

## Fields

| Key | Type | Default if absent | What it does |
|---|---|---|---|
| `banners_enabled` | bool | `true` | The anchored banner on the month view |
| `native_enabled` | bool | `true` | The native slot in the year view |
| `min_version` | int | `0` | The oldest `versionCode` allowed to run |

Unknown keys are ignored, so `_comment` is safe and so is anything added later
that older installs will not understand.

## Everything fails open

No network, a timeout, a 404, malformed JSON, a captive portal returning an HTML
login page — all of them mean "no new instruction", and the app carries on with
what it had. A kill switch that turns everything off when it cannot phone home
would take the app's whole revenue down with any hosting hiccup, which is far
more likely than needing to kill a slot.

The values are cached, so a decision made on one run still applies on the next
one even with no connection.

## ⚠ `min_version` is a live grenade

Setting `min_version` above the newest version on Play **bricks every install**.
Users get a dialog they cannot dismiss and cannot use the app at all.

Worse: **undoing it requires the blocked user to be online.** A corrected file
only reaches someone whose phone can fetch it. Anyone offline stays blocked
until they are not.

Use it for exactly one situation: a released build is doing something wrong
enough that not running is better than running, and Play review is three days
away. It is not an "update available" nudge — there is deliberately no soft
version of it.

**Before you change it:**

1. Check the `versionCode` actually on Play, in the Console. Not the one in
   `app/build.gradle.kts`, which is whatever the next build will be.
2. Set `min_version` to the `versionCode` of the **fixed** build, and only once
   that build is live and rolled out — not while it is in review.
3. Set it back to `0` once the bad version is off the store.

A typo here is worse than the bug it was meant to contain. `AdConfig` clamps a
negative to zero and treats a non-number as zero, but it cannot tell a wrong
number from a right one.
