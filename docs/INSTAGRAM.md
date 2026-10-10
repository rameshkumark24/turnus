# Instagram — can memes bring in the right people?

Whether a meme-led Instagram account can become an acquisition channel for
Turnus, and how to run it if it can. The answer is §1. Everything after it is
the working: who the memes are for, what they say, what they are made from, and
a twelve-week experiment with the numbers that decide whether it continues.

---

## 0. What this is built on — and two files that do not exist

| Asked for | Found |
|---|---|
| `docs/PRD.md` | At the repository root: `PRD.md`. Read in full. |
| The USP in `docs/COMPETITORS.md` | **No such file** — not on `main`, not on this branch, not on GitHub. The USP is written down in `CLAUDE.md` rule 6 and is the spine of `docs/STORE-LISTING.md`: **nothing is ever locked.** Everything is free, paid for by advertising, while competitors paywall reminders and sell ad removal. |
| "Where the audience already is, and the five numbers" in `docs/GROWTH.md` | **No such file.** Where the audience is comes from `PRD.md` §2: rotas arrive on a noticeboard, as a PDF, as an e-mailed spreadsheet, or as a photo of a wall planner dropped into a WhatsApp group. **The five numbers could not be read.** §10.5 proposes five for this channel; when `GROWTH.md` exists, reconcile them with it rather than treating these as those. |

Outside research is from web searches run on 27 September 2026. Direct page
fetches were blocked from the environment this was written in, so figures come
from search-result summaries: follower counts and trend descriptions are as
reported, not checked by hand. Check any account before acting on it. Sources
are at the end.

---

## 1. Verdict

**A meme-led Instagram account is not a scalable acquisition channel for
Turnus. It is worth one bounded experiment — twelve weeks, three hours a week,
after the app is on Play — in one specific form: memes about the *rota* rather
than the *job*, made to be sent to a crew.** If it clears the bar in §10.6 it
becomes a small standing channel. It never becomes the main one.

### Why it fits better than most products

- **The pain is already a genre.** Every moment of need in `PRD.md` §2 is a joke
  shift workers already tell each other — counting on fingers to see whether
  you are in tomorrow, the photo of the rota lost in a camera roll, a mum asking
  about Christmas. Pages with six-figure followings exist on exactly this
  humour (§5.4). Nothing has to be manufactured to make it relatable.
- **The meme's sharing unit is the product's adoption unit.** Instagram's
  strongest signal for reaching non-followers is sends per reach — people
  DMing a post to someone. For shift workers, that someone is usually a person on
  the same shift, and that is exactly who a share code goes to (`PRD.md` §4.10):
  everyone on a crew works the same rotation. A meme sent into a crew chat is
  one step from a code sent into the same chat.
- **The USP works as a joke.** "Nothing is locked" lands because this audience
  is used to the opposite: paying monthly for a reminder, or to make an advert go
  away.

### Why it cannot carry acquisition

1. **Most of the reach lands on the wrong people.** The largest shift-work meme
   audiences are nurses, retail and hospitality. Retail and hospitality are
   excluded by name in `PRD.md` §2 — their rota has no repeating shape. Many
   nurses are rostered period by period rather than on a fixed cycle. The
   best-fit users — plant, refinery and factory operators on DuPont, Pitman and
   4-on-4-off — have the thinnest Instagram meme scene of any segment. The one
   refinery humour page found has about 4,000 followers, because that community
   lives on TikTok and Facebook.
2. **About half of those reached cannot install.** Turnus is Android-only. iOS
   holds about 54% of UK mobile and about 60% of US mobile (StatCounter, early
   2026, via search). Every iPhone owner who laughs, taps through and finds
   nothing is reach that cannot convert.
3. **A meme meets people when they are not looking for anything.** Installing
   happens at a moment of need: a new job, a new crew, a rota change, booking a
   holiday. An unverified account cannot put a clickable link on a post, so the
   path is post → profile → bio link → Play, and every step loses people.
4. **A user is worth too little to spend money on.** The app has two small
   advert slots, sessions last under ten seconds (`PRD.md` §3), and the most-used
   surface — the widget — carries no advert. Suppose a retained UK or US user
   opens the month view once a day and a banner earns about $1 per thousand
   impressions. That user is worth roughly **$0.35 a year**, and much less in
   lower-paying regions. Both inputs are assumptions; the conclusion survives
   any sensible correction to either. Paid shoutouts, boosted posts and creator
   fees cannot pay back. The only currency this channel can be paid in is the
   maintainer's time.
5. **Time is the one resource the product cannot spare.** `PRD.md` §10 Q3: the
   visible failure mode in this category is solo developers quietly stopping.
   Meme accounts reward volume. A channel that wants five posts a week competes
   with the product for the same person, so it gets a hard budget (§10.1).
6. **Beyond the store it cannot be measured — deliberately.** `CLAUDE.md` rule
   9 rules out an analytics SDK. Play Console can attribute store visits and
   installs to a tagged link. It cannot say which of those installs set up a
   rota or kept the app, and that gap is not to be closed by adding an SDK. The
   experiment is judged on installs and on installs per hour; activation is read
   only in aggregate.

### What should carry acquisition instead

In order of expected return per hour (reasoning in §9):

1. **Play search.** The listing is built. Someone typing "4 on 4 off calendar"
   has the need right now.
2. **The share code.** One activated user can bring a whole crew. It is already
   built, and the real job of every other channel is to reach the *first* person
   on each crew.
3. **Answering where rotas are already discussed** — subreddits and Facebook
   pages for police, fire, ambulance, offshore and plant workers. UK Ambulance
   Humour has about 227,000 likes on Facebook; the UK ambulance meme pages on
   Instagram have a few thousand followers.
4. **The noticeboard.** A QR card pinned beside the paper rota, which is exactly
   where `PRD.md` §2 says the audience already looks.

Instagram comes fifth. It supplies reach and recognition that make the other
four convert better, and the same files are cross-posted to TikTok and Facebook
Reels at no extra production cost.

---

## 2. Who the memes are for

| Segment | Rota fit | Instagram meme scene | Role |
|---|---|---|---|
| **UK response police** | 4-on-4-off, 12-hour shifts is the dominant response pattern — Turnus's first preset | Strong: @ukcophumour (~216K), @bluelight_lifestyle (~101K) | **Primary** |
| **Firefighters** | Always a repeating cycle: US 24/48, 48/96, Kelly; UK 2-2-4. Built with the cycle builder rather than a named preset | Strong in the US: @firefighterfenton (~286K), @unfuckedfire (~41K), @frequentflyersanddumpsterfires (~38K). Small in the UK: @ukfirefighterhumour (~1.6K) | **Primary** in the UK; secondary in the US, where iOS is ~60% |
| **Control rooms and dispatch** | DuPont and Pitman are common | @dispatcher_shenanigans (~38K) | Secondary |
| **Ambulance and EMS** | Mixed; many rotate | Small on Instagram: @1st.responder.diaries (~15K), @ukparamedichumour (~6.9K). Large on Facebook: UK Ambulance Humour (~227K likes) | Secondary — **Facebook is the better venue** |
| **Plant, refinery, factory operators** | **The best fit in the product** — DuPont, Pitman, Continental, 4-on-4-off | Thin: @processmyoperator (~4K). The scene lives on TikTok | Core user, **wrong platform** |
| **Offshore and FIFO** | 2/2 (28 days) and 2/3 (35 days) fit. **3/3 (42 days) and 4/4 (56 days) exceed the 40-day cycle limit** (`Pattern.MAX_CYCLE_DAYS`) | Mostly TikTok | Only rotations of 40 days or fewer. **Make no 3/3 memes** — the product cannot hold that rota |
| **Nurses and care staff** | Partial — many are rostered period by period rather than on a fixed cycle | Very large: @nurse.blake, @codebluememes, @nurselifern, @snarkynurses | **Reach, don't target** |
| **Retail, hospitality, agency** | Excluded (`PRD.md` §2) | Very large | **Avoid.** Every install from here is a one-star review waiting to happen |
| **Partners and families** | Not users; the calendar export exists for them | Relationship memes are a huge genre | **Amplifier** — they send the meme to the shift worker |

