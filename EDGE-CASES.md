# Turnus — Edge cases

What can go wrong with *this* app. Read with `PRD.md` §7, which states the rules
these cases test.

**On "where the fix belongs":** this app has no server (`TRD.md` §2), so almost
every fix is on the device. Where that is the answer, this document says which
*part* — engine, scheduler, a named screen — because "client" alone is not
useful. The three non-device places a fix can live are the **remote advert
config**, the **store listing**, and **nothing needs fixing**.

---

## The ranking

Likelihood × damage. The top six are the ones that decide whether this app is
trusted.

| # | Case | Likelihood | Damage | v1? |
|---|---|---|---|---|
| 1 | Reminders are **off by default** and buried in Settings | Certain | Severe — the headline feature is invisible | **Must** |
| 2 | Aggressive OEM battery management silently cancels alarms | Very likely on vivo/Xiaomi/Oppo/Samsung | Severe — missed shift | **Must** (partly done) |
| 3 | User force-stops the app; alarms die until next manual open | Likely | Severe — silent, indefinite | **Must** (mitigate + explain) |
| 4 | A cloud backup file that has not downloaded reports *"not a Turnus backup"* | Moderate | High — user believes their backup is corrupt | **Must** |
| 5 | Share code mangled in transit by a messaging app | Likely | Medium — the growth loop fails | **Must** |
| 6 | Notification tapped while app is open lands on the wrong screen | Likely | Low, but constant | **Must** — small |
| 7 | Device timezone changes (travel, or a phone bought abroad) | Moderate | Medium — "today" moves | v1.1 |
| 8 | Installing an older build over a newer database | Unlikely | Severe — crash on launch, no route out | v1.1 |
| 9 | DST boundary changes real shift length | Certain, twice a year | Low — documented as rostered | Accepted |
| 10 | Emoji or flag in a shift letter renders as a broken glyph | Unlikely | Low | v1.1 |

---

## 1. Empty and first run

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| First launch, no data | — | Root gate shows the wizard; the calendar is unreachable until a rota exists. **Already correct** — verified by deleting everything on-device and landing on the welcome panel | None |
| First frame before the database answers | Setup screen flashes past, then the calendar | A blank frame. **Already correct** and deliberate | None |
| No notification permission ever granted | Alarms fire, post nothing, user never learns | Permission is asked at the moment the reminders switch is turned on; if refused, the switch turns itself back off rather than lying. **Already correct** | None |
| **Reminders off by default** | User never discovers the app has reminders at all | Offer once on the calendar after setup completes: one dismissible card, one button | **Month screen** — see ranking #1 |
| Shift editor with no shifts | Empty screen, no way to build a rota | Four shifts are seeded on first run, and seeding is safe to attempt twice. **Already correct** | None |
| Hours card with zero shifts in the month | *"0 hours · 0 shifts"* — worse than nothing | The card hides itself entirely. **Already correct** | None |
| Next-shift card before the first read | Skeleton flash | Absent until it has an answer. **Already correct** | None |

