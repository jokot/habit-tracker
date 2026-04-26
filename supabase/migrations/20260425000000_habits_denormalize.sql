-- Phase 3 fix: denormalize habits remote schema to match local (name + unit stored
-- alongside template_id). Sync DTOs assume flat 1:1 with LocalHabit.

alter table habits add column if not exists name text;
alter table habits add column if not exists unit text;

-- Backfill existing rows from the habit_templates join.
update habits
   set name = ht.name,
       unit = ht.unit
  from habit_templates ht
 where habits.template_id = ht.id
   and (habits.name is null or habits.unit is null);

alter table habits alter column name set not null;
alter table habits alter column unit set not null;