**UK first.** *Rota* is both the app's word and the UK's word, 4-on-4-off is the
UK response-policing pattern, and the listing is written in British English —
it talks about pennies. The US comes second. There, captions say "schedule",
because that is the word people search for: the vocabulary rules in `PRD.md` §9
govern the app's own copy, not what the audience types into a search box.

**Worth feeding back into the product:** `PRD.md` §10 Q5 records the 40-day
limit as a guess because "no known rota exceeds it". Three-weeks-on,
three-weeks-off FIFO and offshore rotations do exceed it. That is evidence for
the open question; this document does not change the limit.

---

## 3. The fit

### 3.1 The rule that decides what is ours

> **If the punchline is about the rota, it is ours. If it is about the work, it
> is not.**

Patients, calls, arrests, fires, coffee, tiredness, management — the app does
nothing about any of these. A meme about them reaches people and gives them no
reason to find the app. Counting, being asked, planning ahead, the photo, the
crew, the long break: these are the rota, and the app answers every one of them.

### 3.2 Problems that become memes

| Moment (`PRD.md` §2) | The meme | Emotion | What answers it in the app |
|---|---|---|---|
| "Am I in tomorrow?" | Counting on fingers from a blurry photo at 21:40 | This is literally me | *"Today — Night"*, the widget, the reminder |
| The rota lives in a WhatsApp photo | Scrolling past 2,000 photos to find it — and it's upside down | Frustration, played for laughs | The month grid replaces the photo |
| "Are you free on the 14th?" | The four-minute pause before "think so?" | Relatability | Swipe to the month |
| Christmas, weddings, next year | "Which Christmas?" | Surprise | Any day in any year |
| "When am I next off, and for how long?" | Worshipping the long break — DuPont's seven off | Desire | *"Off from tomorrow"* · *"4 days off"*; the longest break in the year view |
| The rota is a day out | An empty car park at 06:45 on your day off | Horror, then laughter | *A day back* / *A day on* — every changed day stays put |
| A new starter asks for the rota | Four people, four explanations | Recognition | The share code |
| The partner never knows | "Are you on nights next week?" for the 400th time | Relationship humour | Export to their calendar |
| Apps charge for reminders | "That'll be £3.99 a month" | Frustration → satisfaction | Nothing is locked |
| The clocks change on a night shift | Everyone else gets an extra hour in bed | Topical surprise | Hours are rostered, and the app says so |

### 3.3 Emotions, ranked for this product

1. **"This is literally me — or my crew."** Recognition is what gets a post
   *sent*. Sends are the reach signal that matters, so this is the default
   register.
2. **Humour.** The vehicle, never the whole point.
3. **Frustration — with the old way, never with the job.** The paper rota and
   the blurry photo are fair game. Colleagues, patients and employers are not.
4. **Satisfaction.** The grid filling in; the one-tap fix. It works in screen
   recordings and carries the product-integrated posts.
5. **Surprise.** "Christmas 2031? Nights." Good for saves and profile visits.
6. **FOMO — sparingly.** Missing birthdays and Christmases is the real cost of
   shift work. A light touch is relatable. Using it to sell is exploitation, and
   this audience will see it as that.

### 3.4 How the product gets in without the post feeling like an ad

Mostly, it doesn't. The account is a **rota account that happens to make an
app**, not an app account that makes memes:

- **Product-free — about 60%.** Pure rota relatability. The profile does the
  selling.
- **Product-light — about 30%.** The app is the "after" panel, the last frame,
  the widget on a home screen in the background.
- **Product-forward — about 10%.** A real screen recording or demo, openly what
  it is.

The ratio is a starting point and one of the experiment's variables (§10.3).
These rules are fixed:

- **Real UI only.** Screens come from `docs/screenshots/` or an emulator, for
  the same reasons as the store screenshots (`docs/STORE-LISTING.md` §5): no
  private notifications, no real employer names, and no note that reads like a
  medical appointment.
- **On product-light posts, the product line goes in the pinned comment**, not
  the caption. The meme stays clean enough to send, and the curious find the line.
- **No "download now"**, anywhere. The profile is the landing page (§7).

---

## 4. The meme content framework

| Type | What it means for Turnus | Examples | Never |
|---|---|---|---|
| **Problem** | The pain before the product, with no product in it | The finger count; the four-minute "think so?" | A problem the app does not solve — tiredness, short staffing |
| **Before vs after** | The manual way, then the same moment with the app | Noticeboard photo → the widget; four explanations → one pasted code | An "after" the app cannot actually produce |
| **POV** | "POV: you…" from inside the moment of need | "POV: 21:40, in bed, not sure if you're in tomorrow" | POVs about the job itself |
| **Relatable situations** | The recurring moments shift workers share | "See you Monday" meaning nothing; the long break; Christmas | Sleep or health advice |
| **Reaction** | Recognisable reactions, made with **house characters** (§5.3) — never film or TV stills | The Crew staring at a new rota | Copyrighted reaction images (§5.3) |
| **Alternative** | The old workflow against Turnus: paper, photo, spreadsheet, fridge, fingers — and the category's habit of charging for reminders | The WhatsApp photo chat; "that'll be £3.99 a month" | **Naming, showing or implying any identifiable competitor.** Category habits only, and only while they are true (`docs/STORE-LISTING.md`) |
| **Product-integrated** | Real screens inside the meme | The year view as a "name this rota" puzzle; the "Out by a day?" buttons as the punchline | Features that do not exist; claims the listing refuses to make (§11) |
| **User-generated** | Formats users can recreate without exposing anything | "Your rota in emoji — no dates"; rota tier lists; "what does your crew call the long break?" | Asking for month screenshots or share codes in public (§4.1) |

### 4.1 Why user-generated posts use emoji and not screenshots

A **cycle without its start day is harmless**:
🟨🟨🟨🟨⬛⬛⬛⬛🟦🟦🟦🟦⬛⬛⬛⬛ says what the rota *is*, not which nights its owner
is out of the house. A month screenshot or a **share code says both**. The code
carries the anchor day (`ShareLink` wire format:
`anchorDay;codes;slots;name`), so a code posted in public comments tells anyone
exactly when someone is away. For police and prison officers that is a real
safety problem. So user-generated formats ask for the shape, never the dates,
and the account never asks for a code in public. Codes go to the crew, in
private, which is where they are meant to go.

It is also a filter. Only people on a repeating rota can answer "your rota in
emoji", so the comments show who the account is actually reaching.

---

## 5. Trend research — September 2026

### 5.1 How Instagram decides who sees a post now

Each fact below changes something in this plan:

| Fact | What it changes |
|---|---|
| Watch time, **sends per reach** and likes per reach are the signals Adam Mosseri has confirmed; sends reportedly outweigh likes several times over for reaching non-followers | Every post is written for a recipient: *who would you send this to?* |
| Since **30 April 2026** the unoriginal-content penalty covers photos and carousels as well as Reels. Accounts that mostly repost are dropped from recommendations | Every meme is made from scratch. User posts are reshared only with permission and real transformation |
| **Hashtags are capped at five** per post (since December 2025). They help search, not reach | §5.5 |
| Public posts from professional accounts are **indexed by Google** (since July 2025) | Captions and alt text are search copy: "4 on 4 off calendar", "DuPont schedule" |
| **Trial Reels** show a Reel to non-followers first — reported open to professional accounts with at least 1,000 followers in 2026 | The A/B tool for §10 once the threshold is met. Check in the app |
| **No clickable links on posts or Reels** without Meta Verified. The bio takes up to five links; stories take link stickers | §7, §10.2 |
| **Business accounts get the Meta Sound Collection only** — about 14,000 tracks cleared for commercial use, not the trending chart | §5.3 |
| Two-way conversation became a ranking signal in March 2026 | Reply to comments. It is also the cheapest research available |

