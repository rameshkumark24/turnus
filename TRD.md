# Turnus — Technical Requirements

How we build what `PRD.md` describes. Read the PRD first; every choice here
cites the requirement that forced it. Where I am guessing, it says so.

---

## 1. The stack

### The argument against the default shape

The default for a product like this is an app, an API, a database and a cloud
account. **Turnus should have none of the last three, and that is the most
important technical decision in the project.**

PRD §1 promises no account and no server. PRD §7 states that nobody but the
user can see anything. PRD §4.13 funds the app with adverts and PRD §5 forbids
any paid tier. Those three together mean:

- there is no user identity to authenticate, so no auth system;
- there is no entitlement to protect, so nothing needs server-side verification;
- there is no shared data, so no multi-tenancy and no row-level security;
- and there is no per-user cost, so the marginal cost of a user is zero.

A server here would add an attack surface, a subpoena target, a recurring bill,
an uptime obligation and a privacy claim we could no longer make — in exchange
for nothing the product needs. **If a future change makes a server look
necessary, that is a signal to re-read PRD §5 before writing any code.**

### The choices

| Choice | Why (PRD reference) | Cost at 1,000 users |
|---|---|---|
| **Native Android, Kotlin** | PRD §4.7 needs exact alarms and boot-persistent scheduling; §4.8 needs a home-screen widget; §4.2 needs a grid that fits the viewport at any font scale. All three are first-class native and awkward-to-impossible in a cross-platform wrapper. | £0 |
| **Embedded SQL database, on device** | PRD §7 requires referential integrity between changed days and shifts, and transactional writes so a restore cannot half-apply. | £0 |
| **Pure-JVM module for the date engine, no Android dependency** | PRD §7's rules are where the expensive bugs live. Isolating them lets thousands of generated cases run in milliseconds on every build instead of needing an emulator. | £0 |
| **One static JSON file over HTTPS** | PRD §4.13 requires switching an advert slot off without a release. | £0 (see §9) |
| **Google Mobile Ads + the consent SDK** | PRD §4.13, and the consent requirement in §8. | £0 to us; revenue-positive |
| **No analytics, no crash reporting** | PRD §8 requires the in-app claim, the policy and the store declaration to agree. Adding either later falsifies a published document. | £0, and a real cost: we are blind to crashes in the field |

**The honest cost at 1,000 users is a rounding error.** Two adverts, two slots,
no infrastructure. The scarce resource is not money, it is the maintainer's
attention — which is why PRD §10 lists "what makes year three worth it" as an
open question rather than pretending the economics are solved.

**Where I am guessing:** that 1,000 users generate enough advert revenue to
matter at all. They almost certainly do not. Nothing in the architecture
depends on the answer.

## 2. The shape

**Everything runs on the device.** There is no server to run anything on.

### What the device decides, and why that is safe

The rule "anything a user could tamper with belongs on the server" exists to
protect two things: entitlements you sold, and other users. Turnus has neither.
Going through what a determined user could change:

| They could tamper with | Who is harmed | Therefore |
|---|---|---|
| Their own rota, shifts, notes | Only themselves | Device is correct |
| Consent state | Nobody; clearing it re-prompts | Owned by the consent SDK, on device |
| The advert kill switch (by blocking the host) | Nobody — it fails **open**, so blocking it leaves adverts **on** | No incentive exists to tamper |
| A backup file, by hand-editing it | Only themselves | Validate at the import boundary, reject with a readable reason |
| A share code | The receiver, mildly — a malformed code | Decode is total: every malformed input is a typed result, never a crash |

**There is no price, no limit, and no permission that a user could bypass to
their advantage, because nothing is sold and nothing is metered.** This is a
security dividend of the free model and it should be stated in review whenever
someone proposes a paid tier.

### The trigger that changes this answer

**The moment anything becomes paid, an on-device flag is trivially bypassable**
and we would need the store's server-side purchase verification at minimum.
That is a second, concrete reason for the no-paywall commitment beyond
positioning.

### The one network call

`GET` a static JSON file. It carries no identifier, no query string and no rota
data. As with any request, the host sees the originating IP. It fails open. It
is the only thing the app sends anywhere.

## 3. The data model

### Choosing the shape

**Chosen: a small relational schema in an embedded SQL database.** Considered
and rejected:

- **Document store.** The strongest reason for relational here is that PRD §7
  requires a changed day to be unable to reference a shift that does not exist,
  and requires deleting a rota to take its changed days with it. Those are
  foreign keys doing real work. A document store would push both into
  application code, which is exactly where they were already broken once.
