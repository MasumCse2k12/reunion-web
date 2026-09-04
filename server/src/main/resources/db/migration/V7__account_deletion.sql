-- A member deleting their own account may have already paid for a ticket.
--
-- The money stays in the ledger. Payment's own comment is the reason: "a claim
-- about money that has disappeared from the database is the accusation the
-- committee cannot answer." What goes is the person it points at — that row is
-- tombstoned and scrubbed, so the surviving payment identifies nobody, and the
-- EAGER person association resolves to null once the join stops matching.
--
-- Which leaves the member no way to be contacted about their refund, because we
-- just deleted the number they would be contacted on. So the flow is the other
-- way round: the app hands them their coordinator's number on the way out, and
-- this flag is what the coordinator filters on when they phone up quoting the
-- bKash reference that is still sitting in payment.reference.
alter table payment add column if not exists refund_pending boolean not null default false;

-- Partial: the flag is false on all but a handful of rows, and only the live
-- ones are ever listed.
create index if not exists payment_refund_pending_idx on payment (refund_pending)
    where refund_pending and deleted_at is null;