### 5.2 Current trends, scored

Each trend is scored 1–3 on **T**rend relevance (live and rising?), **A**udience
relevance (does it fit how shift workers joke?) and **P**roduct relevance (can it
land on a rota moment without forcing?). The three are multiplied. **Use a trend
only at 12 or above, and only if P is at least 2.** Trends stay a minority of
output — at least 80% of the plan is evergreen — because a trend lasts two or
three weeks and the account must not depend on catching them.

| Trend (reported September 2026) | T | A | P | Score | Use? | Why it fits, or doesn't |
|---|---|---|---|---|---|---|
| **"Kinda chic to…"** — a caption list of quiet wins that have nothing to do with money | 3 | 2 | 3 | **18** | **Yes** | "Nothing to do with money" *is* the product's position. Text only, so no rights issue. → Concept 19 |
| **"Bad Dream"** — a product has a dramatic nightmare, then wakes up safe in bed | 3 | 2 | 3 | **18** | **Yes** | The paper rota's nightmare — coffee, a lost pin, the camera roll — and it wakes up as the widget. Made for brands to recreate. Use original or Sound Collection audio. → Concept 6 |
| **Flop-core** — post the failures, not the highlights | 3 | 3 | 2 | **18** | **Yes** | A shift worker's flop is a rota flop: turning up on a rest day. The maintainer's own flops — the build that landed a day out — are founder honesty. → Concept 17 |
| **"The saxophones are getting louder"** — escalating dread over something mundane | 3 | 2 | 2 | **12** | **Text version only** | Escalation suits night four, or a mum's third question about Christmas. ⚠ **The audio is from *Boyz n the Hood*** and the joke references a shooting: copyrighted, and the wrong tone for police and paramedics. **Original alternative:** the same escalation with an in-house alarm tone that gets louder |
| **"What did my husband pray for?"** — the bait-and-switch between what someone wanted and what they got | 2 | 2 | 2 | 8 | **Text only, tentatively** | Reaches partners: "Prayed for someone home every weekend. Got 4 on 4 off." ⚠ Check whether the version going round depends on a commercial sound. → Concept 16 |
| "Potential-maxxing" — proving progress with hard numbers | 2 | 1 | 2 | 4 | No | A "rota-maxxing" version with the year card ("183 working days, 2,196 hours rostered") is possible, but bragging about hours sits badly next to rule 8 |
| "Some people call me…" | 2 | 2 | 1 | 4 | No | ⚠ Runs on a Justin Timberlake track a business account cannot use, and has no rota angle |
| "Right Round hangover" | 2 | 1 | 1 | 2 | No | ⚠ Flo Rida audio, and a prank on someone asleep. A day-sleeper after nights is not a joke to this audience |
| Half-face parent comparison | 3 | 1 | 1 | 3 | No | No rota angle |

### 5.3 Evergreen formats — and the ones that belong to someone else

**Safe, and the backbone of the account:**

- Text-on-screen Reels over **original footage** — hands, a phone, a
  noticeboard, an empty car park at 06:45
- "Nobody: / Shift workers:" and "Tell me you work shifts without telling me",
  as text
- Tier lists, "name this rota" puzzles and carousel explainers, all made
  in-house
- Chat memes in a **generic, drawn** chat UI — not a copy of WhatsApp's
  interface or branding
- Starter packs and expectation-vs-reality, using **only photos or drawings made
  in-house**, never images lifted from search

**⚠ Flagged — templates built on someone else's rights.** An individual posting
these is usually tolerated. A brand posting them to promote an app is commercial
use, and meme owners have sued brands before: Grumpy Cat won $710,000 against a
beverage company, and the creators of Nyan Cat and Keyboard Cat sued Warner Bros.

| Template | Whose it is | Original alternative |
|---|---|---|
| Drake approve / disapprove | Stills from a music video | **The Two Hands** — a drawn hand pushing away the paper rota, the other pointing at the widget |
| Distracted Boyfriend | A commercially licensed stock photograph | **The Look Back** — three drawn Crew characters |
| "This is fine" | KC Green's webcomic | **The Noticeboard Is Fine** — a drawn noticeboard, a curling rota, a calm figure |
| SpongeBob, *The Office*, *The Simpsons*, any film or TV still | Studios | House characters |
| Gru's plan, Two Buttons | A film studio; a webcomic artist | **The Four-Panel Plan**, drawn |
| Trending commercial songs | Labels — and Instagram's licence excludes business use | The Meta Sound Collection, an original sound, a voiceover, or silence with captions |

**One investment worth making before the first post: six house templates**,
drawn once in the app's own palette. Use `COLOR_DAY` `#E0A33C`, the night blue,
and the dark ground and surfaces from `Theme.kt` — the same sources as the
feature graphic.

1. **The Crew** — four figures, A to D shift, one per shift colour. The recurring
   cast.
2. **The Counting Hand** — fingers mid-count.
3. **The Noticeboard** — cork, a drawing pin, a curling A4 rota.
4. **The Two Hands** — no / yes.
5. **The Phone Call** — a two-panel chat with a generic parent.
6. **The Long Break** — a month of squares with one run of empty days glowing.

The templates solve the rights problem, make a post recognisably Turnus without
a logo, and cut every later post to about twenty minutes.

### 5.4 Where the audience already is on Instagram

Follower counts are as reported in search results in September 2026. Check
before acting.

| Account | Audience | Size | Use |
|---|---|---|---|
| @ukcophumour | UK police | ~216K | Study the voice — the primary segment |
| @bluelight_lifestyle | UK emergency services; posts about cancelled rest days | ~101K | Study; a possible collaboration later |
| @firefighterfenton | US fire | ~286K | Study |
| @unfuckedfire | Fire | ~41K | Study only — the name rules out association |
| @frequentflyersanddumpsterfires | Fire and EMS | ~38K | Study |
| @dispatcher_shenanigans | 911 dispatch | ~38K | Study |
| @1st.responder.diaries | EMS | ~15K | Study |
| @ukparamedichumour · @ambulancehumour · @uk_paramedic_memes | UK ambulance | Small | Study; the segment is bigger on Facebook |
| @ukfirefighterhumour | UK fire | ~1.6K | Small enough to reply to as a peer |
| @processmyoperator | Refinery operators | ~4K | The core user — tiny here |
| @nurse.blake · @codebluememes · @nurselifern · @snarkynurses | Nurses | Large | Reach only; partial fit |
| *UK Ambulance Humour* (Facebook) | UK ambulance | ~227K likes | Evidence that Facebook is the venue for some segments |

**How to use them:**

- **Audit first, post second.** In week 0, read the last 30 posts of five of these
  pages and log the format, the topic, and how many comments are tags. That
  replaces guessing which formats this niche rewards.
- **Never plug the app in their comments.** A brand dropping links under someone
  else's meme is spam, and in communities this small it gets remembered.
- **No paid shoutouts.** At about $0.35 per user per year (§1), none can pay back.
- **Collaboration posts** — one post, both accounts' reach — are worth proposing
  only once the account has 30 or more original posts to show, and only with a
  concept the other page would want anyway.

### 5.5 Hashtags and search

Five at most, and they help search, not reach. The formula:

- **One pattern tag.** Small but self-selecting, which is the point:
  `#4on4off` `#dupont` `#pitmanschedule` `#continentalshift` `#2on2off`
  `#24on48off`
