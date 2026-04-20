-- identities (seeded, public read)
create table identities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  description text not null,
  icon text not null,
  created_at timestamptz default now()
);

-- user's selected identities
create table goals (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  identity_id uuid not null references identities(id),
  created_at timestamptz default now(),
  unique(user_id, identity_id)
);

-- habit templates (seeded + user custom)
create table habit_templates (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  unit text not null,
  default_threshold float not null,
  default_daily_target int not null,
  is_custom boolean not null default false,
  created_by_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz default now()
);

-- identity <-> habit template (many-to-many)
create table identity_habits (
  identity_id uuid not null references identities(id),
  habit_template_id uuid not null references habit_templates(id),
  primary key (identity_id, habit_template_id)
);

-- user's active habits
create table habits (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  template_id uuid not null references habit_templates(id),
  threshold_per_point float not null,
  daily_target int not null,
  created_at timestamptz default now()
);

-- need habit logs (append-only, soft delete within 5-min window)
create table habit_logs (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  habit_id uuid not null references habits(id) on delete cascade,
  quantity float not null check (quantity > 0),
  logged_at timestamptz not null,
  deleted_at timestamptz,
  synced_at timestamptz default now()
);

-- want activity definitions (seeded + user custom)
create table want_activities (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  unit text not null,
  cost_per_unit float not null check (cost_per_unit > 0),
  is_custom boolean not null default false,
  created_by_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz default now()
);

-- user's selected want activities with customizable rate
create table user_want_activities (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid not null references want_activities(id),
  cost_per_unit float not null check (cost_per_unit > 0),
  created_at timestamptz default now(),
  unique(user_id, activity_id)
);

-- want activity logs (append-only, soft delete within 5-min window)
create table want_logs (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  activity_id uuid not null references want_activities(id),
  quantity float not null check (quantity > 0),
  device_mode text not null check (device_mode in ('this_device', 'other')),
  logged_at timestamptz not null,
  deleted_at timestamptz,
  synced_at timestamptz default now()
);

-- Indexes for common queries
create index habit_logs_user_logged on habit_logs(user_id, logged_at) where deleted_at is null;
create index want_logs_user_logged on want_logs(user_id, logged_at) where deleted_at is null;
create index habits_user on habits(user_id);

-- RLS: user-owned tables
alter table goals enable row level security;
alter table habits enable row level security;
alter table habit_logs enable row level security;
alter table user_want_activities enable row level security;
alter table want_logs enable row level security;

create policy "goals: user owns" on goals for all using (user_id = auth.uid());
create policy "habits: user owns" on habits for all using (user_id = auth.uid());
create policy "habit_logs: user owns" on habit_logs for all using (user_id = auth.uid());
create policy "user_want_activities: user owns" on user_want_activities for all using (user_id = auth.uid());
create policy "want_logs: user owns" on want_logs for all using (user_id = auth.uid());

-- RLS: seeded tables (public read)
alter table identities enable row level security;
alter table habit_templates enable row level security;
alter table identity_habits enable row level security;
alter table want_activities enable row level security;

create policy "identities: public read" on identities for select using (true);
create policy "habit_templates: public read" on habit_templates for select using (true);
create policy "identity_habits: public read" on identity_habits for select using (true);
create policy "want_activities: public read" on want_activities for select using (true);

-- RLS: custom habit templates + want activities
create policy "habit_templates: user inserts custom" on habit_templates
  for insert with check (is_custom = true and created_by_user_id = auth.uid());
create policy "habit_templates: user owns custom" on habit_templates
  for all using (is_custom = true and created_by_user_id = auth.uid());

create policy "want_activities: user inserts custom" on want_activities
  for insert with check (is_custom = true and created_by_user_id = auth.uid());
create policy "want_activities: user owns custom" on want_activities
  for all using (is_custom = true and created_by_user_id = auth.uid());
