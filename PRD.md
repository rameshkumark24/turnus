# Turnus — Product Requirements

The permanent product reference. Read this before touching code. It assumes no
knowledge of any prior conversation.

This document is *what* and *why*. Never *how* — no frameworks, no code, no
data structures. `TRD.md` covers those, and every choice in it traces back to
something here.

---

## 1. What this is

Turnus is an offline calendar for people who work a repeating shift rotation,
telling them which shifts they are on for any day in any year without their
having to count. It is free, funded by two advertising slots, and it holds
everything on the phone: there is no account, no server, and no way for anyone
else — including us — to see a user's rota. It is an Android app for adults who
work shifts, not a tool for the employers who set those shifts.

## 2. The user

**Who.** Someone on a *repeating cycle*: four on four off, DuPont, Pitman,
Continental, Panama. Plant and factory operators, nurses and care staff,
police, fire and ambulance, security, offshore, rail and logistics. They know
their pattern by name or by shape, and it repeats indefinitely.

**Not our user.** Someone whose rota is published weekly by a manager with no
repeating shape — much of retail, hospitality and agency work. They would have
to enter every day by hand and the app would fight them the whole way. This is
a deliberate exclusion, not an oversight (see §5).

**The moment of need.** Three moments, in order of frequency:

1. *"Am I in tomorrow, and what time?"* — usually in the evening, often tired,
   sometimes about to sleep before a night shift.
2. *"When am I next off, and for how long?"* — booking a holiday, a wedding, a
   hospital appointment; deciding whether to take an offered overtime shift.
3. *"How many hours am I on this month?"* — checking a payslip.

**What they do today instead.** This is the real competition, and it is not an
app. Rotas are distributed as a sheet of paper on a noticeboard, a PDF, a
spreadsheet by e-mail, or a photograph of a handwritten wall planner dropped
into a WhatsApp group. The standard advice given to shift workers is to check
the board and save a photo on their phone. That photo — free, instant, already
in the conversation — is what Turnus has to beat.

## 3. The core loop

The repeated action, as the user experiences it. Most sessions are the first
three steps and last under ten seconds.

1. Open the app, or glance at the home-screen widget.
2. See today marked in a month grid, with a plain-English line above it —
   *"Today — Night"*.
3. See what changes next: *"Off from tomorrow · 4 days off"*.
4. **Occasionally:** tap a day to change it — swapped a shift, called in sick,
   picked up overtime — and optionally attach a note.
5. **Occasionally:** look at the whole year to find a long break, or at the
   month total to check hours against a payslip.
6. **In the background, without opening anything:** a notification arrives
   before each shift, at a lead time they chose once.

Setup happens once, before all of this, and is the only place the app asks
anything substantial. It asks *"what are you on today?"* rather than *"what is
your anchor date?"*, because the user knows the answer to the first question
and does not know what the second one means.

## 4. Features

### 4.1 Rota setup

**Does.** Walks a new user from nothing to a populated calendar. Offers named
preset rotations (4 on 4 off, DuPont, Pitman, Panama, Continental, 5 on 2 off,
and a 16-day days/nights cycle), or lets them build a cycle by tapping days.
Then asks which shift they are on *today* and works out the alignment. When
that answer is ambiguous — the same shift appears more than once in the cycle —
it asks which run they are in, showing a preview of each option.

**Done means.** A user who knows only the name or shape of their rota reaches a
correct, populated calendar without being asked for a date, and can see a
two-week preview before committing.

**Does not.** Ask for a job title, an employer, a name, or any personal detail.
Ask for an "anchor date" or "cycle start" in those words. Import a rota from a
photo or PDF.

### 4.2 Month view

**Does.** A grid of the visible month with one cell per day, coloured by shift
and lettered with its code. Today is ringed. Above the grid, today's shift in
words. Below it, what changes next and the month's hours.

**Done means.** The whole month fits the screen without scrolling on a typical
phone, at default font size, in both light and dark themes; and the grid starts
on the correct weekday for the device's locale.

**Does not.** Show other people's shifts. Show more than one rota at once.

### 4.3 Changing a day

**Does.** Tapping a day opens a sheet offering every shift, "off", and a free
text note. A changed day is marked so it is distinguishable from a generated
one, and can be put back to what the rota says. Every change can be undone
immediately.

**Done means.** A swap, a sick day, an overtime shift and an annotated day are
all expressible; a note can be attached *without* pinning that day's shift; and
putting a day back restores exactly what the pattern generates.

**Does not.** Ask why. Categorise the change as sickness, leave or overtime —
the note is free text and the app never interprets it.

### 4.4 Year view

**Does.** Twelve month blocks on one screen, each day a coloured square. Below
them: working days in the year, the longest unbroken break and when it starts.

