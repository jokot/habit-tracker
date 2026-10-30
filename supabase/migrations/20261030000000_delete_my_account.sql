-- Account deletion (BACKLOG "Before the Play Store" item 4).
--
-- Play requires that a user can delete the account from the app. The app calls
-- `rpc('delete_my_account')` with the session of the user. The function deletes only
-- the user of that session: `auth.uid()`. The caller cannot give another id.
--
-- The delete of the `auth.users` row also deletes, with `on delete cascade`:
-- habits, habit_logs, want_logs, want_activities (by user_id), user_identities,
-- goals and user_want_activities. habit_identities follows habits.
--
-- habit_templates.created_by_user_id uses `on delete set null`, so a custom template
-- of the user stays without this function. The function deletes it. A catalog row
-- (is_custom = false) stays, because other users use it.

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    uid uuid := auth.uid();
    custom_templates uuid[];
begin
    if uid is null then
        raise exception 'Not signed in' using errcode = '42501';
    end if;

    -- Find the custom templates before the delete sets created_by_user_id to null.
    select coalesce(array_agg(id), '{}') into custom_templates
    from public.habit_templates
    where created_by_user_id = uid and is_custom;

    delete from auth.users where id = uid;

    -- The cascade deleted the habits of the user. Keep a template that a row of
    -- another user still uses.
    delete from public.habit_templates t
    where t.id = any(custom_templates)
      and not exists (select 1 from public.habits h where h.template_id = t.id)
      and not exists (select 1 from public.identity_habits ih where ih.habit_template_id = t.id);
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