- **One occupation tag:** `#policeuk` `#firefighter` `#999family`
  `#operatorlife` `#offshorelife`
- **One or two community tags:** `#shiftwork` `#nightshift` `#shiftlife`
- **Optionally, one series tag** for the user-generated format, after checking it
  is unused

These tags' volumes have not been measured; the experiment will measure them.
**Captions and alt text carry the search terms**, because Google indexes them:
*4 on 4 off calendar, shift rota app, DuPont schedule, when am I next off.*

### 5.6 The calendar to hang content on

| Date | Moment | Fit |
|---|---|---|
| **Sun 25 Oct 2026** | UK clocks go back — the night shift works 13 hours | **The best fit of the year.** The joke is true, and the app counts the rostered 12 and says why (`PRD.md` §7). → Concept 15 |
| Sat 31 Oct 2026 | Halloween falls on a Saturday night | Relatable, emergency services |
| Sun 1 Nov 2026 | US clocks go back | US version of Concept 15 |
| Thu 5 Nov 2026 | Bonfire Night — the busiest night for UK fire and police | Keep it about the rota, not the fires |
| December 2026 | "Who's working Christmas?" — Christmas Day is a Friday | → Concept 14 |
| Thu 31 Dec 2026 | New Year's Eve on nights | Relatable |
| January 2027 | Holiday booking opens: the longest break of 2027 | → Concept 13, year view |
| Sun 14 Mar / Sun 28 Mar 2027 | US / UK clocks go forward — an 11-hour night | Concept 15 again |
| 9 Sep 2027 | Emergency Services Day (UK) | Primary segment |

---

## 6. The content system

### 6.1 Five pillars

| # | Pillar | Purpose | Audience | Meme formats | Concepts | CTA strategy | Share |
|---|---|---|---|---|---|---|---|
| 1 | **"Am I in tomorrow?"** | Reach and sends — the most universal rota moment | Every repeating-cycle worker | Text-on-screen Reels, POV, house-character reactions | 1–4 | Mostly **soft** (send it to someone); **curiosity** when the answer is the widget | 25% |
| 2 | **The rota in the group chat** | Position against the real competitor: the photo, the paper, the spreadsheet (`PRD.md` §2) | Crews, and whoever posts the rota | Before/after, alternative memes, the Bad Dream trend | 5–8 | **Problem** ("next time someone asks in the group…"); **direct** on the share-code post | 20% |
| 3 | **Pattern people** | Comments and saves, and filtering the audience down to repeating cycles | People who know their rota by name | Tier lists, puzzles, emoji user-generated posts, Crew rivalry | 9–12 | **Soft** — the reply *is* the action; **curiosity** on the last slide ("every one of these is a preset") | 20% |
| 4 | **Life around the rota** | Reach partners and families, who send it on to the shift worker | Shift workers *and* the people who plan around them | Chat memes, POV, topical posts | 13–16 | **Problem** ("send this to whoever asks"); **curiosity** for the year view | 15% |
| 5 | **Nothing is locked** | Profile visits → installs: the product, shown honestly | Warm followers and profile visitors | Screen recordings, carousels, founder text, flop-core | 17–20 | **Curiosity** and **direct** | 20% |

### 6.2 Twenty concepts

Field key: **Problem** — the user's problem. **Hook** — the first second.
**Format** — the visual and meme format. **On-image** — the text on the image.
**Product** — how it connects to the product. **Objective** — what the post is
for.

---

**1 · Let me check** — *Pillar 1 · Problem*
- **Problem:** Asked whether you're free, you have to count it out from a photo.
- **Hook:** "Mum: are you free Sat 14th?"
- **Format:** 8-second text-on-screen Reel. Original footage: a thumb flicking a
  camera roll, pinching into a blurry photo of a paper rota, fingers counting.
  An in-house alarm tone that escalates.
- **On-image:** "Mum: are you free Sat 14th?" → "*opens 2,318 photos*" →
  "*finds the rota from March*" → "*counts on fingers*" → "…think so?"
- **Caption:** "Every. Single. Time. Who's the one in your house who has to ask?"
- **CTA:** Soft — "Send this to whoever asks you this every week."
- **Product:** None in the post; the profile does it.
- **Objective:** Reach, sends.

**2 · 04:00 rota maths** — *Pillar 1 · POV, product-integrated*
- **Problem:** Working out when you're next off, on night four, with no brain
  left.
- **Hook:** "POV: 04:00, night 4, working out when you're next off"
- **Format:** Reel. The Counting Hand loses count over three beats, then cuts to
  the real month view.
- **On-image:** "4 on… 4 off… does that include today… was last week the long
  one…" → *(app)* **"Off from tomorrow"** · **"4 days off"**
- **Caption:** "The app just says it. That's the whole trick."
- **CTA:** Curiosity. Pinned comment: "It's the line under the calendar. Free,
  nothing locked. Link in bio (Android)."
- **Product:** The "what changes next" line (`PRD.md` §3).
- **Objective:** Profile visits.

**3 · In bed, not sure** — *Pillar 1 · POV, product-light*
- **Problem:** The evening-before doubt, often just before sleeping ahead of
  nights (`PRD.md` §2, moment 1).
- **Hook:** "POV: 21:40, lights off, suddenly not sure if you're in tomorrow"
- **Format:** Reel. A dark room, a phone's glow, a thumb hovering over the camera
  roll. It swipes to the home screen instead, and the widget answers.
- **On-image:** "21:40. Lights off." → "…am I in tomorrow?" → *(widget)*
  **"Off today"** · **"Back in tomorrow · Night"**
- **Caption:** "The quickest way to find out is not opening anything."
- **CTA:** Curiosity — "The widget's in the last frame."
- **Product:** The widget, the most-used surface.
- **Objective:** Profile visits.

**4 · "See you Monday"** — *Pillar 1 · Reaction*
- **Problem:** The world runs on weekdays; the rota doesn't.
- **Hook:** "Office friend: see you Monday!"
- **Format:** Single image — the Crew, four blank stares.
- **On-image:** "Them: see you Monday! / Us: *what is a Monday*"
- **Caption:** "Days of the week are a rumour. Which day is your 'Friday' this
  week?"
- **CTA:** Soft — comment.
- **Product:** None.
- **Objective:** Reach, comments.

**5 · The photo** — *Pillar 2 · Alternative*
- **Problem:** The rota is a photo in the group chat.
- **Hook:** A chat called "C Shift 🔥"
- **Format:** Single image in a drawn, generic chat UI.
- **On-image:** "anyone got the new rota?" / *[blurry photo, sideways]* /
  "can't read it" / "zoom in" / "it's upside down" / "just ask Dave"
- **Caption:** "Every workplace's rota distribution system since about 2014."
- **CTA:** Problem — "Next time someone asks in the group, send them this."
  Pinned: "…or send them a code that puts the whole rota on their phone."
- **Product:** The share code, in the pinned comment only.
- **Objective:** Sends.

**6 · The rota's bad dream** — *Pillar 2 · Trend (Bad Dream), before/after*
- **Problem:** Paper rotas get soaked, lost, photographed crooked and buried.
- **Hook:** "The rota's worst nightmare"
- **Format:** Reel in the Bad Dream structure. An A4 rota suffers: coffee, a
  drawing pin falling out, a crooked photo, 600 memes on top of it in the camera
  roll. Then "…it was just a dream", and the widget on a home screen. Sound
  Collection or original audio.
- **On-image:** "the rota's worst nightmare" → "…it was just a dream"
- **Caption:** "Your rota deserves better than a drawing pin."
- **CTA:** Curiosity — "The widget's free. So is everything else in it."
- **Product:** The widget (`docs/screenshots/07-widget.png`).
- **Objective:** Reach, profile visits. **Time-limited** — only while the trend
  is live.

