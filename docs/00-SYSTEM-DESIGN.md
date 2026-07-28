# Sammalani Secondary School — Alumni Platform System Design

**School:** Sammalani Secondary School, Chalitatala, Narail — established 1968, classes 6–10
**Working name:** *Sammalani Alumni* (the platform outlives the 2027 event — don't name it "Reunion")
**Scope:** Reunion 2027 registration → permanent alumni / school network
**Cohorts:** SSC batches 1968–2026 (59 batches)
**Author:** Md Masum Billah, SSC 2010 — sole engineer, plus a non-technical volunteer committee
**Locale:** Bangladesh — Bangla/English, bKash/Nagad, SMS + WhatsApp/IMO, low-end Android, 3G

> Suggested domains: `sammalani.org` / `sammalanialumni.com` / `ssschalitatala.org`.
> Register it before anything else and put it in the association's name, not yours (see §8, governance).

---

## 0. The actual problem

The engineering is easy. The hard problem is **data acquisition from 59 batches of people who will not fill in a form.**

Segment your users honestly:

| Segment | Batches | Approx. behaviour | Reachable via |
|---|---|---|---|
| Elders | 1968–1985 | Age 55–75. Feature phone or basic Android. Will not type Bangla. May not have email. Trusts a phone call from a classmate. | **A human calling them.** SMS. Sometimes IMO. |
| Mid | 1986–2005 | Age 35–55. Android + WhatsApp/Messenger. Will click a link, hates forms > 1 screen. | WhatsApp/Messenger link |
| Young | 2006–2026 | Age 15–35. Fully digital, will self-register, will share. | Anything |

**Design consequence:** the elders are never onboarded by software. They are onboarded by **another human using your software on their behalf.** Every design decision below follows from that.

The system therefore has two front doors:
1. **Self-service** — for mid/young. Optimize for 60 seconds, zero friction.
2. **Assisted entry** — for elders. A *Batch Ambassador* console optimized for "I am on the phone with him right now, let me type while he talks."

Ignore #2 and you will have ~2,000 records from 2005–2026 and ~40 from 1968–1990, and the reunion will feel like a young people's party. That is the failure mode.

---

## 1. Product strategy

### 1.1 Claim, don't create

Do **not** start from an empty database. Start from a **pre-seeded roster.**

Sources, in order of value:
- School admission/attendance registers (physical books) → photograph → OCR → seed
- Old SSC result gazettes / board result sheets per year
- Existing batch WhatsApp/Facebook groups → member lists
- Any prior reunion souvenir booklets (these are gold — they usually have name + father's name + address)

Even a name and a batch year is enough. Then the user experience flips from *"fill this form"* (rejected) to:

> "We found you. **Md. Rafiqul Islam, SSC 1974.** Is this you? Tap yes."

Claim rates on pre-seeded records run several times higher than cold registration. It also gives you an immediate, motivating public artifact: *"1968: 12 of 41 found"* — which drives the batch groups to hunt down the missing 29 themselves. **Let the crowd do your data entry.**

### 1.2 The virality loop that actually works here

```
Ambassador posts link in batch WhatsApp group
   → member claims profile in 60s
   → immediately shown: "Your batch: 18 of 63 registered. Who's missing?"
   → shows the missing names from the roster
   → "I know Kamal, let me add his number"  ← refer, don't register
   → system SMSes Kamal: "Rafiq bhai added you to SSC 1974 reunion list. Confirm: <link>"
```

The referral step is the whole product. A person will not fill in their own form, but they *will* name three friends. Make "add a classmate" a first-class action with a 2-field form (name + phone).

### 1.3 No passwords. Ever.

Phone number + 6-digit SMS OTP. That is the entire auth story for end users. Email optional.
- Elders cannot manage passwords, and a forgotten password is a permanently lost user.
- Everyone already has a phone number, and it's your natural dedupe key.
- Add "magic link" via WhatsApp/SMS for re-entry so returning users tap once.

Admins get password + TOTP, separately.

---

## 2. Onboarding surfaces (ranked by expected yield)

| # | Surface | Target | Effort | Why it works |
|---|---|---|---|---|
| 1 | **Ambassador console** | Elders | M | A human does the typing. Only thing that reaches 1968–1985. |
| 2 | **Claim-your-profile link** | All | S | Recognition beats recall. Pre-filled = 60s. |
| 3 | **"Add a classmate" referral** | All | S | Turns 1 registration into 3 leads. |
| 4 | **WhatsApp bot** | Mid | M | Zero install, zero login, familiar chat UI. |
| 5 | **QR poster / short link** | All | XS | Print for school gate, mosque/temple notice board, tea stalls near school. |
| 6 | **Public self-registration form** | Young | S | The obvious one. Least important. Build it, don't optimize it. |
| 7 | **Bulk import** | Committee | S | CSV/paste from existing group exports. |

### 2.1 Ambassador console — the highest-leverage screen in the system

Recruit 1–2 volunteers per batch (59 batches → ~80 people). They already exist informally; every batch group has one enthusiastic organizer. Give them:

- A **worklist** of their batch's unregistered roster names, with any known phone number
- **Call-and-type** layout: one screen, big fields, tab-navigable, autosave on every keystroke, no submit button
- Status per person: `Not contacted / Called-no answer / Wrong number / Refused / Deceased / Registered`
- One-tap actions: *Send them the link by SMS*, *Mark deceased*, *Merge duplicate*
- A leaderboard: "1974: 38/63 — 2nd place". Ambassadors are competitive. Exploit that shamelessly.
- Consent field: ambassador ticks "verbal consent given over phone" (see §7).

Build this on **day 2**, right after auth. It is worth more than the entire mobile app.

### 2.2 Minimum viable profile

Required (4 fields — this is a hard ceiling, resist every request to add a fifth):
- Full name
- Batch / SSC passing year
- Mobile number
- Photo *or* skip

Everything else is **progressive** — asked later, one question at a time, on subsequent visits:
nickname, section, house, present address, occupation, organization, blood group, spouse/family attending, T-shirt size, dietary preference, favourite teacher, one memory, old photographs.

Show a profile completeness ring. People finish rings.

### 2.3 Elder-friendly UI rules (non-negotiable)

- Base font **18px**, minimum touch target **48×48px**
- Bangla-first toggle, remembered per user; Bangla numerals option
- **Never** a dropdown with 59 items for batch year — use a numeric keypad input with validation, or "which year did you pass SSC?" free-typed
- No multi-step wizard with hidden progress. One scrolling page.
- No modals, no tooltips, no hover-only affordances
- Voice input button on every text field (browser Web Speech API, Bangla)
- Every error message in plain Bangla, stating what to do, not what went wrong
- Works on 3G: target < 200KB initial JS, server-rendered first paint
- A visible **"Call for help: 01XXXXXXXXX"** button on every screen, wired to a real volunteer

### 2.4 The AI layer (where it genuinely pays)

Use an LLM for the messy-input problems, not for chatbot theatre:

- **Unstructured → structured.** Ambassador pastes a forwarded WhatsApp blob (`"Rafiq bhai 1974 batch, ekhon Dubai te ache, 01711...")` → extract name, batch, location, phone. Human confirms. This is 10× faster than form-filling.
- **Register OCR cleanup.** Scanned handwritten Bangla registers → OCR is noisy → LLM normalizes names, guesses batch from page context, flags low confidence for human review.
- **Fuzzy dedupe.** "Md. Rafiqul Islam" / "Rafiq" / "মোঃ রফিকুল ইসলাম" / "M. R. Islam" — same person? Phone+batch is the deterministic key; LLM + trigram similarity scores the rest into a human review queue. **Never auto-merge.**
- **Memory collection.** Post-reunion, prompt each alum with one question ("Who was your class teacher in class 8?") and compile answers into a souvenir. Cheap, high emotional return, drives re-engagement.
- **Voice note → profile.** Elder sends a Bangla voice note to WhatsApp → transcribe → extract → ambassador confirms.

Keep every AI output behind a human confirm step for identity data. Wrong merges are unrecoverable socially.

---

## 3. Architecture

### 3.1 Shape: modular monolith. Not microservices.

You are one engineer. One deployable Spring Boot service, internally partitioned into modules with enforced boundaries. Use **Spring Modulith** so the boundaries are compile-time-verified and you can split a module out later if you ever need to (you won't).

```
                    ┌────────────────────────────┐
   Web PWA ────────►│                            │
   (Next.js)        │      Cloudflare (CDN,       │
                    │      TLS, WAF, cache)       │
   Android/iOS ────►│                            │
   (Capacitor wrap) └─────────────┬──────────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │   alumni-api                │
                    │  Spring Boot 4.1 / Java 25  │
                    │                             │
                    │  identity   directory       │
                    │  events     payments        │
                    │  content    media           │
                    │  messaging  admin           │
                    │  ingestion  ai              │
                    └───┬────────┬────────┬───────┘
                        │        │        │
              ┌─────────▼──┐ ┌───▼───┐ ┌──▼─────────┐
              │ PostgreSQL │ │ Redis │ │ S3-compat  │
              │  16        │ │       │ │ object     │
              └────────────┘ └───────┘ └────────────┘
                        │
              ┌─────────▼─────────────────────────┐
              │ External: SSLCOMMERZ/bKash,        │
              │ SMS gateway, WhatsApp Cloud API,   │
              │ Anthropic API, SMTP                │
              └────────────────────────────────────┘
```

### 3.2 Modules

| Module | Responsibility |
|---|---|
| `identity` | Phone OTP, sessions/JWT, roles, magic links, admin auth |
| `directory` | Person, Batch, ClassEnrollment, claim workflow, dedupe queue, search |
| `events` | Event, ticket types, registration, guests, seating, check-in |
| `payments` | Payment intent, gateway callbacks, reconciliation, donations, refunds |
| `content` | Notices, news, blog, teacher profiles, In Memoriam, committee pages |
| `media` | Uploads, image resizing, galleries, albums, moderation |
| `messaging` | SMS/WhatsApp/email dispatch, templates, campaigns, delivery log |
| `ingestion` | CSV/paste import, OCR pipeline, staging + review |
| `ai` | LLM extraction, dedupe scoring, transcription — all behind one interface |
| `admin` | Ambassador console, moderation, exports, audit log, dashboards |
| `shared` | Auditing, outbox, idempotency, tenancy-free utils |

Rule: modules talk via published interfaces or Spring application events, never by reaching into each other's repositories. Modulith's `ApplicationModules.verify()` in a test enforces it.

### 3.3 Stack

**Backend** — full pinned versions and rationale in [`03-TECH-STACK.md`](03-TECH-STACK.md)
- **Java 25 (LTS)** — virtual threads for this I/O-bound workload; Scoped Values for audit-actor context; compact object headers for heap savings on a small VPS
- **Spring Boot 4.1.0** (Framework 7.0.8, Security 7.1.0, Hibernate 7.4.1, Jackson 3.1.4)
- **Spring Modulith 2.1.0** — compile-verified module boundaries (own BOM, not in the Boot BOM)
- PostgreSQL 16/17 — `pg_trgm` for fuzzy name search, `unaccent`, JSONB for flexible profile extras, full-text search. **No Elasticsearch.** You have ~15k rows.
- Flyway 12 for migrations
- Redis — OTP store, rate limits, session cache, job queue
- Transactional outbox table + a `@Scheduled` poller for SMS/WhatsApp/email. Do not add Kafka/RabbitMQ.
- Gradle 9.6.1 (Kotlin DSL), Testcontainers 2, JUnit 6, springdoc-openapi
- Records over MapStruct; thin `RestClient` over Spring AI

**Frontend — one codebase**
- Next.js 14 (App Router) + TypeScript + Tailwind, built as an **installable PWA**
- Wrapped with **Capacitor** to produce Play Store / App Store binaries from the same code
- Rationale: you are a Java engineer with limited time. Two native codebases is a trap. A PWA gives you web + "app" immediately; the store binary matters mainly for credibility with the committee ("amader app ache"), and Capacitor gives you that in an afternoon. Revisit native only if you need heavy offline or push nuance later.
- next-intl for bn/en

**Infra (start)**
- One VPS, 4 vCPU / 8GB (any regional provider, or Hetzner). Docker Compose: api, postgres, redis, caddy.
- Cloudflare in front — free TLS, CDN, WAF, bot protection
- Object storage: Cloudflare R2 or S3 (photos will be the bulk of your storage — old scanned pictures)
- Backups: `pg_dump` nightly → offsite bucket, 30-day retention, **and test a restore before you launch**
- Monitoring: Spring Actuator + Uptime Kuma + Sentry. Grafana only if you enjoy it.

Estimated run cost: **$25–45/month** outside the event window. That matters — someone has to pay this for 20 years.

### 3.4 Why not microservices / Kafka / k8s

15,000 users, a few hundred concurrent at peak on registration day, one engineer, a 20-year maintenance horizon with likely handover to a volunteer. Every distributed component you add is an operational liability you personally carry. The monolith will handle this load on a single 8GB box with room to spare.

---

## 4. Data model

Core tables (PostgreSQL). `person` is deliberately generous about identity: a human may be an alum *and* a teacher *and* a parent.

```sql
-- Everyone. Alumni, teachers, staff, current students, guests.
create table person (
  id                uuid primary key default gen_random_uuid(),
  full_name         text not null,
  full_name_bn      text,
  nickname          text,
  photo_url         text,
  gender            text,
  date_of_birth     date,
  blood_group       text,
  deceased          boolean not null default false,
  deceased_on       date,
  bio               text,
  extras            jsonb not null default '{}',      -- progressive fields
  status            text not null default 'SEEDED',   -- SEEDED|INVITED|CLAIMED|VERIFIED|MERGED|BLOCKED
  claimed_at        timestamptz,
  verified_by       uuid references person(id),
  merged_into_id    uuid references person(id),
  source            text not null default 'MANUAL',   -- MANUAL|REGISTER_OCR|IMPORT|SELF|REFERRAL|AMBASSADOR
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now()
);
create index on person using gin (full_name gin_trgm_ops);

create table contact (
  id            uuid primary key default gen_random_uuid(),
  person_id     uuid not null references person(id) on delete cascade,
  type          text not null,          -- MOBILE|WHATSAPP|EMAIL|ADDRESS|FACEBOOK|LINKEDIN
  value         text not null,
  is_primary    boolean not null default false,
  verified      boolean not null default false,
  visibility    text not null default 'BATCH',  -- PUBLIC|ALUMNI|BATCH|COMMITTEE|PRIVATE
  created_at    timestamptz not null default now()
);
-- one verified mobile == one person
create unique index on contact (value) where type='MOBILE' and verified;

create table batch (
  year          int primary key,             -- SSC passing year, 1968..2026
  label         text,                        -- 'SSC 1974'
  motto         text,
  cover_url     text
);

-- studied here vs passed here — matters a lot for old batches
create table enrollment (
  id            uuid primary key default gen_random_uuid(),
  person_id     uuid not null references person(id) on delete cascade,
  batch_year    int references batch(year),
  role          text not null,        -- STUDENT|TEACHER|STAFF|HEADMASTER
  from_class    int, to_class int,    -- 6..10
  section       text,
  house         text,
  roll_no       text,
  passed_here   boolean,              -- false = left before SSC, still an alum
  subject       text,                 -- for teachers
  from_year     int, to_year int,     -- for teachers/staff
  is_primary    boolean not null default true
);

create table app_role (
  person_id     uuid not null references person(id) on delete cascade,
  role          text not null,        -- ALUMNI|AMBASSADOR|COMMITTEE|MODERATOR|ADMIN|SUPERADMIN
  scope         text,                 -- e.g. batch year for AMBASSADOR
  primary key (person_id, role, scope)
);

create table event (
  id            uuid primary key default gen_random_uuid(),
  slug          text unique not null,
  title         text not null, title_bn text,
  description   text,
  starts_at     timestamptz, ends_at timestamptz,
  venue         text, venue_map_url text,
  capacity      int,
  reg_opens_at  timestamptz, reg_closes_at timestamptz,
  status        text not null default 'DRAFT'
);

create table ticket_type (
  id            uuid primary key default gen_random_uuid(),
  event_id      uuid not null references event(id),
  name          text not null,        -- Alumni / Spouse / Child / Teacher(free) / Donor
  amount_bdt    numeric(10,2) not null,
  max_qty       int,
  eligible_role text
);

create table registration (
  id            uuid primary key default gen_random_uuid(),
  event_id      uuid not null references event(id),
  person_id     uuid not null references person(id),
  ticket_type_id uuid not null references ticket_type(id),
  guests        jsonb not null default '[]',   -- [{name, relation, age, ticket_type_id}]
  tshirt_size   text,
  food_pref     text,
  amount_due    numeric(10,2) not null,
  status        text not null default 'PENDING', -- PENDING|PAID|WAIVED|CANCELLED
  qr_token      text unique,
  checked_in_at timestamptz,
  registered_by uuid references person(id),     -- ambassador who filled it
  created_at    timestamptz not null default now(),
  unique (event_id, person_id)
);

create table payment (
  id            uuid primary key default gen_random_uuid(),
  registration_id uuid references registration(id),
  person_id     uuid references person(id),
  purpose       text not null,        -- TICKET|DONATION|SCHOLARSHIP_FUND
  amount_bdt    numeric(10,2) not null,
  method        text,                 -- BKASH|NAGAD|CARD|BANK|CASH
  gateway_ref   text,
  txn_id        text,
  status        text not null default 'INITIATED',
  idempotency_key text unique,
  raw_callback  jsonb,
  collected_by  uuid references person(id),   -- for CASH
  created_at    timestamptz not null default now()
);

create table referral (            -- "I know this person"
  id            uuid primary key default gen_random_uuid(),
  referrer_id   uuid references person(id),
  name          text not null,
  phone         text,
  batch_year    int,
  note          text,
  status        text not null default 'NEW',  -- NEW|INVITED|CLAIMED|BAD_NUMBER|DUPLICATE
  matched_person_id uuid references person(id),
  created_at    timestamptz not null default now()
);

create table outreach (            -- ambassador call log
  id            uuid primary key default gen_random_uuid(),
  person_id     uuid not null references person(id),
  by_person_id  uuid not null references person(id),
  channel       text,                -- CALL|SMS|WHATSAPP|VISIT
  outcome       text,                -- NO_ANSWER|WRONG_NUMBER|WILL_JOIN|REFUSED|DECEASED|REGISTERED
  note          text,
  consent_given boolean,
  occurred_at   timestamptz not null default now()
);

create table merge_candidate (
  id            uuid primary key default gen_random_uuid(),
  person_a      uuid not null references person(id),
  person_b      uuid not null references person(id),
  score         numeric(4,3) not null,
  reasons       jsonb,
  status        text not null default 'OPEN',  -- OPEN|MERGED|REJECTED
  decided_by    uuid references person(id),
  decided_at    timestamptz
);

-- plus: media_asset, album, post, notice, memorial, committee_member,
--       outbox_message, audit_log, import_batch, import_row
```

Design notes worth defending:
- **`person` is not `user`.** Most rows will never log in (seeded, deceased, teachers from 1970). Auth attaches to a person, it doesn't define one.
- **`extras jsonb`** absorbs the 40 fields the committee will demand in month 3 without a migration each time. Promote a field to a real column only when you need to query or index it.
- **`merged_into_id`** — soft-merge, never `DELETE`. You will merge wrongly at least once and need to reverse it.
- **`passed_here`** — for old batches, many students left after class 8 or transferred. They are still alumni and they will be offended if the system says otherwise.
- **`deceased`** — for batches from the 1960s–70s this is a significant fraction. Handle it with dignity: a memorial page, never an SMS to a deceased person's number, and a family-contact field.
- **`visibility` per contact** — the single most important privacy control. Never expose a phone number publicly by default.

---

## 5. API surface (v1)

REST/JSON, `/api/v1`. Cursor pagination. Problem+JSON errors. OpenAPI generated.

**Auth**
```
POST /auth/otp/request        { phone }              → { challengeId, expiresIn }
POST /auth/otp/verify         { challengeId, code }  → { accessToken, refreshToken, person }
POST /auth/magic/consume      { token }              → session
POST /auth/refresh
POST /auth/logout
```

**Claim flow**
```
GET  /public/lookup?name=&batchYear=      → masked candidate list (name + batch only)
POST /claims                              { personId, phone } → OTP challenge
POST /claims/{id}/confirm                 { code, corrections } → claimed profile
POST /public/register                     { name, batchYear, phone } → for "not in list"
```

**Directory**
```
GET  /me                       PATCH /me
GET  /batches                  GET /batches/{year}
GET  /batches/{year}/members   ?status=&q=
GET  /persons/{id}             (visibility-filtered)
GET  /search?q=                (trigram + fts, alumni-only)
POST /referrals                { name, phone, batchYear }
```

**Events**
```
GET  /events                   GET /events/{slug}
POST /events/{id}/registrations
GET  /me/registrations
POST /registrations/{id}/pay   → { gatewayRedirectUrl }
POST /webhooks/payments/{provider}    (public, signature-verified, idempotent)
POST /checkin                  { qrToken }   (gate volunteers)
```

**Ambassador**
```
GET  /amb/worklist?batch=&status=
POST /amb/persons              (create on behalf)
PATCH /amb/persons/{id}
POST /amb/outreach             (log a call)
POST /amb/invite               { personId, channel }
POST /amb/extract              { rawText } → structured draft (AI, requires confirm)
GET  /amb/leaderboard
```

**Admin**
```
POST /admin/imports            (CSV/paste) → staging
GET  /admin/imports/{id}/rows  PATCH row   POST /admin/imports/{id}/commit
GET  /admin/merge-candidates   POST /admin/merge  POST /admin/unmerge
POST /admin/campaigns          (SMS/WhatsApp blast, with dry-run + cost estimate)
GET  /admin/exports/*          (CSV: registrations, payments, tshirt sizes, food)
GET  /admin/audit
```

**Content** — `/notices`, `/posts`, `/teachers`, `/memorials`, `/albums`, `/gallery`.

---

## 6. Critical flows

### 6.1 Claim
```
User taps WhatsApp link → /claim?b=1974
  → "Find your name" — list of 63 names for batch 1974, no phone numbers shown
  → taps "Md. Rafiqul Islam"
  → "Enter your mobile to confirm it's you"  → OTP
  → verified → profile pre-filled from roster → asks only for photo
  → lands on batch page: "18 of 63 found. Help us find the rest →"
```
Total: ~60 seconds, 3 taps + phone number + OTP.

**Abuse guard:** the lookup list shows name+batch only. Claiming requires OTP. Rate-limit lookups per IP. If two people claim the same record, flag to committee — do not resolve it in software.

### 6.2 Payment (bKash/Nagad via SSLCOMMERZ or direct)
```
POST /registrations/{id}/pay  → create payment (INITIATED, idempotency_key)
  → redirect to gateway
  → user pays in bKash app
  → gateway webhook → verify signature → verify amount → mark PAID (idempotent)
  → outbox: SMS receipt + WhatsApp e-ticket with QR
  → user returns to /payment/return → poll status (never trust the return URL)
```
**Always** reconcile from the webhook, never the browser redirect. Store `raw_callback`. Provide a manual `CASH`/`BANK` path — a large fraction of elder alumni will hand cash to a committee member, and that must be recordable with `collected_by` for accountability.

### 6.3 Event day check-in
QR on the e-ticket → volunteer scans with the PWA → `POST /checkin`. Offline-tolerant: cache the day's registration list in IndexedDB, queue scans, sync when the venue wifi returns (it will not be good). Also allow **search by name or phone** at the gate — half the elders will arrive with no phone, no QR, and no patience. Print a paper fallback list per batch.

---

## 7. Privacy, consent, safety

This is a directory of real people's phone numbers and home addresses, including elderly people. Treat it seriously — a leak here is a permanent reputational injury to you personally, in your own community.

- **Consent at capture.** Ambassador records verbal consent. The invite SMS states what's stored and links to a plain-Bangla privacy note.
- **Default visibility = BATCH.** Nothing about a person is public unless they opt in. No public phone numbers, ever.
- **No scraping.** Directory endpoints require an authenticated alumni session, are rate-limited, and return cursor-paginated results only. No "download all members".
- **Right to be removed** — a one-tap "hide my profile" and an admin hard-delete path.
- **Deceased handling** — memorial flag suppresses all outbound messaging immediately.
- **Audit log** on every admin/ambassador read of contact data. Ambassadors see only their own batch.
- **Data export** for the user (their own data), not for admins-in-bulk without a second approver.
- Encrypt at rest (disk + `pg` backups), TLS everywhere, secrets in env/Vault not git.
- OWASP basics: parameterized queries (JPA), CSRF for cookie flows, strict CORS, CSP, no PII in logs.
- **OTP hardening:** 6 digits, 5-minute TTL, 5 attempts, per-phone and per-IP rate limits, and a **spend cap on the SMS gateway** — OTP endpoints are a favourite way to burn someone else's money.

---

## 8. Delivery plan

### Day 0 — before any code: pilot on SSC 2010

You are SSC 2010. That batch is your **pilot cohort**, and starting there is not sentiment — it's the correct engineering sequence:

- You already have their trust, their WhatsApp group, and their phone numbers. No cold outreach.
- They are digitally fluent, so they will self-register and *tell you where the UX hurts* — feedback the 1974 batch will never give you, they'll just silently not register.
- A working batch page showing **"SSC 2010 — 47 of 88 found"** is your recruitment pitch to the other 58 batches. Abstract promises don't recruit ambassadors; a live page with real names does.
- Fix everything the pilot exposes *before* the elders ever see it. You get exactly one first impression with a 70-year-old alum — if the link fails or the OTP doesn't arrive, they will not try twice, and their batchmates will hear about it.

Sequence: **2010 → 2005–2020 (adjacent, digital) → 1990–2004 → 1968–1989 (ambassador-driven)**. Work outward from yourself.

Two non-code tasks to start today, in parallel with development, because they have the longest lead time:
1. Get permission from the current headmaster to **photograph the old admission/attendance registers**. This is your seed data and it's gated on a human decision you don't control.
2. Recruit the first **10 ambassadors** from the oldest batches you can reach. Ask your parents' generation and the current school staff — they know who's still in touch with the 1970s alumni.

### Day 1 (the "one day" you asked about — realistically 12–14 hours)
This is achievable if you scope it to *acquisition only*. Ship:

1. Spring Boot skeleton + Postgres + Flyway + Docker Compose *(1h)*
2. `person`, `contact`, `batch`, `enrollment`, `referral`, `outreach` migrations *(1h)*
3. Phone OTP auth + JWT + roles *(2h)*
4. Public landing page + claim/lookup + self-register *(3h)*
5. "Add a classmate" referral form *(1h)*
6. Ambassador worklist + create/edit on behalf *(3h)*
7. SMS integration + invite template *(1h)*
8. Deploy behind Cloudflare, seed batches 1968–2026 *(1h)*

**Explicitly deferred:** payments, gallery, app store build, teacher profiles, notices, memorial, AI. None of them collect a single extra name.

The bottleneck on day 1 is not code — it's **recruiting the 59 ambassadors and getting the old registers photographed.** Start that in parallel, today, before you write a line of code.

### Week 1
Bulk import + dedupe queue, batch pages with the "found/missing" counter, ambassador leaderboard, profile completeness, Bangla UI, WhatsApp Cloud API onboarding.

### Month 1
Payments (bKash/Nagad + cash recording), event + ticket types + guests, e-ticket QR, admin exports, campaign sender, media/gallery, Capacitor app store builds.

### Month 2–3 (pre-event)
Check-in app with offline mode, seating/table assignment, souvenir data collection, volunteer scheduling, live event-day dashboard.

### Post-event → the long game
This is the part most reunion sites fail. After the event, engagement collapses unless the platform does something ongoing:
- **School noticeboard** — the current school actually uses it (results, notices, admission info)
- **Teacher directory + In Memoriam** — the emotional core, and it never goes stale
- **Scholarship fund** — recurring donations, transparent ledger, recipient stories. This is the single strongest reason a 1974 alum returns monthly.
- **Job/mentorship board** — young alumni find seniors; seniors love being asked
- **Batch sub-groups** with their own pages, photos, and mini-reunions
- **Annual data refresh** — one SMS a year: "is this still your number?"
- **Governance:** a registered alumni association, 2+ people with admin/DNS/payment access, and a documented handover. Do not be the single point of failure for a 20-year institution.

---

## 9. Repository layout

```
reunion/
├── docs/
│   ├── 00-SYSTEM-DESIGN.md
│   ├── 01-adr/                     # architecture decision records
│   ├── 02-runbook.md
│   └── 03-TECH-STACK.md
├── api/                            # Spring Boot
│   ├── src/main/java/org/sammalani/alumni/
│   │   ├── AlumniApplication.java
│   │   ├── identity/  directory/  events/  payments/
│   │   ├── content/   media/      messaging/
│   │   ├── ingestion/ ai/         admin/     shared/
│   │   └── config/
│   └── src/main/resources/db/migration/   # Flyway V1__init.sql ...
├── web/                            # Next.js PWA
│   ├── app/(public)/ (auth)/ (member)/ (ambassador)/ (admin)/
│   ├── components/  lib/  messages/{bn,en}.json
│   └── capacitor.config.ts
├── ops/
│   ├── docker-compose.yml  Caddyfile  backup.sh
└── .github/workflows/ci.yml
```

---

## 10. Decisions, stated plainly

| Question | Decision | Because |
|---|---|---|
| Microservices? | No — modular monolith | One engineer, 20-year horizon, trivial load |
| Native apps? | PWA + Capacitor wrap | One codebase; store presence without a second stack |
| Passwords? | No, phone OTP only | Elders cannot manage passwords |
| Search engine? | Postgres `pg_trgm` + FTS | ~15k rows; Elasticsearch is pure overhead |
| Message broker? | Transactional outbox table | Same reason |
| Start from empty DB? | No — pre-seed the roster | Claim beats create, by a wide margin |
| Primary onboarding channel? | Humans phoning humans | The only thing that reaches 1968–1985 |
| Auto-merge duplicates? | Never | Social cost of a wrong merge is unrecoverable |
| Public phone numbers? | Never | It's a directory of elderly people's contact details |

---

## 11. Risks

| Risk | Mitigation |
|---|---|
| **Old batches never register** — the defining risk | Ambassador programme, staffed and started before launch. Track 1968–1985 coverage as *the* KPI. |
| Bad/duplicate data floods in | Staging + review for every import; merge queue; phone-verified as the trust signal |
| SMS costs run away | Spend cap, per-phone throttle, prefer WhatsApp where available, dry-run cost estimate on campaigns |
| Payment disputes | Immutable payment log, `raw_callback`, `collected_by` on cash, daily reconciliation export to the treasurer |
| Committee scope creep | The 4-field cap on required profile fields; a written "not in v1" list |
| Bus factor = you | 2+ admins, credentials in a shared vault, runbook, boring stack a volunteer can maintain |
| Platform dies after 2027 | Fund the scholarship ledger and school noticeboard — utility, not nostalgia, is what sustains it |

---

## 12. Success metrics

Track from day 1 — a public counter on the landing page is itself a growth mechanism.

- **Coverage by batch** = registered / roster, with 1968–1990 broken out separately (this is the number that matters)
- Claim conversion: invite sent → claimed
- Referrals per registered alum (target > 1.5)
- Ambassador activity: calls logged, conversions
- Median time-to-complete registration (target < 90s)
- Profile completeness distribution
- Post-event 90-day return rate — the honest test of whether you built a platform or a form
