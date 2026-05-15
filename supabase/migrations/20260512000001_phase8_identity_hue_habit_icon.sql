-- Phase 8: identity gains `hue` (OKLCH 0..360); habit_template gains `icon_name`.
-- Backfill canonical hue + Material icon for 8 existing identities by stable id.
-- Backfill icon_name on 31 existing habit_templates by stable id.
-- New identities (09..13) + new templates are not inserted here — those land via
-- client reconcile + seed.sql update on next deploy.

alter table identities add column hue integer not null default 142;
alter table habit_templates add column icon_name text;

update identities set hue = 30,  icon = 'menu_book'        where id = '00000000-0000-0000-0000-000000000001';
update identities set hue = 225, icon = 'code'             where id = '00000000-0000-0000-0000-000000000002';
update identities set hue = 5,   icon = 'directions_run'   where id = '00000000-0000-0000-0000-000000000003';
update identities set hue = 285, icon = 'edit_note'        where id = '00000000-0000-0000-0000-000000000004';
update identities set hue = 190, icon = 'school'           where id = '00000000-0000-0000-0000-000000000005';
update identities set hue = 130, icon = 'eco'              where id = '00000000-0000-0000-0000-000000000006';
update identities set hue = 255, icon = 'self_improvement' where id = '00000000-0000-0000-0000-000000000007';
update identities set hue = 155, icon = 'favorite'         where id = '00000000-0000-0000-0000-000000000008';

update habit_templates set icon_name = 'menu_book'         where id = '10000000-0000-0000-0000-000000000001';
update habit_templates set icon_name = 'article'           where id = '10000000-0000-0000-0000-000000000002';
update habit_templates set icon_name = 'description'       where id = '10000000-0000-0000-0000-000000000003';
update habit_templates set icon_name = 'code'              where id = '10000000-0000-0000-0000-000000000004';
update habit_templates set icon_name = 'bug_report'        where id = '10000000-0000-0000-0000-000000000005';
update habit_templates set icon_name = 'lightbulb'         where id = '10000000-0000-0000-0000-000000000006';
update habit_templates set icon_name = 'rate_review'       where id = '10000000-0000-0000-0000-000000000007';
update habit_templates set icon_name = 'fitness_center'    where id = '10000000-0000-0000-0000-000000000008';
update habit_templates set icon_name = 'fitness_center'    where id = '10000000-0000-0000-0000-000000000009';
update habit_templates set icon_name = 'directions_run'    where id = '10000000-0000-0000-0000-000000000010';
update habit_templates set icon_name = 'directions_bike'   where id = '10000000-0000-0000-0000-000000000011';
update habit_templates set icon_name = 'accessibility_new' where id = '10000000-0000-0000-0000-000000000012';
update habit_templates set icon_name = 'timer'             where id = '10000000-0000-0000-0000-000000000013';
update habit_templates set icon_name = 'edit_note'         where id = '10000000-0000-0000-0000-000000000014';
update habit_templates set icon_name = 'rss_feed'          where id = '10000000-0000-0000-0000-000000000015';
update habit_templates set icon_name = 'create'            where id = '10000000-0000-0000-0000-000000000016';
update habit_templates set icon_name = 'description'       where id = '10000000-0000-0000-0000-000000000017';
update habit_templates set icon_name = 'smart_display'     where id = '10000000-0000-0000-0000-000000000018';
update habit_templates set icon_name = 'cast'              where id = '10000000-0000-0000-0000-000000000019';
update habit_templates set icon_name = 'language'          where id = '10000000-0000-0000-0000-000000000020';
update habit_templates set icon_name = 'style'             where id = '10000000-0000-0000-0000-000000000021';
update habit_templates set icon_name = 'cleaning_services' where id = '10000000-0000-0000-0000-000000000022';
update habit_templates set icon_name = 'inventory'         where id = '10000000-0000-0000-0000-000000000023';
update habit_templates set icon_name = 'delete_sweep'      where id = '10000000-0000-0000-0000-000000000024';
update habit_templates set icon_name = 'self_improvement'  where id = '10000000-0000-0000-0000-000000000025';
update habit_templates set icon_name = 'self_improvement'  where id = '10000000-0000-0000-0000-000000000026';
update habit_templates set icon_name = 'spa'               where id = '10000000-0000-0000-0000-000000000027';
update habit_templates set icon_name = 'water_drop'        where id = '10000000-0000-0000-0000-000000000028';
update habit_templates set icon_name = 'bedtime'           where id = '10000000-0000-0000-0000-000000000029';
update habit_templates set icon_name = 'kitchen'           where id = '10000000-0000-0000-0000-000000000030';
update habit_templates set icon_name = 'no_food'           where id = '10000000-0000-0000-0000-000000000031';
