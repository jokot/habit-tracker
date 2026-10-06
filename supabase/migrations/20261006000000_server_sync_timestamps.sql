-- The pull watermarks come from the server clock only (#35).
--
-- The app sent the time of the phone in synced_at, and in updated_at on an insert.
-- A phone with a wrong clock then wrote a time before or after the real time, and
-- other devices with a watermark past that time never pulled the row.
--
-- These triggers replace the value that the app sends. An old version of the app
-- also gets the server time.

create or replace function touch_synced_at()
returns trigger as $$
begin
    new.synced_at = now();
    return new;
end;
$$ language plpgsql;

drop trigger if exists habit_logs_touch_synced_at on habit_logs;
create trigger habit_logs_touch_synced_at
    before insert or update on habit_logs
    for each row execute function touch_synced_at();

drop trigger if exists want_logs_touch_synced_at on want_logs;
create trigger want_logs_touch_synced_at
    before insert or update on want_logs
    for each row execute function touch_synced_at();

-- The updated_at triggers fired on an update only. An insert kept the phone time.
drop trigger if exists habits_touch_updated_at on habits;
create trigger habits_touch_updated_at
    before insert or update on habits
    for each row execute function touch_updated_at();

drop trigger if exists want_activities_touch_updated_at on want_activities;
create trigger want_activities_touch_updated_at
    before insert or update on want_activities
    for each row execute function touch_updated_at();

drop trigger if exists habit_identities_touch_updated_at on public.habit_identities;
create trigger habit_identities_touch_updated_at
    before insert or update on public.habit_identities
    for each row execute function touch_updated_at();
