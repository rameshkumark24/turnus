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
versionCode = 1      // must increase for every upload, and never repeat
versionName = "0.1.0" // what the user sees
```

Play rejects a bundle whose `versionCode` it has seen before, including one
from a build you deleted. Increment it before every upload, even a re-upload.

## 4. Build it

```sh
./gradlew :app:bundleRelease
```

The bundle lands at `app/build/outputs/bundle/release/app-release.aab`.

Before uploading, run the checks that catch the things a release breaks:

```sh
./gradlew :engine:test                       # the rota arithmetic
./gradlew :data:connectedDebugAndroidTest    # needs a device or emulator
./gradlew :app:assembleRelease               # proves R8 has not broken Room, WorkManager or ads
```

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
- **Store listing** — screenshots, a feature graphic, a short and full
  description.

## 6. After the first release

The remote ad kill switch is `config/ads.json` in this repository, read from
`raw.githubusercontent.com`. Setting either flag to `false` turns that slot off
on every install within a launch or two, with no release needed. It fails
**open**: if the file cannot be fetched, ads stay on, because a hosting hiccup
must not cost the app its entire revenue.
