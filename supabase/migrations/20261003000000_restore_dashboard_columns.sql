-- Phase 5c-2 and Phase 5e added these columns in the dashboard SQL editor.
-- No migration file recorded them, so a database built from this folder had
-- no such columns, and every habit and identity upsert failed on it.
--
-- Production already has the columns. "if not exists" makes this file change
-- nothing there. The Phase 5e backfill is not repeated: a new database has no
-- rows to fill, and production already has its values.

-- Phase 5c-2: identity pinning, the "why" text and soft removal.
alter table public.user_identities add column if not exists is_pinned boolean not null default false;
alter table public.user_identities add column if not exists why_text text;
alter table public.user_identities add column if not exists removed_at timestamptz;

-- Phase 5e: the days a habit and a habit-identity link count toward a streak.
alter table public.habits add column if not exists effective_from timestamptz;
alter table public.habits add column if not exists effective_to timestamptz;
alter table public.habit_identities add column if not exists effective_from timestamptz;
alter table public.habit_identities add column if not exists effective_to timestamptz;