**Done means.** A user can find their longest break of the year at a glance and
tap a month to jump to it.

**Does not.** Show hours per month, or allow editing.

### 4.5 Shifts

**Does.** Edit the letter, name, colour, start time, length and unpaid break of
each shift type; add and delete shifts. A shift may be untimed — a marker for
rotas that record which shift you are on but not when it runs.

**Done means.** A user whose day shift starts at 06:00 rather than the seeded
07:00 can correct it, and their reminders and exported calendar follow.

**Does not.** Let two shifts share a letter. Let a break be as long as the
shift. Let a shift have a start with no length, or a break with no length.

### 4.6 Hours

**Does.** Shows the visible month's total as hours and shift count, and the
year's total in the year view. The total is rostered time less unpaid breaks.
Shifts with no times are counted as shifts, contribute no hours, and the card
says so rather than under-reporting silently.

**Done means.** The figure matches what the user would get by adding up their
own rostered hours, and is labelled so nobody mistakes it for a timesheet.

**Does not.** Calculate pay or earnings. Calculate overtime. Claim to be a
record of hours worked. Count elapsed clock time across a daylight-saving
boundary (see §7).

### 4.7 Reminders

**Does.** A notification before each shift, at a lead time chosen once from a
short list (from "at the start" up to one day). Individual shifts can be muted.
Untimed shifts are announced the evening before. Reminders survive a reboot.

**Done means.** A reminder arrives before every working shift the user has not
muted, including after a phone restart, without the app being opened.

**Does not.** Notify on days off. Ask for notification permission before the
user has a populated calendar — Android permits one ask, and it is spent only
once the app has demonstrably done something useful.

### 4.8 Home-screen widget

**Does.** Today's shift and what changes next, on the home screen, updated when
the rota changes and at the start of each day.

**Done means.** The widget agrees with the app at all times.

**Does not.** Allow editing.

### 4.9 Calendar export

**Does.** Produces a standard calendar file covering the next year and hands it
to whichever app the user picks — a partner's calendar, an e-mail to a manager.

**Done means.** The file imports cleanly into common calendar apps, and
re-exporting updates the existing events rather than duplicating them.

**Does not.** Include notes (§8). Upload anything. Sync — this is a one-way
snapshot, not a live connection.

### 4.10 Sharing a rota by code

**Does.** Turns the user's cycle into a short code that goes into any messaging
app, alongside plain-English instructions. Anyone else with Turnus can paste it
in and adopt the same rotation, aligned to their own answer about today.

**Done means.** A code pasted into a WhatsApp message survives the round trip
and produces the same cycle on the receiving phone; the receiver sees a preview
and what it will replace before anything changes.

**Does not.** Include a web link — a link needs a domain and a domain needs an
owner. Include the sender's identity, their changed days, or their notes.
Require either party to have an account.

### 4.11 Backup and restore

**Does.** Writes everything the user owns — rota, shifts, changed days, notes,
settings — to a file of their choosing, and restores from one. Before a restore
replaces anything, it shows what is *in* the chosen file. The rota being
replaced is kept so the restore can be undone.

**Done means.** A user can move to a new phone and get their rota back exactly,
notes included; and a restore from the wrong file is recoverable.

**Does not.** Upload anywhere. Merge two rotas — restore replaces (§7).

### 4.12 Delete everything

**Does.** Removes the rota, shifts, changed days, notes, settings and the
retained undo copy, returning the app to first run.

**Done means.** After it, nothing the user entered remains on the device
anywhere the app can reach.

**Does not.** Offer an undo. It is the one irreversible action in the app and
says so before it runs.

### 4.13 Advertising

**Does.** One banner on the month view and one native slot in the year view.
In regions that require it, a consent form is shown — after the calendar is
populated, never on first launch. Consent can be changed at any time from
settings. Each slot can be switched off remotely without shipping a release.

**Done means.** The app is fully usable with adverts refused, and no advert is
requested before consent has been resolved where consent is required.

**Does not.** Use interstitials or app-open adverts, ever. Lock any feature
behind removing adverts. Sell an ad-free tier (§7).

## 5. Not in v1

**Multiple rotas / a second job.** The most commonly expected missing feature,
and the one most likely to cost a review. It changes what a calendar day *is* —
a day can then hold two shifts, which affects the grid, the hours total and the
reminders together. Waiting because doing it badly is worse than not having it,
not because it is unimportant.

**Pay and earnings.** Excluded permanently, not deferred. The app cannot know
about overtime, an hour sent home early or a shift someone covered, so any
figure would be confidently wrong in the direction of a wage dispute — and it
contradicts the labelling in §4.6.

