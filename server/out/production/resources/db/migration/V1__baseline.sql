-- ---------------------------------------------------------------------------
-- Baseline schema. Follows docs/00-SYSTEM-DESIGN.md §4, narrowed to what this
-- service actually serves today: identity, batches, the reunion, registrations,
-- offline payments, and the append-only record of every human decision.
--
-- Conventions:
--   * uuid primary keys, generated in the database (gen_random_uuid is core in PG13+)
--   * timestamptz everywhere; the reunion is in Dhaka but the committee is not
--   * enums live as text + CHECK, not pg enums: adding a value must not need a
--     lock on a table someone is reading at the time
-- ---------------------------------------------------------------------------

create extension if not exists pg_trgm;

-- ---------------------------------------------------------------------------
-- Reference
-- ---------------------------------------------------------------------------

create table batch (
    year            int primary key,
    label           text not null,
    label_bn        text,
    roster_estimate int  not null default 0,      -- how many the register says there were
    cover_url       text
);

comment on column batch.roster_estimate is
    'Best estimate of the batch size from the school register. The denominator of the "found so far" counter.';

-- ---------------------------------------------------------------------------
-- People. Note there is no password here: most of these rows will never log in.
-- ---------------------------------------------------------------------------

create table person (
    id             uuid primary key default gen_random_uuid(),
    name           text not null,
    name_bn        text,
    batch_year     int references batch (year),
    status         text not null default 'SEEDED',
    phone          text,
    email          text,
    gender         text,
    dob            date,
    blood_group    text,
    occupation     text,
    city           text,
    deceased       boolean not null default false,
    extras         jsonb not null default '{}'::jsonb,
    merged_into_id uuid references person (id),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz,
    constraint person_status_chk check (status in ('SEEDED', 'CLAIMED', 'VERIFIED', 'REJECTED')),
    constraint person_gender_chk check (gender is null or gender in ('MALE', 'FEMALE', 'OTHER'))
);

