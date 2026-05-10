-- Phase 7 pivot: units-per-point want point model.
--
-- want_activities: drop cost_per_unit, add units_per_point.
--   Repair user-claimed seed rows by name → new Int. Custom rows default to 1.
-- want_logs: wipe (solo dev — no production logs) and stamp points_spent on each row.

alter table want_activities add column units_per_point integer not null default 1;

update want_activities set units_per_point = case lower(name)
    when 'tiktok'          then 1
    when 'youtube shorts'  then 1
    when 'youtube'         then 10
    when 'netflix'         then 15
    when 'twitter/x'       then 2
    when 'instagram'       then 2
    when 'reddit'          then 2
    when 'gaming'          then 10
    when 'online shopping' then 5
    when 'junk food'       then 1
    when 'snacks'          then 1
    when 'sweets'          then 1
    when 'sugary drinks'   then 1
    when 'coffee'          then 1
    else 1
end where is_custom = false;

alter table want_activities drop column cost_per_unit;

truncate table want_logs;
alter table want_logs add column points_spent integer not null default 1;
