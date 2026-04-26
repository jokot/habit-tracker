-- Phase 3 fix: align want_activities to flat user-scoped semantic so sync DTOs
-- (which mirror LocalWantActivity 1:1) can push/pull without conflict.

alter table want_activities
    add column if not exists user_id uuid references auth.users(id) on delete cascade;

-- Backfill: existing user-created rows used created_by_user_id; copy across.
update want_activities
   set user_id = created_by_user_id
 where user_id is null
   and created_by_user_id is not null;

-- Replace old policies (public read + custom-only write) with single user-scoped policy.
drop policy if exists "want_activities: public read" on want_activities;
drop policy if exists "want_activities: user inserts custom" on want_activities;
drop policy if exists "want_activities: user owns custom" on want_activities;

create policy "want_activities: user owns"
    on want_activities
    for all
    using (user_id = auth.uid())
    with check (user_id = auth.uid());