**Overtime calculation.** Thresholds, multipliers and averaging periods vary by
country, contract and union. A generic implementation is either wrong for most
people or a settings screen nobody finishes.

**Two-way calendar sync.** Needs accounts and a server, which ends the offline
guarantee. The one-way export covers the actual need: getting shifts in front
of someone else.

**Shift swapping between colleagues.** Needs a backend and a critical mass of
users in the same workplace.

**Rota scanning from a photo or PDF.** Serves people whose rota has no repeating
cycle — explicitly not our user (§2) — and needs a server and a per-scan cost.

**Annual leave and sickness as distinct categories.** Notes cover the common
case. Categorising invites the app to interpret health information.

**Cloud backup.** The file backup is better for this product: the user holds
it, and it is checkable, which is what makes the privacy claim credible.

## 6. Screens

**Setup wizard.** *Purpose:* from nothing to a populated calendar. *With no
data:* this is the no-data state — a welcome panel naming the app and what it
does, and one button. It is the only screen a user can reach with no rota.

**Month.** *Purpose:* the daily answer. *With no data:* unreachable — the app
shows setup until a rota exists. Between launch and the first read it is blank,
deliberately: a spinner that appears and vanishes reads worse than nothing.

**Day sheet.** *Purpose:* change one day. *With no data:* not applicable; it
always opens against a resolved day.

**Year.** *Purpose:* find the long breaks. *With no data:* unreachable.

**Settings.** *Purpose:* everything that is set once and forgotten — reminders,
shifts, the rota itself, backup, sharing, privacy, deletion. *With no data:*
unreachable. Sections whose preconditions are absent hide themselves rather
than showing disabled controls; the hours and consent sections are the two that
can be absent.

**Shift editor.** *Purpose:* correct the seeded guesses. *With no data:* never
empty — four shifts are seeded on first run.

**Rota editor.** *Purpose:* change the rotation after setup, keeping changed
days and notes. *With no data:* unreachable. It re-enters the setup wizard at
the pattern list rather than the welcome screen.

**Widget.** *Purpose:* the answer without opening anything. *With no data:* a
prompt to set up a rota.

**Notification.** *Purpose:* the reminder. *With no data:* none is posted.

## 7. The rules of the domain

The business logic that is not obvious. Most rework in this project has come
from breaking one of these.

### Time and dates

- **A rota date is a civil day count, not a timestamp.** Dates in rota
  arithmetic have no zone and no clock. Mixing them with wall-clock time is the
  single most likely source of a rota that is silently a day out.
- **Two different units of time exist and must never be confused:** rota days,
  and audit timestamps recording when a record changed.
- **Negative day differences are normal.** A user browsing before the date
  their cycle is anchored to produces a negative offset, and naive remainder
  arithmetic returns a negative index. Cycle position must always be a true
  modulo.
- **Hours are rostered, never elapsed.** Twice a year a night shift crossing a
  daylight-saving boundary really does last eleven or thirteen hours. The total
  counts the shift's defined length regardless, because elapsed time cannot be
  computed without a zone, rotas are rostered in hours, and a total that moved
  by an hour twice a year would read as a bug rather than as a fact about the
  clock.
- **A shift belongs to the day it starts.** A night shift running 19:00–07:00
  counts entirely on the first day, including in that month's hours.

### Generation and storage

- **A generated shift is never stored.** Only the cycle and its exceptions are
  kept; every calendar cell is recomputed on read. This is what makes "my rota
  is out by a day" a one-field correction instead of a data migration, and it
  is why changing a shift's times also changes the hours shown for months
  already worked.
- **A changed day and a note are separable.** Attaching a note must not pin
  that day's shift, or correcting a misaligned rota would strand every
  annotated day on its old shift.

### Limits and conflicts

- **Maximum cycle length: 40 days.**
- **Maximum distinct shifts in a shareable code: 36.** Beyond that a rota
  cannot be shared as a code, though it still works locally.
- **Maximum reminder lead time: 7 days.**
- **Reminders are scheduled 30 days ahead**, rebuilt on app open, on boot, and
  by a daily background job.
- **At most one rota is active at a time.** More than one is a corrupt state.
- **Two shifts may not share a letter**, compared case-insensitively.
- **A break must be shorter than its shift**, and a shift with no times cannot
  have one.
- **On a conflict between the cycle and a changed day, the changed day wins.**
  Always, with no prompt.
- **Restore replaces; it never merges.** Merging two rotas has no correct
  answer — whose alignment wins, what happens to a day changed in both — and a
  user restoring a backup is asking to return to a known state.

### Reversibility

- **Irreversible: "delete everything".** The only such action in the app. It
  says so before it runs, and points at the backup file as the way back.
