-- Phase 3: add updated_at to mutable rows, auto-stamp via trigger
alter table if exists habits
    add column if not exists updated_at timestamptz not null default now();

alter table if exists want_activities
    add column if not exists updated_at timestamptz not null default now();

create or replace function touch_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

drop trigger if exists habits_touch_updated_at on habits;
create trigger habits_touch_updated_at
    before update on habits
    for each row execute function touch_updated_at();

drop trigger if exists want_activities_touch_updated_at on want_activities;
create trigger want_activities_touch_updated_at
    before update on want_activities
    for each row execute function touch_updated_at();
