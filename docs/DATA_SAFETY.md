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
- Processed ephemerally: **No** — Google retains ad-request data beyond the request
- Required or optional: **Required**
- Purpose: **Advertising or marketing** (both collection and sharing)

### Location — Approximate location
- Collected: **Yes** (derived from IP address by the ads SDK)
- Shared: **Yes**
- Processed ephemerally: **No**
- Required or optional: **Required**
- Purpose: **Advertising or marketing** (both collection and sharing)

> Declare this even though the app requests no location permission. IP-derived
> coarse location still counts, and omitting it is a common cause of rejection.

> **Required, not Optional — this was filed differently from how it was first
> drafted here, and the correction is the useful part.** The draft said Optional
> on the reasoning that a user can decline consent. They can, but only where UMP
> says consent is required: `AdGate` calls `loadAndShowConsentFormIfRequired`,
> and the Settings *"Ad privacy choices"* card is gated on
> `privacyOptionsRequired`. Both are essentially the EEA and the UK. A user in
> India or the United States is never offered the choice inside the app.
>
> Approximate location is the clearer case of the two, because it is derived
> from the IP address on the request: even deleting the advertising ID in
> Android settings does not stop it.
>
> The asymmetry decides it. Declaring *Optional* when most users get no choice
> is an over-claim on a public store listing. Declaring *Required* when some
> users do get one understates the app slightly and carries no policy risk.
> Both data types are declared the same way on purpose — one SDK answered two
> ways invites a question with no good answer.
>
> If the consent form is ever shown to every user rather than only where it is
> required, this becomes **Optional** and should be changed back.

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

- **Data is encrypted in transit:** Yes — there are exactly two network paths,
  the ads SDK and the config fetch in `AdConfig`, and both are HTTPS
- **Account creation:** none, and no sign-in with an account made elsewhere
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