**7 · The new starter** — *Pillar 2 · Before/after*
- **Problem:** Everyone explains the rota to a new starter differently.
- **Hook:** "New starter: 'so what's the rota?'"
- **Format:** Reel with the Crew: three answers, each worse than the last. The
  fourth character just sends something. Cut to the real "Use this rota?"
  preview.
- **On-image:** "A: four on four off" / "B: well, days then nights" / "C:
  depends on Dave" / "D: *sends a code*" → **"Use this rota?"** · **"The next
  two weeks"**
- **Caption:** "One of you sets it up. Everyone else pastes the code. Your own
  shift times, changed days and notes stay as they are."
- **CTA:** Direct — "Set your rota once, send the code to your shift. Android,
  link in bio."
- **Product:** The share code (`docs/screenshots/03-sharecode.png`).
- **Objective:** Installs — and seeding the crew loop.

**8 · The spreadsheet** — *Pillar 2 · Alternative*
- **Problem:** The rota arrives as a spreadsheet you have to read on a phone.
- **Hook:** "The new rota has arrived. It's a spreadsheet."
- **Format:** Carousel. Slide 1: a drawn, lovingly awful spreadsheet — 14 tabs,
  frozen panes, your name on row 212. Slide 2: reading it on a phone. Slide 3:
  the same year in the real year view.
- **On-image:** "rota_FINAL_v7(2).xlsx" → "on a phone, at a bus stop" → "…or
  the whole year on one screen"
- **Caption:** "Whoever makes the spreadsheet is doing their best. It's just not
  a calendar."
- **CTA:** Problem — "Tag whoever sends yours."
- **Product:** The year view (`docs/screenshots/04-year.png`).
- **Objective:** Profile visits.

**9 · The rota tier list** — *Pillar 3 · User-generated, by comment*
- **Problem:** Everyone is sure their pattern is the best — or the worst.
- **Hook:** "Ranking shift patterns. Fight me."
- **Format:** Carousel tier list: 4-on-4-off, DuPont, Pitman, Continental,
  Panama, 5-on-2-off, 24/48, 2-2-4, placed S to D.
- **On-image:** The tiers, with one-word reasons ("S — the seven off").
- **Caption:** "DuPont in S for the seven off. Continental in C because nobody
  can explain it to their mum. Tell me I'm wrong."
- **CTA:** Soft — comment. Last slide, curiosity: "Six of these are presets in
  Turnus. The other two take a minute to build."
- **Product:** The presets and the cycle builder.
- **Objective:** Comments, saves, audience filtering.

**10 · Your rota in emoji** — *Pillar 3 · User-generated*
- **Problem:** Nobody outside the job understands your pattern.
- **Hook:** "Explain your rota. Emoji only."
- **Format:** Carousel — slide 1 the example, slide 2 the rule.
- **On-image:** Slide 1: 🟨🟨🟨🟨⬛⬛⬛⬛🟦🟦🟦🟦⬛⬛⬛⬛. Slide 2: "🟨 days · 🟦
  nights · ⬛ off. **No dates.**"
- **Caption:** "Mine's 4 days, 4 off, 4 nights, 4 off. Longest cycle in the
  comments wins respect and nothing else. Leave the dates out — a pattern is
  harmless, a pattern with a start day tells people when your house is empty."
- **CTA:** Soft/user-generated. A follow-up Reel builds the five most-posted
  patterns in the app, crediting commenters only with their permission.
- **Product:** Any repeating cycle up to 40 days.
- **Objective:** Comments, follows — and a direct reading of who the account
  reaches (§4.1).

**11 · Name this rota** — *Pillar 3 · Product-integrated puzzle*
- **Problem:** Shift workers know their rota by its shape.
- **Hook:** "Name this rota."
- **Format:** Single image. A real year view with the year and labels cropped
  away, leaving only the coloured blocks. The answer goes in the next day's
  story.
- **On-image:** "Name this rota. Three seconds."
- **Caption:** "If you got it instantly, you've worked it."
- **CTA:** Soft (comment); in the answer story, curiosity: "It's the year view.
  Every rota looks like this in it."
- **Product:** The year view — the image *is* the product.
- **Objective:** Comments, saves.

**12 · Every shift thinks it's the hard one** — *Pillar 3 · Relatable, the Crew*
- **Problem:** Four shifts on the same rota, days apart, each convinced its week
  is the worst.
- **Hook:** "A, B, C and D shift. Same rota. Four days apart."
- **Format:** Single image — the Crew, each figure saying the same line.
- **On-image:** Each figure: "we get the worst of it"
- **Caption:** "Same pattern, different start. Tag the other shift."
- **CTA:** Soft — tag.
- **Product:** Light. Pinned comment: a share code lines the same pattern up
  with each person's own "today".
- **Objective:** Sends *between* crews — the spread from one shift to the next.

**13 · The long break** — *Pillar 4 · Relatable, product-light*
- **Problem:** Finding the long run of days off to book a holiday around.
- **Hook:** "DuPont people when the seven off comes round:"
- **Format:** Single image on the Long Break template. In January, the real
  year card instead.
- **On-image:** Seven glowing squares — "the seven off". January version: the
  real **"Longest break: … from …"** line.
- **Caption:** "What does your crew call the long break? Wrong answers welcome."
- **CTA:** Soft (comment). January version, curiosity: "The year view finds your
  longest one."
- **Product:** The year view's longest break.
- **Objective:** Comments; the January version drives profile visits.

**14 · "Which Christmas?"** — *Pillar 4 · Product-integrated, surprise*
- **Problem:** Families plan Christmas years out.
- **Hook:** "Mum: are you working Christmas?"
- **Format:** Screen recording on an emulator, swiping the month view forward
  year after year and stopping on each December to show the 25th.
- **On-image:** "Mum: are you working Christmas?" / "Me: which year?"
- **Caption:** "Set your rota once and it knows every day after. Swaps and
  overtime you add yourself — it isn't psychic."
- **CTA:** Curiosity — "Link in bio. Nothing is locked."
- **Product:** Any day in any year; a changed day overrides the pattern.
- **Objective:** Profile visits, saves. **December.**

**15 · The clocks go back** — *Pillar 4 · Topical*
- **Problem:** When the clocks go back, a 12-hour night shift lasts 13.
- **Hook:** "Clocks go back tonight."
- **Format:** Two-panel single image.
- **On-image:** "Everyone: an extra hour in bed 🥰" / "Night shift: an extra
  hour at work"
- **Caption:** "Tonight's night shift is 13 hours long. For what it's worth,
  Turnus still counts the 12 you're rostered — and says so — because a total that
  jumps by an hour twice a year looks like a bug."
- **CTA:** Soft — "Send it to whoever's on tonight."
- **Product:** Hours are rostered, not elapsed, and labelled that way
  (`PRD.md` §4.6, §7).
- **Objective:** Sends. **UK: post Saturday 24 October. US: Saturday
  31 October.**

**16 · What I prayed for** — *Pillar 4 · Trend (text only), partners*
- **Problem:** Partners plan their lives around someone else's rota.
- **Hook:** "What did I pray for?"
- **Format:** Text-only version of the current trend.
- **On-image:** "What did I pray for? Someone home every weekend." / "What did
  I get? Someone home four days in eight. Never the same four."
- **Caption:** "Shift workers' partners, this one's yours. Turnus can send the
  next year of shifts to your calendar — one file, and sending it again updates
  it instead of doubling it. Their notes never go with it."
- **CTA:** Problem — "Send this to your shift worker."
- **Product:** The calendar export (`PRD.md` §4.9); notes stay on the phone
  (rule 7).
- **Objective:** Sends from partners to shift workers.