- **Event log / event sourcing.** Genuinely tempting: the product *is*
  "configuration plus a sparse set of exceptions", and PRD §7 forbids storing
  generated days, so the app is already halfway to a fold-over-events design.
  Rejected because exceptions are keyed by day with last-write-wins semantics —
  there is no interesting history to replay, so a log buys audit capability
  nobody asked for at the cost of replaying on every read.
- **A local-first sync database (CRDTs and similar).** These exist to reconcile
  concurrent writers. PRD §5 rules out sync and §7 states there is exactly one
  writer. Rejected as solving a problem we have promised not to have.
- **Plain files.** Considered for the config-plus-exceptions shape. Rejected on
  transactional integrity: PRD §4.11 requires a restore that cannot half-apply.

**The most important schema decision is a negative one: there is no table of
generated shifts.** PRD §7 requires every cell to be recomputed on read.

### Entities

**`shift_type`** — a kind of shift.

| Field | Type | Empty? | Notes |
|---|---|---|---|
| `id` | text, PK | no | stable; the seeded four use fixed ids so presets resolve without translation |
| `code` | text | no | 1–2 chars; unique index, compared case-insensitively |
| `name` | text | no | |
| `color` | integer | no | packed ARGB, not a string |
| `start_minute` | integer | **yes** | 0–1439; null together with duration |
| `duration_minute` | integer | **yes** | 1–1440; may cross midnight |
| `break_minutes` | integer | no | default 0; must be < duration |
| `is_working` | boolean | no | |
| `sort_order` | integer | no | |
| `created_at`, `updated_at` | integer | no | epoch **milliseconds** |

**`pattern`** — a rota.

| Field | Type | Empty? | Notes |
|---|---|---|---|
| `id` | text, PK | no | |
| `name` | text | no | user-visible |
| `anchor_day` | integer | no | **day number**, not a timestamp |
| `slots` | text | no | delimited shift ids, `-` for off |
| `slot_count` | integer | no | denormalised length of `slots` |
| `is_active` | boolean | no | at most one true (PRD §7) |
| `created_at`, `updated_at` | integer | no | epoch milliseconds |

**`day_override`** — one exception.

| Field | Type | Empty? | Notes |
|---|---|---|---|
| `pattern_id` | text, PK part | no | FK → `pattern` |
| `day` | integer, PK part | no | **day number** |
| `shift_type_id` | text | **yes** | FK → `shift_type`; null means explicitly off |
| `overrides_shift` | boolean | no | false = note only, shift still follows the rota |
| `note` | text | **yes** | the most sensitive field in the app |
| `created_at`, `updated_at` | integer | no | epoch milliseconds |

The composite key includes `pattern_id` even though PRD §5 defers multiple
rotas — adding it now costs nothing and adding it later is a migration.

**`app_meta`** — key/value settings (reminder preferences today).

### Ownership

**Every record is owned by the device's app sandbox, and no field proves it —
correctly.** There is no user id column and there must not be one: with no
accounts, an owner column would be a field that always holds the same value,
which is not a check, it is decoration.

**Flagging it explicitly, as instructed:** *ownership is enforced by the
operating system, not by the data.* The mechanism is the Android application
sandbox — a per-app Linux UID, a private data directory, and a database file
unreadable by other apps on a non-rooted device. There are no exported content
providers. There is no row-level security because there are no rows belonging
to anyone else.

**The three places ownership actually crosses a boundary**, and what enforces it
there:

1. **The backup file** — written through the system file picker, which grants
   access to exactly one user-chosen file. The app needs no storage permission,
   so it cannot read anything else. Once written, the file is the user's
   problem, which PRD §4.11 accepts deliberately.
2. **The calendar export** — handed out as a temporary per-share grant to one
   app the user picked, not a world-readable file. Contains no notes (PRD §8).
3. **The share code** — contains the cycle, its alignment and its name. No
   identifier, no changed days, no notes.

Within the app there is exactly one writer, so no locking or optimistic
concurrency is needed. **If that ever stops being true, this section is wrong.**

### Delete behaviour

| Relationship | On delete | Why |
|---|---|---|
| `pattern` → `day_override` | **CASCADE** | Exceptions to a deleted rota are meaningless |
| `shift_type` → `day_override` | **RESTRICT** | PRD §4.5: deleting a shift still in use must fail with a message naming what uses it, not silently blank days |
| `pattern.slots` → `shift_type` | **No FK possible** — `slots` is one delimited text column | Enforced in application code *inside the same transaction as the write*, because SQL cannot police ids inside a string |
| "Delete everything" | Manual order: overrides, then patterns, then shifts, then settings | The RESTRICT above makes any other order fail |

### Exact types for things humans count

- **Rota dates:** a value class wrapping a signed integer day count from a fixed
  epoch. Not a date object, not a string, no zone, no clock. PRD §7 makes this
  the highest-consequence type decision in the project; the value class exists
  so the compiler rejects mixing it with a timestamp.
