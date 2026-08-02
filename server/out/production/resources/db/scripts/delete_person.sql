-- ============================================================
-- Hard-delete a person and all related data.
--
-- Usage:
--   1. Replace the UUID on the line marked << CHANGE THIS >>
--   2. Run against the target database:
--        psql -U <user> -d <db> -f delete_person.sql
--
-- What this script handles:
--   - audit_log.actor_id          nullable FK  → set NULL
--   - referral.referrer_id        nullable FK  → set NULL
--   - referral.matched_person_id  nullable FK  → set NULL
--   - payment.paid_to_id          nullable FK  → set NULL
--   - review.decided_by           NOT NULL FK  → DELETE those review rows
--                                               (comment/uncomment as needed)
--   - payment (via registration)  no cascade   → DELETE
--   - payment (standalone)        no cascade   → DELETE
--   - registration                no cascade   → DELETE
--   - admin_batch_scope           cascades from admin_credential
--   - admin_credential            ON DELETE CASCADE from person
--   - person.merged_into_id       self-ref     → set NULL
--   - person                      final DELETE
-- ============================================================

DO $$
DECLARE
    target_id uuid := 'xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx'; -- << CHANGE THIS
BEGIN

    -- 1. Nullify audit_log references (actor_id is nullable)
    UPDATE audit_log SET actor_id = NULL WHERE actor_id = target_id;

    -- 2. Nullify referral self-references
    UPDATE referral SET matched_person_id = NULL WHERE matched_person_id = target_id;
    UPDATE referral SET referrer_id       = NULL WHERE referrer_id       = target_id;

    -- 3. Nullify payment.paid_to_id (nullable)
    UPDATE payment SET paid_to_id = NULL WHERE paid_to_id = target_id;

    -- 4. review.decided_by is NOT NULL — pick one option:
    --    Option A (default): delete review rows where this person was the decider
    DELETE FROM review WHERE decided_by = target_id;
    --    Option B: if you want to keep the reviews, alter the column first and nullify:
    --    UPDATE review SET decided_by = NULL WHERE decided_by = target_id;

    -- 5. Delete payments linked to this person's registrations
    DELETE FROM payment
    WHERE registration_id IN (
        SELECT id FROM registration WHERE person_id = target_id
    );

    -- 6. Delete standalone payments by this person
    DELETE FROM payment WHERE person_id = target_id;

    -- 7. Delete registrations (admin_batch_scope + admin_credential cascade automatically)
    DELETE FROM registration WHERE person_id = target_id;

    -- 8. Nullify person.merged_into_id self-reference
    UPDATE person SET merged_into_id = NULL WHERE merged_into_id = target_id;

    -- 9. Delete the person (cascades: admin_credential → admin_batch_scope)
    DELETE FROM person WHERE id = target_id;

    RAISE NOTICE 'Person % and all related data deleted.', target_id;
END;
$$;
