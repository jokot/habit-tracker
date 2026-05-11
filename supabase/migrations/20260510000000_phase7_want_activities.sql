-- Phase 7: Want CRUD server-side schema alignment.
--
-- Three fixes:
-- 1. Reassign public seed rows (user_id IS NULL) to the user(s) that have
--    logs against them. Phase 3 made want_activities user-scoped with RLS
--    `user_id = auth.uid()`. The original public seed rows have user_id
--    NULL, so client-side reconcile (Phase 7 task 3) hits
--    `ON CONFLICT(id) DO UPDATE` → RLS USING fails against NULL →
--    42501 RLS violation → sync fails.
--
--    Strategy: claim each public seed row for whichever user already
--    has logs against it (single-user dev: exactly one user; multi-user:
--    first user wins, other users' historical logs become RLS-hidden but
--    data preserved). Unreferenced public rows get deleted.
--
--    Cannot DELETE all public rows directly: want_logs.activity_id FK
--    (NO ACTION) blocks deletes for any row referenced by logs.
--
-- 2. Loosen `cost_per_unit > 0` → `>= 0`. Phase 7 form validation accepts
--    zero-cost wants; the old CHECK rejects them at server insert time.
-- 3. Add icon_key + hidden_at columns for forward compatibility. Current
--    sync DTO does not carry them yet (separate follow-up to round-trip
--    hide state and custom icons).

-- (1a) Claim public-seed rows that have log references for the first
--      user that logged them.
update want_activities wa
   set user_id = sub.uid,
       created_by_user_id = coalesce(wa.created_by_user_id, sub.uid),
       updated_at = now()
  from (
    select distinct on (wl.activity_id) wl.activity_id, wl.user_id as uid
      from want_logs wl
      join want_activities wa on wa.id = wl.activity_id
     where wa.user_id is null
     order by wl.activity_id, wl.user_id
  ) sub
 where wa.id = sub.activity_id
   and wa.user_id is null;

-- (1b) Delete remaining unclaimed public-seed rows (no logs reference them).
delete from want_activities where user_id is null;

-- (2) Loosen cost_per_unit check.
alter table want_activities
    drop constraint if exists want_activities_cost_per_unit_check;
alter table want_activities
    add constraint want_activities_cost_per_unit_check
    check (cost_per_unit >= 0);

-- (3) Forward-compat columns.
alter table want_activities
    add column if not exists icon_key text;
alter table want_activities
    add column if not exists hidden_at timestamptz;