- **Audit timestamps:** epoch milliseconds, as plain integers, named so they
  cannot be mistaken for day numbers.
- **Times of day and durations:** integer minutes. Never floats — 7.5 hours as
  a float is a rounding bug waiting for a monthly total to expose it.
- **Money:** **there is none, anywhere.** PRD §5 excludes pay permanently. If
  money is ever added, it must be integer minor units, never a float or a
  formatted string.

### The queries this app actually runs

| Query | Made fast by |
|---|---|
| Changed days for one rota in a date range | Primary key `(pattern_id, day)` — the range scan is a key prefix |
| The active rota | Trivial: the table holds one row per rota, and there are one or two |
| All shift types, ordered | Index on `sort_order`; the table has single-digit rows |
| Count of changed days using a shift (before delete) | Index on `shift_type_id` |
| All rows, for a backup | Full scan of four small tables, once, inside one transaction |

The volumes are tiny — a decade of heavy use is low thousands of changed days —
so the performance risk is not query cost but **doing the work on the main
thread**. Every read is asynchronous and every write is transactional.

### Stable sorts

**There is no pagination anywhere.** A month is 42 cells and a year is 365; both
are fetched whole. So the classic duplicate-across-pages bug cannot occur.

**But there is a real instability, and it should be fixed:** shift types are
ordered by `sort_order` alone, with no tiebreaker. Creation assigns
`max + 1` so ties cannot arise normally — but a restore from a hand-edited
backup can produce two rows with the same `sort_order`, and their relative
order would then be undefined and could change between reads, making the shift
list appear to shuffle. **Recommendation: order by `sort_order`, then `id`.**
Small, and it removes a whole class of "why did my shifts move" report.

## 4. The API

There is one, it is a static file, and it is not ours to run.

| | |
|---|---|
| **Method / path** | `GET` a fixed JSON file over HTTPS, hosted as a static asset in the project's own public repository |
| **Auth** | None. It is public, and it must be — an authenticated config would need a credential in the app, which is not a secret |
| **Request** | No query string, no headers carrying identity, no body |
| **Response** | An object of independent booleans, one per advert slot: `{ "bannersEnabled": true, "nativeEnabled": true }` |
| **Unknown fields** | Ignored |
| **Missing fields** | Treated as `true` |
| **Any failure — timeout, 404, malformed, offline** | Treated as all-`true`. **Fails open** (PRD §7) |
| **Frequency** | Once per app launch |
| **Caching** | Last known values persisted locally and applied at launch before the network is consulted, so an offline launch still honours the last known switch |

There are no other endpoints. There is no user data endpoint, because there is
no user data leaving the device (PRD §7).

## 5. The old-build rule

The template's framing — "this API must serve an app build from eight months
ago" — applies here in four places, three of which are not an API. **Every one
of these is a wire format that a stranger's out-of-date install will read.**

**1. The advert config file.** The only literal instance. An eight-month-old
build will fetch this exact file forever. Rules: *fields may be added, never
removed or repurposed; unknown fields are ignored; absent fields mean "on".*
Removing a field silently turns that slot permanently on for every old build.
There is no versioning and there should not be — the fail-open default is the
compatibility mechanism.

**2. The share code.** Versioned by a leading tag. A code from a newer build
arriving at an older install is reported as *"made by a newer version, update
the app"* rather than partially parsed — PRD §7's "a partially understood rota
is worse than a refused one". New format versions get a new tag; the decoder
keeps every version it has ever understood.

**3. The backup file.** Carries a format identifier and an integer version.
Rules: a *higher* version than the reader understands is refused with a clear
message; a *lower* one is read, with absent fields defaulting — which is how a
backup written before unpaid breaks existed still restores correctly today.
Fields may be added with a safe default; existing fields may never change
meaning.

**4. The database schema.** Migrations are the mechanism. Every migration ever
written stays in the chain forever and none is removed, because users skip
releases — someone will go from the first version straight to the seventh, and
the chain must be complete rather than merely adjacent. Migrations are additive
where possible. The database file is copied aside before any migration runs,
and only when one is pending: it is the only safety net that exists for an
operation that runs unattended, once, on a stranger's phone, and cannot be
rolled back because the release that ran it cannot be un-shipped.

## 6. State

**Server state:** none.

**Persisted device state:**

| What | Where | Why there |
|---|---|---|
| Rota, shifts, changed days, notes, reminder settings | The database | Transactional; relational integrity |
| Last known advert switch values | Simple key/value preferences | Two booleans; a table would be ceremony |
| Advertising consent | Owned and stored by the consent SDK | Not ours to model; the SDK is the source of truth |
| The pre-restore undo copy | A single file in private app storage | Must survive process death; deleted by "delete everything" (PRD §4.12) |

