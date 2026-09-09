# Play Data Safety answers for Turnus

The Data Safety form must match what the app actually does. An inaccurate
declaration is grounds for removal, and it is checked against the SDKs in the
uploaded bundle — so these answers are derived from the merged manifest and a
source audit, not from intent.

## Audit result

Turnus's own code contains **one** network call (the advert kill-switch config
fetch) and **zero** reads of any device identifier. No analytics or
crash-reporting SDK is present. Everything below comes from the Google Mobile
Ads SDK.

The rota, the changed days and the notes are **excluded from Android's automatic
backup and from device-to-device transfer** (`res/xml/data_extraction_rules.xml`
and `res/xml/backup_rules.xml`). That is not a Data Safety field — Android's
backup is not collection by this app — but it is worth knowing when answering
the deletion and retention questions, because it means the only copy that ever
leaves the device is one the user saved deliberately.

Permissions the ads SDK merges into the release manifest:

- `com.google.android.gms.permission.AD_ID`
- `android.permission.ACCESS_ADSERVICES_AD_ID`
- `android.permission.ACCESS_ADSERVICES_ATTRIBUTION`
- `android.permission.ACCESS_ADSERVICES_TOPICS`
- `android.permission.INTERNET`, `ACCESS_NETWORK_STATE`

## Answers

**Does your app collect or share any of the required user data types?**
Yes.

### Device or other IDs
- Collected: **Yes**
- Shared: **Yes** (with Google as an advertising partner)
- Processed ephemerally: No
- Required or optional: **Optional** — users in consent regions may decline
- Purpose: **Advertising or marketing**

### Location — Approximate location
- Collected: **Yes** (derived from IP address by the ads SDK)
- Shared: **Yes**
- Purpose: **Advertising or marketing**

> Declare this even though the app requests no location permission. IP-derived
> coarse location still counts, and omitting it is a common cause of rejection.

### App activity / App info and performance
- Only if you later add analytics or crash reporting. **Today: No.**

### Everything else — personal info, financial, health, messages, photos, files, contacts, calendar
- Collected: **No**

> The user's rota is calendar-shaped but never leaves the device, so it is not
> "collected" under Play's definition, which covers transmission off-device.

> Backup, calendar export and share codes do not change this answer. Each is
> the user handing their own data to an app they chose, through the system
> share sheet or file picker — Turnus transmits nothing itself. "Files and
> docs" stays **No** for the same reason, and because the app holds no storage
> permission: the file picker grants it exactly one file, the one selected.

## Other declarations

- **Data is encrypted in transit:** Yes (the ads SDK and the config fetch use HTTPS)
- **Users can request deletion:** Yes — uninstalling or clearing app data removes
  everything the app stores; ad data is requested from Google
- **Committed to the Play Families Policy:** No (not a children's app)
- **Independent security review:** No

## Also required

- **Privacy policy URL** — host `privacy-policy.md` and link it
- **Ads declaration** — the listing must say the app contains ads
- **Trader status (EU DSA)** — ad-funded means trader; the address given is
  published publicly on the listing
- **Target audience** — adults; not designed for children