- **Reversible with an explicit undo:** changing a day, restoring a backup,
  adopting a shared code.
- **Reversible by repeating the opposite action:** moving the whole rota a day
  forward or back.

### Visibility

- **Nobody but the user can see anything.** There are no accounts, no other
  users, and no server holding rota data. Everything leaves the device only
  when the user hands a file or a code to another app they chose.
- **A note never leaves the device except in a backup file.** Not in the
  calendar export, not in a share code. Users record sickness, hospital
  appointments and bereavements in notes, and the calendar export exists to be
  handed to employers.

### Must never happen twice

- **The notification permission is asked for once.** Android allows one prompt;
  spending it before the user has a populated calendar wastes it permanently.
- **Re-exporting the calendar must update events, not duplicate them.**
- **Seeding default shifts must be safe to attempt more than once** — startup
  can reach it from more than one direction.
- **Adopting the same shared code twice must not create a second rota.**

### Failure behaviour

- **The remote advert switch fails open.** If it cannot be fetched, adverts
  stay on. A hosting failure must not silently cost the app its only revenue.
- **A rota with no times still works.** Reminders degrade to an evening-before
  announcement rather than disappearing.
- **Missing exact-alarm permission degrades, never disables.** Reminders become
  approximate, and the app says so.

## 8. Compliance requirements, as product requirements

- **The app must be usable in full with advertising consent refused.** No
  feature may be withheld on that basis.
- **Consent must be requestable again at any time** from within settings, not
  only at first launch.
- **The consent request must not be the first thing a new user sees.** It comes
  after the calendar exists.
- **The privacy claim shown in the app must be narrower than "everything stays
  on this phone".** The rota stays on the phone; the advertising SDK sends
  device information off it. The wording users see, the privacy policy, and the
  store's data-safety declaration must all agree.
- **The app must state that approximate location is collected**, because it is
  derived from IP address by the advertising SDK, even though the app requests
  no location permission.
- **A user must be able to delete everything from inside the app**, without
  going into system settings.
- **A user must be able to export their own data in a readable form**, and the
  backup file must be human-readable so the privacy claim can be checked.
- **The app declares an adult audience.** It is not directed at children, and
  no age gate is used (see §10).
- **The app contains advertising and must be declared as such.**
- **Debug builds must never use live advertising units.**

## 9. Vocabulary

Pick one word and never drift. Inconsistency here reads as carelessness to a
user and causes rework in copy.

**Use:**

| Word | Meaning |
|---|---|
| **rota** | the user's whole repeating rotation |
| **cycle** | the repeating unit, e.g. "16-day cycle" |
| **shift** | one working period, e.g. Night |
| **day off** / **off** | a non-working day |
| **changed day** | a day the user overrode |
| **note** | free text on a day |
| **rostered** | what the pattern says, as opposed to worked |
| **reminder** | the notification before a shift |
| **backup** | the file that can restore everything |
| **export** | the calendar file for other apps |
| **code** | the shareable rota token |

**Never use:**

- **schedule**, **roster** — say *rota*
- **override** — internal only; users see *changed day*
- **alarm** — say *reminder*
- **pattern** — internal only; users see *rota* or *cycle*
- **link**, **URL** — sharing produces a *code*
- **timesheet**, **hours worked**, **clock in**, **attendance** — the app is
  none of these things and must never imply it is
- **employee**, **staff**, **manager**, **employer** — the user is a person
  with a rota, not a resource in someone's system
- **premium**, **pro**, **upgrade**, **unlock** — nothing is locked

## 10. Open questions

Genuinely undecided. Do not resolve these by guessing.

1. **Where does the second job go?** Multiple rotas is the top gap, but its
   shape is unsettled: does the calendar *switch* between rotas, or *overlay*
   them? Switching is straightforward and answers less than half the need —
   someone with two jobs is asking "am I free on Saturday", which needs both at
   once. Overlaying changes what a day is.
2. **Does a vertical version make sense?** The generic term is the most
   contested phrase in the category, and the one large exit in this space was a
   nurse-specific product. Unresolved whether that means a separate app,
   differently-worded store listings, or nothing.
3. **What makes year three worth it?** With nothing ever locked, advertising is
   the entire business. The visible failure mode in this category is not
   companies failing but solo developers quietly stopping. No answer yet, and
   the answer must not be "paywall the reminders".
4. **Should the widget offer more than one size or layout?** No evidence either
   way yet.
5. **Is the 40-day cycle limit right?** It is a guess. No known rota exceeds
   it, but no research has been done.
6. **Public holidays.** Many industrial rotas treat them differently. Entirely
   unmodelled, and it is not known how much that matters in practice.