**Nothing goes in a secure/encrypted store, because the app holds no
credential.** There is no token, no password and no key. If one ever appears,
it belongs in the platform keystore and not in preferences.

**Local UI state** — the visible month, wizard step, open sheet, in-progress
draft — lives in view models and is restored across configuration changes. It
is never persisted to disk.

**Derived state that must never be stored:**

- **The resolved calendar itself.** PRD §7 forbids it outright.
- **Hours totals.** Recomputed from the rota and the shift definitions. Storing
  them would make a shift's times and last month's total disagree the moment
  someone edits a shift.
- **"When am I next off"** and the year's longest break. Both are scans over
  resolved days.
- **Whether today is a working day.** Always computed, never cached across a
  midnight boundary.

## 7. Third parties

**Google Mobile Ads.** *Why:* PRD §4.13 — it is the entire business model.
*Cost:* none to us. *When it is down:* the advert slot stays empty at its
reserved height; the calendar never reflows and nothing else is affected.
*Native code:* **yes.** It is a native dependency, so any change to it requires
a store release. Android has no over-the-air code update path in any case, so
"ends OTA updates" is not a marginal cost here — **every** change to this app
ships through the store. The remote kill switch (§4) exists precisely because
that round trip is too slow to be the only lever.

**Google's consent SDK.** *Why:* PRD §8 requires consent where law requires it,
and requires it to be changeable later. *Cost:* none. *When it is down:*
consent cannot be resolved, so no advert may be requested; the app must remain
fully usable, which PRD §8 mandates independently. *Native code:* yes.

**A public static file host, for the advert config.** *Why:* §4. *Cost:* none.
*When it is down:* fails open. *Native code:* no — plain HTTPS with the
platform's own client, deliberately: adding a networking library for one GET
would be a dependency and an update obligation for a single request.

**That is the complete list.** No analytics, no crash reporting, no image
loader, no dependency-injection framework, no date library, no networking
library. PRD §8 makes the first two a published claim rather than a preference.

## 8. The five decisions that are expensive to reverse

1. **No server, no accounts.** *Forecloses:* shift swapping between colleagues,
   two-way calendar sync, live sharing, cross-device sync, any server-verified
   entitlement, and any feature needing a second user. *Cost to reverse:* a
   backend, an auth system, a privacy policy rewrite, new store declarations,
   and the loss of the claim the product is sold on.

2. **Nothing is ever locked behind a payment.** *Forecloses:* subscriptions, a
   pro tier, ad-removal purchases. *Cost to reverse:* not just pricing — see §2,
   a paid flag on-device is bypassable, so it drags in server verification and
   therefore decision 1.

3. **Generated days are never stored.** *Forecloses:* per-day data that is not
   an exception, and any feature wanting to annotate a generated day without
   pinning it. *Cost to reverse:* everything downstream assumes recomputation;
   reversing means a materialised calendar and a rebuild path for every edit.
   *In exchange:* correcting a misaligned rota is one field, not a migration.

4. **Rota dates are a zone-free day count.** *Forecloses:* sub-day precision in
   rota arithmetic, and rotas that shift with the clock rather than the
   calendar. *Cost to reverse:* it is the type running through the engine, the
   schema, the share code and the backup format.

5. **The share code carries shift letters rather than internal ids.** *Why it
   matters:* a code has to mean the same thing on a phone that has never seen
   the sender's data. *Forecloses:* sharing anything identified by internal id —
   colours, per-shift settings — without a format version bump. *Cost to
   reverse:* every code already in circulation in someone's chat history.

## 9. What breaks at 10×

**The first specific thing: the advert config fetch.**

Every install performs one `GET` against a public code-hosting service on every
app launch. That service is designed for source browsing, not as an application
config CDN, and it applies rate limiting and abuse controls that a sustained
pattern of identical requests from thousands of devices can trip.

**When it trips, the app keeps working perfectly** — it fails open, so adverts
stay on and users notice nothing. **What breaks is the kill switch itself**: the
one lever for turning a misbehaving advert slot off without a store release
stops being reliable at exactly the scale where you would need it. It fails
silently, and the failure is invisible from the user side.

*The fix, when it is needed:* move the file to a real static host or CDN, and
fetch at most once a day rather than once a launch. Both are small. **The
trigger to watch is install count, not error rate**, because the failure does
not surface as an error anyone sees.

**Second, and further out:** a user with a decade of changed days accumulates
low thousands of rows. Well within the store's limits, but the backup encodes
every one into a single string in memory. That is fine at thousands and would
need streaming at hundreds of thousands, which no real user will reach.

**Not a concern at any scale:** query performance, storage, server load,
concurrency. There is no server, and the per-device working set is a few
hundred kilobytes.