**17 · I turned up on my day off** — *Pillar 5 · Flop-core, product-integrated*
- **Problem:** The rota is a day out.
- **Hook:** "06:45. The car park's empty."
- **Format:** Reel. Original footage of an empty car park at dawn, cut to the
  real "Out by a day?" card.
- **On-image:** "06:45. The car park's empty." → "…it was my day off." →
  **"Out by a day?"** · **"‹ A day back"** · **"A day on ›"**
- **Caption:** "Everyone's done it once. A rota landing a day out is the
  commonest thing that goes wrong with rota apps, so Turnus fixes it with one
  button — and every day you've already changed stays put."
- **CTA:** Curiosity — "It's the last frame. Link in bio."
- **Product:** The nudge (`docs/screenshots/05-nudge.png`).
- **Objective:** Profile visits, installs.

**18 · The reminder paywall** — *Pillar 5 · Alternative, unnamed*
- **Problem:** The category charges for reminders and for removing its own
  adverts.
- **Hook:** "Me: I'd like a reminder before my shift."
- **Format:** Two-panel single image with the Two Hands. No app named, shown, or
  recognisable.
- **On-image:** "Me: I'd like a reminder before my shift" / "Rota apps: that'll
  be £3.99 a month" / "Turnus: …it's free. Everything is."
- **Caption:** "No Pro. No unlock. No subscription — not even one to remove the
  ads. A banner on the calendar pays for it."
- **CTA:** Direct — "Android. Link in bio."
- **Product:** Nothing is locked (rule 6).
- **Objective:** Installs.
- **Before posting:** the £3.99 is illustrative. Re-check that the category
  claim is still true.

**19 · Kinda chic to…** — *Pillar 5 · Trend (text only)*
- **Problem:** Small wins shift workers rarely get.
- **Hook:** "Kinda chic to…"
- **Format:** Caption-list meme, text on the app's palette.
- **On-image:** "Kinda chic to know if you're on Christmas 2029" / "Kinda chic
  to not count on your fingers at 4am" / "Kinda chic to get a reminder nobody
  charges you for" / "Kinda chic to keep your notes on your own phone"
- **Caption:** "Not one of these costs money. That's the point."
- **CTA:** Curiosity.
- **Product:** Every line is a real feature.
- **Objective:** Saves, profile visits. **Time-limited.**

**20 · Five things this app will never do** — *Pillar 5 · Product-forward,
founder voice*
- **Problem:** People distrust free apps.
- **Hook:** "Five things Turnus will never do."
- **Format:** Carousel, one refusal per slide, each with its reason.
- **On-image:** 1 "Charge you — for anything" / 2 "Work out your pay — it
  can't know your overtime, and a confident wrong number helps nobody" / 3
  "Scan your rota from a photo — that needs a server" / 4 "Send your notes
  anywhere — not in exports, not in codes" / 5 "Show you a pop-up ad — a banner,
  never a takeover"
- **Caption:** "Each of these was decided before the app was built, and each is
  the reason something else in it works the way it does."
- **CTA:** Direct — "Android, free, link in bio."
- **Product:** `CLAUDE.md` rules 6–10, said in public.
- **Objective:** Installs from warm visitors, and fewer *disappointed* installs —
  which means fewer one-star reviews. Pin it.

---

## 7. The funnel