-- One verified mobile is one person. Seeded rows have no phone at all, and
-- NULLs do not collide, so a register can hold 15,000 phoneless alumni.
create unique index person_phone_uidx on person (phone) where phone is not null;
create index person_batch_status_idx on person (batch_year, status);
-- "Is my name in this list?" is a fuzzy search over two scripts.
create index person_name_trgm_idx on person using gin (name gin_trgm_ops);
create index person_name_bn_trgm_idx on person using gin (name_bn gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- Admin access. A separate table because a nullable password_hash on a table of
-- 15,000 mostly-passwordless rows invites the query that forgets to check it.
-- ---------------------------------------------------------------------------

create table admin_credential (
    person_id       uuid primary key references person (id) on delete cascade,
    username        text not null,
    password_hash   text not null,              -- Argon2id. Never leaves the server.
    role            text not null,
    active          boolean not null default true,
    must_change     boolean not null default true,
    last_login_at   timestamptz,
    failed_attempts int not null default 0,
    locked_until    timestamptz,
    created_by      uuid references person (id),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz,
    constraint admin_role_chk check (role in ('SUPER_ADMIN', 'GROUP_ADMIN'))
);

-- Case-insensitive without depending on the citext extension.
create unique index admin_username_uidx on admin_credential (lower(username));

-- Which batch years a GROUP_ADMIN may act on. A SUPER_ADMIN has no rows here
-- and sees everything — absence of scope is not absence of authority.
create table admin_batch_scope (
    person_id  uuid not null references admin_credential (person_id) on delete cascade,
    batch_year int not null references batch (year),
    primary key (person_id, batch_year)
);
create index admin_batch_scope_year_idx on admin_batch_scope (batch_year);

-- ---------------------------------------------------------------------------
-- The event
-- ---------------------------------------------------------------------------

create table event (
    id            uuid primary key default gen_random_uuid(),
    slug          text unique not null,
    title         text not null,
    title_bn      text,
    subtitle      text,
    subtitle_bn   text,
    starts_at     timestamptz,
    ends_at       timestamptz,
    venue         text,
    venue_bn      text,
    venue_map_url text,
    capacity      int,
    reg_opens_at  timestamptz,
    reg_closes_at timestamptz,
    status        text not null default 'OPEN',
    created_at    timestamptz not null default now(),
    updated_at    timestamptz,
    constraint event_status_chk check (status in ('DRAFT', 'OPEN', 'CLOSED', 'DONE'))
);

create table ticket_type (
    id         uuid primary key default gen_random_uuid(),
    event_id   uuid not null references event (id) on delete cascade,
    code       text not null,
    name       text not null,
    name_bn    text,
    note       text,
    note_bn    text,
    amount_bdt numeric(10, 2) not null,
    relation   text,                              -- which guest relation it covers
    sort_order int not null default 0,
    unique (event_id, code),
    constraint ticket_amount_chk check (amount_bdt >= 0),
    constraint ticket_relation_chk check (relation is null or relation in ('SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER'))
);

-- ---------------------------------------------------------------------------
-- Registrations
-- ---------------------------------------------------------------------------

create table registration (
    id             uuid primary key default gen_random_uuid(),
    event_id       uuid not null references event (id),
    person_id      uuid not null references person (id),
    batch_year     int references batch (year),
    ticket_type_id uuid references ticket_type (id),
    guests         jsonb not null default '[]'::jsonb,
    tshirt_size    text,
    food_pref      text,
    member_note    text,
    amount_due     numeric(10, 2) not null default 0,
    status         text not null default 'DRAFT',
    payment_status text not null default 'UNPAID',
    submitted_at   timestamptz,
    qr_token       text unique,
    checked_in_at  timestamptz,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz,
    unique (event_id, person_id),
    constraint registration_status_chk check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    constraint registration_payment_status_chk check (payment_status in ('UNPAID', 'REPORTED', 'CONFIRMED', 'REJECTED')),
    constraint registration_amount_chk check (amount_due >= 0)
);

comment on column registration.batch_year is
    'Denormalised from person so the review queue can filter and page without joining. Copied on submit.';
comment on column registration.payment_status is
    'Derived from the payment rows, maintained in the same transaction. The payment table stays the source of truth; this exists so the queue is one index scan.';

-- The review queue reads exactly one way: filter by a status (optionally within
-- a batch), newest first, paged by keyset. These four indexes are that query.
create index registration_member_queue_idx on registration (status, submitted_at desc, id desc);
create index registration_member_queue_batch_idx on registration (batch_year, status, submitted_at desc, id desc);
create index registration_payment_queue_idx on registration (payment_status, submitted_at desc, id desc);
create index registration_payment_queue_batch_idx on registration (batch_year, payment_status, submitted_at desc, id desc);
create index registration_person_idx on registration (person_id);

-- ---------------------------------------------------------------------------
-- Money that moved outside this system, recorded after the fact. There is no
-- gateway, so there is no INITIATED state and nothing to reconcile against a
-- provider.
-- ---------------------------------------------------------------------------

create table payment (
    id              uuid primary key default gen_random_uuid(),
    registration_id uuid references registration (id) on delete cascade,
    person_id       uuid references person (id),
    purpose         text not null default 'TICKET',
    amount_bdt      numeric(10, 2) not null,
    method          text,
    reference       text,
    paid_to_id      uuid references person (id),
    reported_at     timestamptz not null default now(),
    status          text not null default 'REPORTED',
    created_at      timestamptz not null default now(),
    updated_at      timestamptz,
    constraint payment_status_chk check (status in ('REPORTED', 'CONFIRMED', 'REJECTED')),
    constraint payment_purpose_chk check (purpose in ('TICKET', 'DONATION')),
    constraint payment_method_chk check (method is null or method in ('BKASH', 'NAGAD', 'ROCKET', 'BANK', 'CASH')),
    constraint payment_amount_chk check (amount_bdt >= 0)
);

-- Two members cannot claim the same bKash transaction. A rejected claim frees
-- the reference again, because the usual cause is a typo.
create unique index payment_reference_uidx on payment (method, reference)
    where reference is not null and status <> 'REJECTED';
create index payment_registration_idx on payment (registration_id, reported_at desc);

-- ---------------------------------------------------------------------------
-- Every human decision. Append-only: a reversal is a new row, never an UPDATE.
-- ---------------------------------------------------------------------------

create table review (
    id           uuid primary key default gen_random_uuid(),
    subject_type text not null,
    subject_id   uuid not null,
    batch_year   int references batch (year),
    decision     text not null,
    note         text,
    decided_by   uuid not null references person (id),
    decided_at   timestamptz not null default now(),
    constraint review_subject_chk check (subject_type in ('REGISTRATION', 'PERSON_VERIFICATION', 'PAYMENT')),
    constraint review_decision_chk check (decision in ('APPROVED', 'REJECTED', 'CONFIRMED')),
    -- "Your registration was declined" with no reason is how you lose an alum
    -- permanently. The API refuses it; so does the table.
    constraint review_reason_chk check (decision <> 'REJECTED' or (note is not null and length(btrim(note)) > 0))
);

create index review_subject_idx on review (subject_type, subject_id, decided_at desc);
create index review_decider_idx on review (decided_by, decided_at desc);
create index review_batch_idx on review (batch_year, decided_at desc);

-- ---------------------------------------------------------------------------
-- Content and outreach
-- ---------------------------------------------------------------------------

create table notice (
    id           uuid primary key default gen_random_uuid(),
    title        text not null,
    title_bn     text,
    body         text,
    body_bn      text,
    pinned       boolean not null default false,
    published_at timestamptz not null default now(),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz
);
create index notice_feed_idx on notice (pinned desc, published_at desc);

create table referral (
    id                uuid primary key default gen_random_uuid(),
    referrer_id       uuid references person (id),
    name              text not null,
    phone             text,
    batch_year        int,
    note              text,
    status            text not null default 'NEW',
    matched_person_id uuid references person (id),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz,
    constraint referral_status_chk check (status in ('NEW', 'INVITED', 'CLAIMED', 'BAD_NUMBER', 'DUPLICATE'))
);
create index referral_batch_idx on referral (batch_year, status);