**Empty list vs filtered-to-zero:** this app has no search and no filters, so the
distinction has no instance. The nearest thing is the year view when the whole
year is off — a rota with no working days is refused at setup (*"Add at least one
working day"*), so it cannot arise.

**An item deleted while open elsewhere — real and unhandled:** open the day sheet,
which lists every shift; in another entry point delete a shift that no pattern or
override uses (so `RESTRICT` allows it); tap the now-deleted shift in the still-open
sheet. **Unhandled:** an unknown-shift error surfaces as a snackbar. **Should:** the
sheet's shift list is driven by an observed flow and should recompose the row away.
**Fix: Day sheet.** *Unverified — I have not reproduced this; it needs a test.*

## 2. Network

There is exactly one request in the entire app: fetching the advert config
(`TRD.md` §4). No user data is ever transmitted. That makes this whole category
smaller than it looks and much less dangerous.

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| Offline before the request | Adverts vanish, revenue lost | Cached values applied first, network consulted after. **Already correct** | None |
| Offline *during* the request | Same | Fails open — adverts stay on | None |
| **Captive portal** — DNS resolves, a login page is returned | HTML parsed as JSON | Parse failure is treated as "all on". **Already correct** — fails open by construction | None |
| Request hangs | UI thread blocked | 8-second timeout, on a background dispatcher | None |
| Response arrives after the user left the screen | — | The result only sets advert flags; there is no screen to be stale | None |
| **The same write sent twice** | — | **No instance.** There are no writes over the network | None |

**The honest risk here is the opposite of the usual one:** the failure mode is
*silent success*. If the host rate-limits us (`TRD.md` §9), everything keeps working
and only the kill switch quietly stops being reliable. Nobody sees an error.

## 3. Lifecycle

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| OS kills the app mid-wizard | Setup restarts from the top, a hand-built cycle lost | Wizard state lives in a view model that survives configuration change; **process death still loses it** | **Accepted for v1** — see §12 |
| Killed mid-restore | Half a rota | Restore is one transaction: it either happened or it did not. **Already correct** | None |
| Backgrounded an hour, reopened | Stale "today" across midnight | Reminder flows re-read the date on resubscribe. The **Settings screen's "today is Night" caption is read once** and can be a day stale | **Settings** — cosmetic, v1.1 |
| Deep link from cold start | — | **No instance.** The app declares no deep links | See §10 |
| **Notification tapped while app is open on another screen** | Lands on Settings or the year view | Should land on the month view, ideally on the day the reminder is about | **ReminderReceiver + MainActivity** — ranking #6 |
| Rotation mid-edit | Draft lost | Sheet and draft state live in view models. **Already correct** | None |

**"Mid-upload" and "mid-payment" have no instance** — the app uploads nothing and
takes no money (`PRD.md` §5).

## 4. Auth

**No instance, in every row.** There are no accounts, no tokens, no sessions, no
other devices and no permissions server (`TRD.md` §2).

The one structurally analogous case is worth stating because it *is* handled:
**an alarm fires for a shift that no longer exists** — the rota was changed or
deleted after the alarm was scheduled. The receiver re-reads the rota at fire time
and drops anything that no longer says the user is working, so a stale alarm
cannot post a wrong reminder. This is the "expired token at request time" pattern,
solved by never trusting the thing that was scheduled.

## 5. Data and concurrency

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| Two devices editing one rota | — | **No instance.** No sync (`PRD.md` §5) | None |
| Queued offline write conflicting on sync | — | **No instance** | None |
| Row deleted while being edited | See §1 | | |
| List changing under pagination | — | **No instance.** Nothing paginates; a month is 42 cells | None |
| **Shift ordering ties** | Two shifts sharing a `sort_order` — reachable via a hand-edited backup — order undefined, list appears to shuffle between reads | Order by `sort_order`, then `id` | **Shift DAO** — `TRD.md` §3, small |
| Zero: a 1-day cycle | Divide-by-zero or a blank grid | Valid — "work every day". Guarded by a minimum of one slot | None |
| Zero: an all-off cycle | A calendar with no work | Refused at setup with *"Add at least one working day"*. **Already correct** | None |
| Negative: browsing before the anchor | Negative modulo → index out of bounds → crash | True floor-modulo. **The single most likely bug in this codebase**, property-tested | None |
| Enormous: 40-day cycle, decade of changed days | Slow reads | Bounded at 40 slots; thousands of override rows are trivial for SQLite | None |
| Enormous: a note of 10,000 characters | Stored, backed up, no limit anywhere | **Unbounded today.** Field is single-line with no cap | **Day sheet** — cap it, v1.1 |
| Boundary: shift ending exactly at midnight | Off-by-one into the next day | Duration 1..1440 inclusive; "crosses midnight" is `start + duration > 1440`, so exactly 1440 does not | None |
| Boundary: break exactly equal to shift length | A shift nobody works | Refused — break must be strictly shorter | None |

## 6. Input

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| Empty shift letter or name | A blank cell in the grid | Refused: *"A shift needs a letter and a name"* | None |
| **Whitespace-only name** | A shift that looks blank | Trimmed before validation, so it is caught as empty. **Already correct** | None |
| 10,000-character note | Accepted silently | See §5 | Day sheet |
| **Emoji as a shift letter** | `take(2)` splits a flag or skin-tone sequence mid-sequence → a broken glyph in every calendar cell | Take two *grapheme clusters*, or refuse non-letter input | **Shift editor** — ranking #10 |
| Right-to-left shift names | Mixed-direction text in a 44dp cell | The manifest supports RTL; the grid's column order follows the locale's first day of week | Believed correct — **untested**, flag for a v1.1 pass |
| Apostrophe in a name | SQL injection, or a broken calendar file | Parameterised queries throughout; the calendar writer escapes per RFC 5545 with a deliberately careful escape order | None |
| Pasted formatted text into a note | Markup stored verbatim | Stored as plain text; nothing renders it as markup | None |
| **A file that is not what its extension claims** — picking a photo at the restore prompt | Reading a video into memory as text | Read is capped at 4 MB and aborts; result is *"That file is not a Turnus backup"* | None |
| **A pasted share code with invisible characters** — messaging apps insert soft line breaks and zero-width characters | Decode fails; user is told their workmate's code is broken | Strip whitespace and non-token characters before decoding; the parser already tolerates surrounding chatter and URLs | **Code entry** — ranking #5 |

## 7. Device

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| **Storage full during a backup write** | Truncated file that looks like a backup | Write mode truncates, and the failure surfaces as an error. A *partial* write would still parse-fail on restore rather than restore garbage | Believed correct — **untested** |
| Storage full during a database write | Room throws; the edit is lost | Repository failures surface as a snackbar rather than a crash | None |
| Largest system font | Shift letters clipped, arrows destroyed | The grid caps its own font scale at 1.3×; controls use minimum sizes rather than fixed | None |
| Smallest screen | Grid overflows | Grid fits to both axes with a minimum cell height, and scrolls when the floor wins | None |
| Rotation mid-action | See §3 | | |
| **Permission denied permanently** (notifications) | Switch says on, nothing arrives | Switch turns itself off; a card explains and offers to open system settings | None |
| Exact-alarm permission never granted | Reminders up to an hour late, silently | A card says exactly that, in those words, and offers the setting | None |
| Low-power mode / Doze | Alarms deferred | Alarms use the allow-while-idle path; inexact fallback when exact is not permitted | None |
| Genuinely slow device | Jank on the month grid | Reads are asynchronous; the grid is 42 cells | None |

## 8. Money

**No instance.** The app takes no payment, has no purchases and computes no pay
(`PRD.md` §5). Every row in this category — double-tap on pay, charge without
confirmation, duplicate webhook, refund, lapsed subscription — is structurally
impossible.

This is the direct benefit of the no-paywall commitment: an entire class of the
hardest bugs in mobile software cannot occur here.

## 9. Abuse

Small, because there is no server, no accounts, no user-generated content anyone
else can see, and nothing to scrape.

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| A malicious share code crafted by hand | Crash, or a corrupt rota | Decode is total: every malformed input returns a typed result. Referenced shifts are validated before any write | None |
| A share code encoding an absurd cycle | 10,000-day cycle stored | **Unverified:** import should apply the same 40-day ceiling the builder enforces | **Import path** — check, v1.1 |
| Offensive text in a shared rota name | Arrives on a workmate's phone | Accepted: the recipient chose to paste a code from someone they know, and moderation implies a server we do not have | Accepted |
| Someone reading a rota off an unlocked phone | Shift pattern and notes visible | Accepted: no app lock in v1. The device lock is the security boundary | Accepted — see §12 |
| Advert fraud / click farming | Account suspension | Debug builds are hard-wired to test units so a developer cannot generate live impressions | None |

---

# The categories the nine did not cover

These are where a rota app actually fails.

## 10. Time, clocks and calendars

The category with the highest density of real bugs for this product.

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| **DST transition during a night shift** | Hours total moves by an hour twice a year and looks like a bug | Totals are rostered, not elapsed, and the UI says "Rostered". A reminder for a shift starting in the skipped hour clamps to the transition instant rather than jumping forward an hour | None |
| **Device timezone changes** (travel; a phone set up abroad) | "Today" shifts by a day; the ringed cell is wrong | Rota dates are zone-free, so the *rota* is unaffected — only "which day is today" moves, which is arguably correct. **But reminders are scheduled from wall-clock times and are not rescheduled on a timezone change** | **Scheduler** — listen for the timezone-change broadcast; ranking #7 |
| **Device clock set wrong or manually changed** | Alarms fire at the wrong time or not at all | Nothing detects this. Android reschedules nothing on a time change either | v1.1 — the time-changed broadcast, same fix as above |
| Crossing local midnight with the app open | Yesterday stays ringed | Reminder and outlook flows re-read the date when resubscribed; the **month grid's `today` is captured per state emission** and refreshes on return to foreground | Believed correct — **untested overnight** |
| Year boundary / leap year | Off-by-one in the year view | Day counts are civil-day arithmetic with no special-casing needed; property-tested across year and leap boundaries | None |
| Locale first-day-of-week changes while running | Grid keeps Sunday-first after switching to a Monday-first locale | Locale is pushed in from the composition, not captured once — a view model outlives the recreation a locale change causes | None |
| A rota anchored decades ago or in the future | Overflow or absurd offsets | Day numbers are 64-bit; floor-modulo handles arbitrary negative offsets | None |

## 11. Alarms and background execution

**This category decides whether the product's headline feature works.** On the
devices most common in this app's target markets, the default answer is "not
reliably".

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| **OEM battery management kills the app** (vivo, Xiaomi, Oppo, OnePlus, Samsung) | Reminders stop, silently, forever | A Settings card names those manufacturers explicitly and offers to open app settings. **This is the best that can be done from inside the app** | Partly done — **also belongs in the store listing** |
| **User force-stops the app from the app switcher** | All alarms cancelled; the boot receiver will not fire until the app is manually opened again | Nothing can fix this from inside a stopped app. It must be *explained* where people will read it | **Store listing + Settings copy** — ranking #3 |
| Reboot | Alarms lost; Android does not tell the app | A boot receiver reschedules; several manufacturer-specific boot actions are registered alongside the standard one | None |
| **App updated from the store** | Alarms cleared by the OS | The package-replaced broadcast is registered and reschedules | None |
| Exact-alarm permission revoked *after* being granted | Silent downgrade to inexact | The Settings card re-reads permission state on resume rather than caching it | None |
| Notification permission revoked while the app is closed | Alarms fire, post nothing | Receiver checks the permission at fire time, with the API-level guard that stops it dropping every reminder on Android 8–12 | None |
| More than 30 days without opening the app | The alarm window runs out | A daily background job tops the window up; the window is also rebuilt on open and on boot | None |
| Two reminders for the same day | Duplicate notifications | The pending-intent request code is the day number, and the day travels in the intent *data* — extras are ignored for equality, which is the trap here | None |

## 12. The rota domain itself

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| **The user's real rota changes and they do not update the app** | The app is confidently wrong; they miss a shift | Unfixable by any app. Mitigated by the day editor and the ±1-day nudge | Accepted — `PRD.md` §7 |
| Rota is a day out | Every future day wrong | One field, applied instantly, keeping every changed day and note. This is why generated days are never stored | None |
| Changing the rota while changed days exist | Old exceptions land on unrelated days | Deliberate: the rota editor **reuses the pattern id** so changed days and notes survive. Whether that is right for a *different* rota is genuinely arguable | Accepted, deliberately — worth revisiting |
| Deleting a shift still used by the rota or a changed day | Blank cells nobody can explain | Refused, naming what still uses it and how many days | None |
| Process death mid-wizard | A hand-built 40-day cycle lost | View-model state does not survive process death. Accepted for v1: the window is small and the loss is recoverable by redoing it | Accepted |

## 13. Widget

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| Rota changed in the app | Widget shows yesterday's answer | Refreshed at the same moment alarms are rebuilt — when the user leaves | None |
| Widget placed, then "delete everything" | Widget shows a rota that no longer exists | **Unverified.** It should fall back to the set-up prompt | Check — v1.1 |
| Two widgets on the home screen | One updates, one does not | Both are updated; the app and widget share one repository instance because two database handles on one file go blind to each other's writes | None |
| Day rolls over at midnight | Yesterday's shift on the home screen until the app is opened | The daily job refreshes it | None |
| Launcher never renders the picker preview | Widget looks broken before it is placed | **Known, unresolved.** The placed widget works; the picker thumbnail does not render on Funtouch or Pixel launchers despite three different configurations | Open — parked |

## 14. Interop and sharing

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| Code from a **newer** app version | Partially parsed → a wrong rota | Reported as *"made by a newer version, update the app"*. A partially understood rota is worse than a refused one | None |
| **Code broken across lines by a messaging app** | Decode fails, user blames the sender | Tolerate whitespace and invisible characters before decoding | **Code entry** — ranking #5 |
| Code pasted with the whole surrounding message | Fails | Already tolerated — the parser finds the token in chatter or a URL | None |
| Backup from a **newer** version | Silently ignores fields it does not know | Refused with a clear message, by format version | None |
| Backup from an **older** version | Missing fields crash the parser | Absent fields default — which is how a backup written before unpaid breaks existed still restores today | None |
| **A cloud file that has not downloaded** picked at the restore prompt | *"That file is not a Turnus backup"* — wrong and alarming | Distinguish a read failure from a parse failure and say *"That file could not be read — it may still be downloading"* | **Backup file reader** — ranking #4 |
| The file changes between picking and confirming (synced folder) | Restores different bytes than were shown | The decoded snapshot is held and applied, not re-read. **Already correct** | None |
| Calendar export into different calendar apps | Duplicate events on re-export | Stable per-day UIDs so re-import updates | None |
| Share-sheet grant expires before the receiving app reads it | Export fails silently | Per-URI, per-share grant issued at share time | None |

## 15. Adverts and consent

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| Consent withdrawn mid-session | Adverts keep serving | Withdrawal re-checks and disables slots; the config re-application also re-checks consent, which was a real bug found by re-reading rather than running | None |
| Ads SDK fails to initialise | Blank space or a crash | Slot height is reserved regardless, so the calendar never reflows | None |
| Kill switch host unreachable | Adverts disabled, revenue to zero | Fails open | None |
| An advert fills after the user leaves the screen | Leaked view | The banner is destroyed on release | None |
| **Consent form shown before the user has seen anything** | Uninstall — a wall of legal text as a first impression | Deferred until the calendar is populated | None |

## 16. Upgrade, downgrade and migration

| Trigger | Unhandled | Should happen | Fix |
|---|---|---|---|
| Schema migration on update | Data loss, unattended, unrecoverable | The chain is complete, never adjacent-only; the database file is copied aside first, and only when a migration is pending. **Verified end-to-end on a device with a real minified build** | None |
| **Installing an older build over a newer database** | Room throws on open; the app cannot start and the user cannot get back in | Detect a future schema version and show something better than a crash — even *"this version is too old, update"* | v1.1 — ranking #8 |
| Migration fails halfway | Corrupt database | Migrations run in a transaction; the pre-migration copy is the backstop | None |
| Restore of a backup taken before a migration | Missing columns | Backup format versioning is independent of schema version, precisely so this works | None |

---

## What must be handled before v1

1. **Surface reminders after setup** — the headline feature is currently off and hidden.
2. **Explain force-stop and battery management** in the store listing, not only in Settings.
3. **Tolerate mangled share codes** — strip whitespace and invisible characters before decoding.
4. **Distinguish "could not read" from "not a backup"** at the restore prompt.
5. **Route notification taps to the month view.**
6. **Add the `id` tiebreaker to shift ordering.**

Items 3–6 are each small. Item 1 is a card and a preference. Item 2 is copy.

## What is deliberately accepted for v1

- The app can be confidently wrong if someone's real rota changes and they do not tell it. No app fixes this.
- DST changes real shift length; totals stay rostered.
- Process death mid-wizard loses a hand-built cycle.
- No app lock — the device lock is the security boundary.
- No moderation of shared rota names — it implies a server we have promised not to have.
- The widget picker thumbnail does not render.
