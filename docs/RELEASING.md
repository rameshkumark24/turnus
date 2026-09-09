# Releasing Turnus

What has to be true before a build can go to Play, and how to make it so.
Nothing in this file is a secret; everything it tells you to create is
gitignored, and must stay that way.

## 1. The upload key

Play needs a signed bundle. Create the keystore **once**, outside the repository:

```sh
keytool -genkeypair -v \
  -keystore ~/turnus-upload.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias turnus-upload
```

> **Back this file up before you use it**, somewhere that is not this laptop —
> a password manager's file attachment, an encrypted drive, anywhere with a
> second copy. Play App Signing means a lost *upload* key can be reset by
> Google support, but the reset takes days and you cannot ship in the meantime.
> Losing it is recoverable; losing it the week of a bug fix is not fun.
>
> Keep the passwords with it. A keystore whose password you cannot remember is
> the same as a keystore you do not have.

Then tell the build where it is, in `local.properties` — which is gitignored,
and which must never be committed:

```properties
release.keystore=C:/Users/you/turnus-upload.jks
release.keystorePassword=…
release.keyAlias=turnus-upload
release.keyPassword=…
```

CI can supply the same four values as `TURNUS_KEYSTORE`,
`TURNUS_KEYSTORE_PASSWORD`, `TURNUS_KEY_ALIAS` and `TURNUS_KEY_PASSWORD`
instead.

Without these the release build still compiles — useful for checking R8 has
not broken anything — but comes out **unsigned** and Play will reject it. Every
build prints a line saying so.

## 2. The AdMob identifiers

Also in `local.properties`:

```properties
admob.appId=ca-app-pub-…~…
admob.bannerUnitId=ca-app-pub-…/…
admob.nativeUnitId=ca-app-pub-…/…
```

Without them a release build ships Google's **test** units and earns nothing,
and prints a line saying so. Debug builds always use the test units regardless —
impressions from your own device are what gets an AdMob account suspended.

## 3. Version numbers

In `app/build.gradle.kts`:

```kotlin
versionCode = 1       // must increase for every upload, and never repeat
versionName = "1.0.0" // what the user sees
```

Play rejects a bundle whose `versionCode` it has seen before, including one
from a build you deleted. Increment it before every upload, even a re-upload.

## 4. Build it

```sh
./gradlew :app:bundleRelease
```

The bundle lands at `app/build/outputs/bundle/release/app-release.aab`.

**`bundleRelease` refuses to build without real AdMob ids.** That is deliberate
and it is the one place in this project the build stops you rather than warns
you. Every other release mistake announces itself — an unsigned bundle is
rejected at the upload screen, a broken R8 rule crashes on launch. Shipping
Google's *test* ad units announces nothing: the app installs, runs, and fills
100% of its ad requests with demo creatives, for no money, and the first symptom
is a dashboard reading zero a week after the bundle went live. With one banner as
the whole business, that failure costs everything and looks like success.

If you only want to exercise R8, build `assembleRelease` instead — it keeps the
test-id fallback on purpose, so the check below works on a fresh clone, on CI,
and for whoever picks this up next.

Before uploading, run the checks that catch the things a release breaks:

```sh
./gradlew :engine:test :app:testDebugUnitTest  # the rota arithmetic and the paste handling
./gradlew :data:connectedDebugAndroidTest      # needs a device or emulator
./gradlew :app:assembleRelease                 # proves R8 has not broken Room, WorkManager or ads
bash docs/check-listing.sh                     # the listing still fits Play's field limits
```

### Then run the minified build on a real phone

Assembling is not the same as working. Install
`app/build/outputs/apk/release/app-release.apk` — signed, so you need the key —
and walk it, because these are the paths R8 and resource shrinking actually
break:

| Check | What it proves |
|---|---|
| First run reaches a populated grid | the app shell, Compose, the setup wizard |
| Reminders on, then `dumpsys alarm \| grep REMIND` shows alarms | Room, the engine and AlarmManager together — the biggest R8 risk |
| The widget appears in the picker and shows today | Glance survived **resource** shrinking, not just code shrinking |
| Paste a share code and see the preview | the engine's decoder |
| Save a backup and check the file has bytes in it | Room, `org.json` and the file picker |
| Open a day: the recents thumbnail should be blank | `FLAG_SECURE` is still being applied |

All six passed on a vivo V2307 against `1.0.0` with zero crashes. Re-run them
whenever a dependency moves.

**Uninstall the test-signed build before installing anything from Play.** A build
signed with a different key cannot be upgraded over — Play's install will fail
with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` and the fix is an uninstall that takes
the rota with it.

## 5. What Play asks for that is not code

- **Privacy policy URL** — `docs/privacy-policy.md`, with its placeholders
  filled in, hosted somewhere public. GitHub Pages on this repository is free
  and adequate.
- **Data Safety form** — the answers are in `docs/DATA_SAFETY.md`, worked out
  against the source rather than guessed. The two easy ones to get wrong:
  approximate location **is** collected (the ads SDK derives it from the IP
  address, even though the app asks for no location permission), and the user's
  rota is **not** collected, because it never leaves the device.
- **Ads declaration** — the listing must say the app contains ads.
- **Trader status (EU DSA)** — an ad-funded app is a trader, and the address
  given is published on the listing.
- **Target audience** — adults. Not a children's app, so no Families Policy.
- **Store listing** — all of it is drafted in `docs/STORE-LISTING.md`: the name,
  both descriptions, the feature-graphic brief and an eight-shot screenshot
  sequence with the reason each shot exists. `bash docs/check-listing.sh` checks
  the copy against Play's field limits, which exists because two of the three
  counts written there by hand were wrong. The description should say plainly that Turnus is a personal
  planner and **not a record of hours worked**: the hours it shows are what the
  pattern says, less unpaid breaks, and it knows nothing about overtime, a shift
  someone covered, or an hour sent home early. The month card says the same
  thing where the figure appears, and the two should agree.

## 6. Pre-flight, in order

The order matters — each step is cheap and catches something the next one would
waste time on.

1. `bash docs/check-listing.sh` — seconds, and stops a rejected listing.
2. `./gradlew :engine:test :app:testDebugUnitTest` — the arithmetic and the paste handling.
3. `./gradlew :data:connectedDebugAndroidTest` — needs a device.
4. `./gradlew :app:assembleRelease`, then **run the six checks in §4 on a phone**.
   This is the step people skip and it is the one that finds R8 damage.
5. Bump `versionCode`. Play rejects a code it has already seen, including from a
   bundle you deleted.
6. `./gradlew :app:bundleRelease` — refuses without real ad ids, and is unsigned
   without a key.
7. Upload to **internal testing**, not production. Install from Play on a real
   phone before promoting anything.
8. Fill the Data Safety form from `docs/DATA_SAFETY.md` and check the privacy
   policy URL actually resolves. A policy that 404s is a rejection.

### What is not code and cannot be done for you

An upload key · real AdMob ids · the privacy policy hosted somewhere public ·
the Data Safety form · trader status and the address it publishes · a Play
account. The build warns about the first two and refuses the bundle for the ad
ids; the rest are yours and nothing in the repository can check them.

## 7. After the first release

The remote ad kill switch is `config/ads.json` in this repository, read from
`raw.githubusercontent.com`. Setting either flag to `false` turns that slot off
on every install within a launch or two, with no release needed. It fails
**open**: if the file cannot be fetched, ads stay on, because a hosting hiccup
must not cost the app its entire revenue.