| Stage | What should happen | What makes it happen | Read it in | Where it leaks | The fix |
|---|---|---|---|---|---|
| **Reach** | Non-followers on repeating rotas see a rota meme | Pattern words in the first frame; UK first; original content | Instagram Insights — reach, % non-followers | Nurses, retail, US iPhone owners | Pattern-named hooks; read the comments to see who is actually there |
| **Engagement** | It gets *sent* — to a crewmate or a partner | A clear recipient; a soft CTA | Sends, saves, comments | Likes without sends: entertained, not passed on | Rewrite for a recipient. Likes are never a decision input |
| **Profile visit** | A curious viewer taps through | Product-light posts, curiosity CTAs, pinned comments | Profile visits | The profile doesn't explain itself in three seconds | Treat the profile as the landing page (below) |
| **Bio / CTA** | They tap the link | A single Play link | External link taps | iPhone owners; link-in-bio pages | "Android" in the bio; link straight to Play |
| **Store listing** | The listing continues the post's promise | A **custom store listing** for Instagram traffic, with its own URL | Play Console: store listing visitors by UTM campaign and by listing | A listing whose tone breaks the meme's | Same claims; screenshots lead with "Every feature free" and the share code |
| **Install** | They install | The listing (`docs/STORE-LISTING.md`) | Play Console: acquisitions by UTM and by listing; conversion rate | Listing conversion | Test the screenshot order on the custom listing only |
| **Activation** | They reach a populated calendar | The one-minute setup that asks *"what are you on today?"* | **Not per channel** (rule 9). In aggregate: AdMob banner impressions — the month view is unreachable until a rota exists | Someone who doesn't know their pattern's name | A pinned 20-second screen recording of setup |
| **Retention** | The widget or a reminder answers them daily; they send the code to the crew | The widget, reminders, the share code | Play Console retained installers by channel, **if the console offers it for UTM traffic** — check; otherwise uninstalls in aggregate | OEM battery managers killing reminders (`EDGE-CASES.md` #2) | Occasional posts on what existing users miss: the year view, "Out by a day?", the widget, "Reminders not arriving?" |

**The profile, as a landing page:**

- **Name field** (searchable): `Turnus · shift rota calendar`
- **Bio:**
  > Your rota, offline. Reminders before every shift.
  > Nothing is ever locked — no Pro, no subscription.
  > Android · free ↓
- **Link:** straight to the Play listing, UTM-tagged (§10.2). Not a link-in-bio
  page — that is one more hop, and a third-party tracker on a product whose case
  is privacy.
- **Three pinned posts:** Concept 20 (what it will never do), a 20-second
  setup recording, and Concept 7 (send it to your crew).
- **Highlights:** *How it works* · *iPhone?* ("Android only for now — honest
  answer") · *Your data* (what stays on the phone, in the privacy policy's own
  words — §11).

---

## 8. CTA strategy

**The rule: the more shareable the post, the softer the CTA.** A hard sell on a
relatable post stops it being sent, and sending is the only reach mechanism.

| Type | When | Example lines | Starting share |
|---|---|---|---|
| **Soft** | Highly relatable, product-free posts — Pillars 1 and 3 | "Send this to whoever asks you this every week" · "Tag your shift" · "Your rota in emoji — no dates" · "Wrong answers only" | 40% |
| **Problem** | The meme names a pain the app answers | "Next time someone asks in the group, send them this" · "Tag whoever sends the spreadsheet" · "Send this to your shift worker" | 20% |
| **Curiosity** | The product is in frame but unexplained | "It's the last frame" · "The line under the calendar just says it" · "The widget's free. Everything is." | 25% |
| **Direct** | High-intent posts — demos, the share code, the refusals | "Android, free, nothing locked — link in bio" · "Set it up once, send the code to your shift" | 15% |

Rules:

- Never the same CTA type on two consecutive posts.
- On product-light posts the product line goes in the **pinned comment**, not
  the caption.
- **No comment-to-DM automation.** Those tools are a third party processing
  commenters' data, on a product whose case is that it collects nothing. And a
  DM-link funnel reads as exactly the marketing this account is trying not to be.
- Never "Download now". Never "never miss a shift again" (§11).

---

## 9. Better channels — and why they rank above Instagram

| # | Channel | Why it suits *this* product | Cost | Measurable? |
|---|---|---|---|---|
| 1 | **Play search** | Highest intent: someone typing "4 on 4 off calendar" has the problem now. The listing is built, and its long description already names the professions people search for | Occasional listing edits | Yes — Play Console breaks acquisitions down by search term |
| 2 | **The share code** | The crew is the adoption unit: one activated user on a four-crew site can bring the rest of their shift. Already built and hardened (`EDGE-CASES.md` #5) | Nothing — it's in the product | No, by design. Ask in comments: "did your shift switch over?" |
| 3 | **Communities where rotas are already asked about** — subreddits such as r/policeuk, r/Firefighting, r/ems, r/nightshift and r/oilandgasworkers, and occupational Facebook pages and groups | People there ask which app to use for their rota. An honest founder post — why nothing is locked, why there is no pay figure — is the kind these communities accept. **Read each community's self-promotion rules first** | About an hour per good post | Yes — one UTM campaign per community |
| 4 | **The noticeboard** | `PRD.md` §2: the rota is on a noticeboard. A card beside it with a QR code reaches people at the exact moment they are reading it | Printing, and one person per site | Yes — one UTM campaign per card |
| 5 | **TikTok and Facebook Reels** | Where plant, refinery and offshore humour actually lives. The same files, exported without watermarks | Minutes per post | Yes — separate UTM sources |
| 6 | **Instagram** | Reach and recognition; a feeder for 2–5 | 3 hours a week | Up to the install (§10) |

---

## 10. The posting experiment

### 10.1 Before it starts

- **The app must be on Play.** It isn't yet: `BUILD-PLAN.md` Phase 16 is blocked
  on the upload. If the Play developer account is a **personal account created
  after 13 November 2023**, production access also needs a **closed test with at
  least 12 testers opted in for 14 continuous days**. Instagram can help with
  that. During pre-launch, post twice a week to find the voice and bank
  templates, and recruit closed testers who actually work repeating rotas.
  They make better testers than a tester-exchange service, and their feedback is
  real.
- **Week 0 — one session:** audit five pages (§5.4), draw the six house
  templates (§5.3), set up the profile (§7) and the tracking (§10.2).
- **Budget: 3 hours a week, logged** — two hours batch-producing four posts from
  the templates, one hour replying to comments. If the channel needs more than
  that to work, that is a result, not a reason to spend more.
- **Account:** an Instagram *business* account. The music licence is clear, it
  gets Insights, and it qualifies for Trial Reels at 1,000 followers. Sound
  Collection or original audio only.

### 10.2 Measurement plumbing

Instagram cannot attribute an install to a post, and the app must not try
(rule 9). All attribution happens at the store:

- **UTM-tagged Play links**, built with Google's *Play* campaign URL builder,
  which produces the `referrer` form Play expects. The shape:
  `https://play.google.com/store/apps/details?id=com.turnus.rota&referrer=utm_source%3Dinstagram%26utm_medium%3Dbio%26utm_campaign%3Dig-bio-w01`
  Click-test each new link once and confirm it appears in Play Console before
  relying on it. The data lags by a day or two.
- **The bio link can't be per post, so it rotates weekly:** `ig-bio-w01`,
  `ig-bio-w02`, … Each week is one test arm.
- **Per-post attribution comes from stories.** Every feed post gets a companion
  story with a link sticker tagged `ig-st-c07` (concept 7), so a post's own
  traffic is visible.
- **A custom store listing for Instagram traffic**, with its unique URL, so Play
  Console reports its visitors and conversion apart from organic search.
- **One spreadsheet row per post:** date and time · pillar · concept · type ·
  format · hook variant · CTA type · product weight (0 free / 1 light / 2
  forward) · trend or original · reach · % non-followers · sends · saves ·
  comments · **relevant comments** (someone on a repeating rota) ·
  **wrong-audience comments** ("iPhone?", "my rota changes every week") ·
  profile visits · link taps · UTM campaign · store visitors · acquisitions ·
  minutes spent.

### 10.3 Design — twelve weeks

**Weeks 1–4 · Explore.** Four posts a week — two Reels, one carousel, one single
image — each with a story. Every pillar appears at least three times. This builds
a baseline for every metric; nothing is varied on purpose.

**Weeks 5–8 · Test, one variable at a time.** Use Trial Reels once past 1,000
followers; before that, alternate weeks. Keep pairs matched — same pillar, same
week, one difference.

**Weeks 9–12 · Exploit and decide.** Double down on the two best
pillar-and-format pairs, test frequency, then apply §10.6.

| Variable | Arms | Primary metric | What it answers |
|---|---|---|---|
| **Posting frequency** | 3 vs 5 a week, alternate weeks 9–12 | Installs per hour · sends per post | Does volume pay for itself, or just cost time? |
| **Meme format** | Single image vs Reel vs carousel | Sends per 1K reach (image, Reel) · saves per 1K (carousel) | Which format carries rota humour furthest |
| **Hook** | Pattern-named ("4 on 4 off people:") vs generic ("Shift workers:") | Profile visits per 1K reach · share of relevant comments | Does naming the pattern shrink reach but raise quality? *Hypothesis: yes, and it's worth it* |
| **Caption** | One line vs five lines with search keywords | Saves · profile visits | Does searchable copy convert, or get skipped? |
| **CTA** | Soft vs curiosity on matched memes; direct vs curiosity on product posts | Profile visits · link taps — and sends, as the cost | What a CTA costs in sends against what it adds in visits |
| **Posting time** | 06:30 (shift change) vs 14:00 (day sleepers waking) vs 20:30 (the evening before) | Reach in the first 3 hours · sends | When this audience actually scrolls |
| **Original vs trending** | House templates vs Concepts 6, 16 and 19 | Sends per 1K · follows per post | Whether trends earn their short life |
| **Product weight** | Free vs light vs forward (0 / 1 / 2) | Sends *and* profile visits, read together | Where the curve between "gets shared" and "sells" bends |

### 10.4 What is tracked, and what each metric is for

**Likes and views are recorded, and never used to decide anything.** A view is
a scroll past; a like is approval that goes nowhere. Neither says whether the
right person saw the post or did anything about it.

| Metric | Read in | Role |
|---|---|---|
| Reach, and % non-followers | Instagram Insights | **Denominator only** |
| **Shares (sends)** | Instagram Insights | **The primary content signal** — distribution, and the crew behaviour the product needs |
| **Saves** | Instagram Insights | Intent to come back — explainers and year-view posts |
| **Comments** — relevant vs wrong-audience | Read by hand | **Audience quality.** The only way to know whether the reach is on repeating rotas |
| **Profile visits** | Instagram Insights | Product curiosity |
| **Link clicks** | Instagram Insights (external link taps); story sticker taps | Intent |
| **Store visits** | Play Console — store listing visitors by UTM and by custom listing | Intent that reached the store |
| **Installs** | Play Console — acquisitions by UTM and listing; conversion | **The outcome the channel is judged on** |
| **Activated users** | **Not per channel.** Aggregate proxy: AdMob banner impressions, which need a populated calendar | Sanity check only |
| **Retention** | Play Console retained installers by channel if offered — check; otherwise uninstalls in aggregate | Whether Instagram installs behave like search installs |
| **Revenue** | AdMob, aggregate. Per channel it can only be **modelled**: installs × retention × ~$0.35 a year | Context, not a decision input — too small to steer by |
| **Minutes spent** | The log | **The cost** — the denominator of the most important number |

### 10.5 The five numbers for this channel

These are this channel's numbers, proposed because `docs/GROWTH.md` does not
exist. Reconcile them with its five when it does.

1. **Sends per 1,000 non-follower reach** — is the content travelling?
2. **Profile visits per 1,000 reach** — is it making anyone curious?
3. **Instagram store-listing visitors per week** (Play Console, UTM) — is the
   curiosity reaching the store?
4. **Instagram installs per week**, and the Instagram listing's conversion rate —
   is it producing users?
5. **Installs per hour of maintainer time**, against Reddit, Facebook and the
   noticeboard measured the same way — **is this the best use of the hour?**

### 10.6 The decision at week 12

| Outcome | Rule | Then |
|---|---|---|
| **Scale** | Installs per hour at least matches the best alternative channel, **and** rises over weeks 9–12, **and** relevant comments outnumber wrong-audience ones | Keep it as a standing channel at the winning frequency, still inside a fixed weekly budget |
| **Maintain** | Sends are healthy but installs per hour trails the alternatives | One post a week, recycled from the best twenty; keep cross-posting |
| **Stop** | Under 1 install per hour at both week 8 and week 12, **or** wrong-audience comments dominate | Archive the templates, leave the account dormant with its pinned posts, and move the three hours to channels 1–4 |

The thresholds are floors, not targets, and they are set low on purpose. The
question is not whether Instagram *can* produce installs — it can — but whether
it produces them at a better rate than the maintainer's other hours.

### 10.7 What a realistic result looks like

This is for calibration, not a forecast. **Every rate below is an assumption
that the week-4 data should replace.**

| | Typical post | A hit (1 in 20) |
|---|---|---|
| Reach | 3,000 | 50,000 |
| Profile visits (0.7%) | 21 | 350 |
| Link taps (10% of visits) | 2 | 35 |
| On Android (~46%, UK) | 1 | 16 |
| Installs (~35% listing conversion) | **~0.3** | **~6** |

Twenty posts come to about 12 installs. At four posts a week that is five weeks
and fifteen hours — a little under one install an hour, right at the stop line.
**The result is decided by rare hits, multiplied by the share code, which
cannot be seen.** That is the case for running the experiment, and also its
limit: if the hits don't come and crews don't adopt, it never compounds.

---

## 11. Rules the content does not break

Each comes from a decision already made about the app. Breaking one in a meme
breaks it in public.

- **Never name a competitor**, and never show, crop or hint at another app's
  interface. Category claims only, and only while they are true
  (`docs/STORE-LISTING.md`).
- **No pay or earnings.** Hours are "rostered". No payslip-dispute jokes (rule 8).
- **Never promise that reminders always arrive.** "Never miss a shift again" is
  banned: battery managers are real, and the listing tells the truth about them.
- **No photo scanning** (rule 10), no sync, no cloud.
- **The privacy claim is exactly as narrow as the privacy policy's.** Say *"your
  rota stays on your phone"* and *"no account"*. **Never say "nothing leaves your
  phone" or "no tracking"** — the advertising SDK sends device information
  (`PRD.md` §8). Instagram is the least forgiving place to be caught overstating
  it.
- **Notes never appear**, real or realistic. Never ask for them, and never show
  one that looks medical (rule 7).
- **No share codes or month screenshots solicited in public** (§4.1).
- **No uniforms, force crests, employer names or logos.** The characters are
  generic shift workers.
- **Don't punch down.** No patients, no members of the public, no named
  colleagues, no jokes about injury or death. This audience sees those for real.
- **Vocabulary:** rota, cycle, shift, off, changed day, reminder, code.
  *Premium*, *pro*, *upgrade* and *unlock* appear only to deny them ("nothing to
  unlock"). US captions may say "schedule", because that is the search word.
- **Android, said plainly** — in the bio, and in a reply whenever someone asks
  about iPhone.
- **Original work only.** No reposting other pages' memes; user posts are
  reshared only with permission and real transformation.

---

## Sources

Searched 27 September 2026. Figures are as the sources report them.

**Instagram — ranking, originality, features**
- [Adam Mosseri on shares as the key signal — Socialync](https://www.socialync.io/blog/adam-mosseri-shares-instagram-algorithm-2026)
- [Instagram algorithm 2026: ranking signals — Dataslayer](https://www.dataslayer.ai/blog/instagram-algorithm-2025-complete-guide-for-marketers)
- [Instagram removes recommendations for unoriginal aggregators (30 Apr 2026) — Tubefilter](https://www.tubefilter.com/2026/04/30/instagram-removes-algorithm-recommendations-repost-content-aggregator/)
- [Instagram limits hashtags to five — Social Media Today](https://www.socialmediatoday.com/news/instagram-implements-new-limits-on-hashtag-use/808309/)
- [Google indexing of Instagram posts — Inrō](https://www.inro.social/blog/instagram-google-indexing-2025)
- [Trial Reels — Instagram for Creators](https://creators.instagram.com/blog/instagram-trial-reels) · [2026 access — PostFast](https://postfa.st/blog/instagram-trial-reels)
- [Clickable links in Reels — Inrō](https://www.inro.social/blog/meta-verified-clickable-links-instagram-reels-pricing)
- [Music in Reels for business accounts — Tripepi Smith](https://tripepismith.com/insights/music-in-reels-business-accounts/) · [Foxi](https://www.foximusic.com/blog/instagram-reels-music-copyright-legal-guide/)

**Trends**
- [Latest Instagram trends, 23 Sep 2026 — SocialBee](https://socialbee.com/blog/latest-instagram-trends/)
- [Reels trends — Later](https://later.com/blog/instagram-reels-trends/)
- [Trending memes, September 2026 — NapoleonCat](https://napoleoncat.com/blog/trending-memes/)
- ["The saxophones are getting louder" — Dictionary.com](https://www.dictionary.com/culture/slang/the-saxophones-are-getting-louder)

**Meme rights**
- [Can you get sued for using a meme? — The Hustle](https://thehustle.co/can-you-get-sued-for-using-a-meme)
- [Grumpy Cat wins $710,000 — The Fashion Law](https://www.thefashionlaw.com/memes-have-rights-too-says-grumpy-cats-counsel/)
- [Nyan Cat and Keyboard Cat v Warner Bros — Revision Legal](https://revisionlegal.com/copyright/copyright-infringement/nyan-cat-copyright-infringement-warner-bros-lawsuit/)

**Audience and accounts**
- [@ukcophumour](https://www.instagram.com/ukcophumour/) · [@bluelight_lifestyle](https://www.instagram.com/bluelight_lifestyle/reel/C7CIsXPilhD/) · [@firefighterfenton](https://www.instagram.com/firefighterfenton/) · [@unfuckedfire](https://www.instagram.com/unfuckedfire/) · [@frequentflyersanddumpsterfires](https://www.instagram.com/frequentflyersanddumpsterfires/) · [@dispatcher_shenanigans](https://www.instagram.com/dispatcher_shenanigans/) · [@1st.responder.diaries](https://www.instagram.com/1st.responder.diaries/) · [@ukparamedichumour](https://www.instagram.com/ukparamedichumour/) · [@ukfirefighterhumour](https://www.instagram.com/ukfirefighterhumour/) · [@processmyoperator](https://www.instagram.com/processmyoperator/)
- [UK Ambulance Humour — Facebook](https://www.facebook.com/ukambulancehumour/)
- [Nurse meme accounts — Incredible Health](https://www.incrediblehealth.com/nurse-advice/questions/7f8e4d95/what-are-the-best-nurse-meme-instagram-accounts)
- [UK police shift patterns — BlueLineHub](https://bluelinehub.co.uk/police-shift-pattern-explained)
- [DuPont schedule — Sling](https://getsling.com/blog/dupont-shift-schedule/)

**Platform share and Google Play**
- [Android vs iOS market share 2026 — MobiLoud](https://www.mobiloud.com/blog/android-vs-ios-market-share/)
- [Acquisition reporting — Play Console](https://play.google.com/console/about/acquisitionreporting/)
- [Custom store listings — Play Console](https://play.google.com/console/about/customstorelistings/)
- [Measure acquisition and retention — Play Console Help](https://support.google.com/googleplay/android-developer/answer/6263332?hl=en)
- [Testing requirements for new personal developer accounts — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
