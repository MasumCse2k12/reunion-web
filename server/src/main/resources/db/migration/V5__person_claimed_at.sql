-- When a person proved they hold the mobile number on their row.
--
-- The identity queue is read newest-first and paged by keyset, which needs a sort
-- key that does not move under the reader. updated_at moves on every profile
-- edit, so a coordinator scrolling page four would silently skip whoever edited
-- their own city meanwhile. created_at is no better: for a seeded row it is the
-- evening a volunteer typed the name off the 1974 register, decades before
-- anybody claimed it. So the moment is recorded once, when it happens.
alter table person add column if not exists claimed_at timestamptz;

comment on column person.claimed_at is
    'Set once, when a person first verifies a mobile number against their row. Immutable afterwards: it is the sort key of the identity review queue.';

-- Existing claims predate the column. updated_at is the closest thing we have to
-- when the status last moved, and it is right for the rows that matter — a claim
-- is usually the last thing to have happened to a row that is still CLAIMED.
--
-- Every row that has left SEEDED gets a value, not only the ones with a phone:
-- the queue sorts on this column, and a null would make the row unreachable by
-- the very screen that exists to find it.
update person
   set claimed_at = coalesce(updated_at, created_at)
 where claimed_at is null
   and status <> 'SEEDED';

-- The queue's read: the claims a coordinator has to judge, newest first, within
-- the batches they are assigned. Partial on deleted_at for the same reason as
-- every other index added in V3 — a tombstone is never read.
create index if not exists person_claim_queue_idx
    on person (status, claimed_at desc, id desc)
    where deleted_at is null;
