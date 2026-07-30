-- ---------------------------------------------------------------------------
-- Two changes that belong in one migration, because each is unsafe without the
-- other: rows are no longer deleted, and every write says who made it.
--
-- The pairing is not stylistic. A tombstone that nobody can attribute is worse
-- than a deleted row — it looks like live data with no explanation. And an audit
-- trail whose subject can vanish from the table it describes answers "who
-- changed this" with a dangling id.
--
-- Conventions follow V1: timestamptz everywhere, enums as text, and no
-- constraint that would need a full-table rewrite on a table this one will grow
-- to be.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- The audit trail. Append-only, like `review`, but written by the persistence
-- layer rather than by a human decision: `review` records the handful of
-- judgements the committee makes, this records every row that changed.
-- ---------------------------------------------------------------------------

create table audit_log (
    -- bigserial, not uuid. This is the one table in the schema that will hold
    -- millions of rows, it is only ever appended to, and it is only ever read in
    -- time order. A random v4 uuid would scatter those appends across the index
    -- and double its width to buy nothing anybody here needs.
    id          bigserial   primary key,
    at          timestamptz not null default now(),
    action      text        not null,
    -- The table, not the Java class: this outlives any package rename.
    entity      text        not null,
    -- Text, because a composite or non-uuid key must still be recordable.
    entity_id   text        not null,
    -- Denormalised so a group admin's own trail is one index scan and so the
    -- row stays readable after the subject has been soft-deleted.
    batch_year  int,
    actor_id    uuid        references person (id),
    actor_kind  text        not null,
    -- Who they were at the time — a username or a masked number. Kept as text
    -- because the point of an audit row is to still make sense in 2035, after
    -- the person has been renamed, revoked, or removed.
    actor_label text        not null,
    -- Why, when the caller had a reason worth keeping ("merged duplicate").
    note        text,
    -- {"status": {"from": "SEEDED", "to": "VERIFIED"}} — changed fields only.
    changes     jsonb       not null default '{}'::jsonb,
    request_id  text,
    ip          text,
    method      text,
    path        text,
    constraint audit_actor_kind_chk check (actor_kind in ('ADMIN', 'MEMBER', 'ANONYMOUS', 'SYSTEM'))
);

-- Deliberately no CHECK on `action`. Every other enum in this schema has one,
-- and this is the exception: the set of things worth recording grows, and
-- revalidating a CHECK against a hundred million rows takes a lock on the table
-- the whole application is writing to. A constraint that makes the next
-- developer's cheapest option "stop recording it" is a constraint that costs
-- more than it protects. The application owns this vocabulary; see AuditAction.
comment on column audit_log.action is
    'INSERT | UPDATE | DELETE | RESTORE, plus the domain events the ORM cannot see (LOGIN, LOCKOUT, SCOPE_CHANGED, ...). Open-ended on purpose.';
comment on column audit_log.changes is
    'Changed fields only, as {field: {from, to}}. Secrets are never recorded — see Redactions.';

-- "What happened lately", the default read.
create index audit_recent_idx on audit_log (at desc, id desc);
-- "Everything that ever touched this registration."
create index audit_subject_idx on audit_log (entity, entity_id, at desc);
-- "Everything this coordinator did", the question an investigation starts from.
create index audit_actor_idx on audit_log (actor_id, at desc) where actor_id is not null;
-- A group admin reads their own batches only, so that filter gets its own index.
create index audit_batch_idx on audit_log (batch_year, at desc) where batch_year is not null;

-- ---------------------------------------------------------------------------
-- Tombstones.
--
-- Applied to the five tables that hold a record of something real and therefore
-- have something to lose. Not applied to:
--   * review     — already append-only; a reversal is a new row
--   * audit_log  — the trail cannot be editable by the thing it audits
--   * batch, event, ticket_type — reference data, changed by migration
--   * admin_credential — keyed on person_id, so a tombstone would permanently
--     block re-granting access to that person. Its `active` flag already is
--     the tombstone, and revoking now sets it (see AdminAccountService).
-- ---------------------------------------------------------------------------

alter table person       add column deleted_at timestamptz;
alter table registration add column deleted_at timestamptz;
alter table payment      add column deleted_at timestamptz;
alter table notice       add column deleted_at timestamptz;
alter table referral     add column deleted_at timestamptz;

comment on column person.deleted_at is
    'Set instead of DELETE. Hibernate @SoftDelete adds "deleted_at is null" to every read, so a tombstone is invisible to the application without any query remembering to exclude it.';

-- ---------------------------------------------------------------------------
-- Every uniqueness rule has to be rewritten, and this is the part that quietly
-- breaks a soft-delete migration if it is skipped.
--
-- A tombstoned row still occupies its unique index entry. Left alone, removing
-- a member would keep their mobile number reserved forever, a rejected-and-
-- withdrawn registration would block that person from ever registering again,
-- and the withdrawal would look like a bug in the claim flow rather than in
-- here. Each index is therefore partial on "deleted_at is null": live rows are
-- unique against each other, tombstones against nothing.
-- ---------------------------------------------------------------------------

drop index person_phone_uidx;
create unique index person_phone_uidx on person (phone)
    where phone is not null and deleted_at is null;

alter table registration drop constraint registration_event_id_person_id_key;
create unique index registration_event_person_uidx on registration (event_id, person_id)
    where deleted_at is null;

alter table registration drop constraint registration_qr_token_key;
create unique index registration_qr_token_uidx on registration (qr_token)
    where qr_token is not null and deleted_at is null;

drop index payment_reference_uidx;
create unique index payment_reference_uidx on payment (method, reference)
    where reference is not null and status <> 'REJECTED' and deleted_at is null;

-- ---------------------------------------------------------------------------
-- The read indexes get the same predicate, for a different reason: correctness
-- is already handled by Hibernate, but every query now carries an extra
-- "deleted_at is null" and a plain index would make Postgres recheck it against
-- the heap. Matching the predicate keeps the queue an index-only scan.
-- ---------------------------------------------------------------------------

drop index person_batch_status_idx;
create index person_batch_status_idx on person (batch_year, status) where deleted_at is null;

drop index registration_member_queue_idx;
create index registration_member_queue_idx
    on registration (status, submitted_at desc, id desc) where deleted_at is null;

drop index registration_member_queue_batch_idx;
create index registration_member_queue_batch_idx
    on registration (batch_year, status, submitted_at desc, id desc) where deleted_at is null;

drop index registration_payment_queue_idx;
create index registration_payment_queue_idx
    on registration (payment_status, submitted_at desc, id desc) where deleted_at is null;

drop index registration_payment_queue_batch_idx;
create index registration_payment_queue_batch_idx
    on registration (batch_year, payment_status, submitted_at desc, id desc) where deleted_at is null;

drop index payment_registration_idx;
create index payment_registration_idx
    on payment (registration_id, reported_at desc) where deleted_at is null;

-- The recycle bin reads the other way — "what is deleted" — and there is no
-- index in the schema that answers it, because until now nothing was.
create index person_deleted_idx       on person       (deleted_at desc) where deleted_at is not null;
create index registration_deleted_idx on registration (deleted_at desc) where deleted_at is not null;
create index payment_deleted_idx      on payment      (deleted_at desc) where deleted_at is not null;
create index notice_deleted_idx       on notice       (deleted_at desc) where deleted_at is not null;
create index referral_deleted_idx     on referral     (deleted_at desc) where deleted_at is not null;
