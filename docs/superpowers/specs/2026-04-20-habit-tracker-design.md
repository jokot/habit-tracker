# Habit Tracker — Design Spec

**Date:** 2026-04-20  
**Status:** Approved  
**Target user:** Beginners building habits for the first time  
**Success metric:** User keeps habit streak alive for 90 days, never misses twice

---

## 1. Product Thesis

Journal + enforcement system based on Atomic Habits principles:

1. **Identity-based** — user declares who they want to become; app surfaces evidence they're becoming that person
2. **Temptation bundling** — Need habits earn points; Want activities spend points. Do the need to unlock the want.
3. **Two Minutes Rule** — each habit has a bare-minimum threshold. Start small.
4. **Never miss twice** — streak system forgives one miss, breaks on two consecutive

---

## 2. Core Mechanics

### 2.1 Need Habits (earn points)

Each habit has:
- `unit`: pages | minutes | reps
- `threshold_per_point`: minimum quantity to earn 1 point (bare minimum)
- `daily_target`: how many threshold completions to aim for per day
- `identities[]`: one habit can serve multiple identities

**Earning rules:**
- Log quantity → `floor(quantity / threshold_per_point)` = points earned
- Completions ≤ `daily_target` → earns points
- Completions > `daily_target` → logged, no points (data feeds exchange rate algorithm)
- Partial progress = lost (no carryover). UI shows progress bar toward next threshold.

**Habit display:** flat list with identity tags. Grouped by identity in onboarding only.

**Default need habits by identity:**

```
READER
  Read book/kindle   → 3 pages    = 1 pt  | daily_target: 3
  Read article       → 5 min      = 1 pt  | daily_target: 2
  Read research      → 10 min     = 1 pt  | daily_target: 1

BUILDER (Developer)
  Code project       → 15 min     = 1 pt  | daily_target: 3
  Write tests        → 10 min     = 1 pt  | daily_target: 2
  Learn new tech     → 15 min     = 1 pt  | daily_target: 2
  Review/refactor    → 10 min     = 1 pt  | daily_target: 1

ATHLETE
  Push up            → 15 reps    = 1 pt  | daily_target: 3
  Squat              → 20 reps    = 1 pt  | daily_target: 3
  Walk/run           → 10 min     = 1 pt  | daily_target: 2
  Cycling            → 10 min     = 1 pt  | daily_target: 2
  Stretching         → 5 min      = 1 pt  | daily_target: 2
  Plank              → 30 sec     = 1 pt  | daily_target: 3

WRITER
  Journaling         → 5 min      = 1 pt  | daily_target: 2
  Blog writing       → 15 min     = 1 pt  | daily_target: 2
  Creative writing   → 10 min     = 1 pt  | daily_target: 2
  Outline/draft      → 10 min     = 1 pt  | daily_target: 1

LEARNER
  Watch edu video    → 10 min     = 1 pt  | daily_target: 2
  Take course        → 15 min     = 1 pt  | daily_target: 2
  Practice language  → 10 min     = 1 pt  | daily_target: 2
  Flashcard review   → 5 min      = 1 pt  | daily_target: 3

MINIMALIST
  Declutter space    → 5 min      = 1 pt  | daily_target: 1
  Organize items     → 5 min      = 1 pt  | daily_target: 1
  Digital cleanup    → 5 min      = 1 pt  | daily_target: 1

DEVOTEE
  Pray               → 1 session  = 1 pt  | daily_target: 3
  Meditate           → 5 min      = 1 pt  | daily_target: 2
  Gratitude journal  → 3 entries  = 1 pt  | daily_target: 1

HEALTH-CONSCIOUS
  Drink water        → 250ml      = 1 pt  | daily_target: 8
  Sleep on time      → 1 night    = 1 pt  | daily_target: 1
  Meal prep          → 10 min     = 1 pt  | daily_target: 1
  No junk food       → 1 day      = 1 pt  | daily_target: 1
```

### 2.2 Want Activities (spend points)

User logs time spent AFTER the activity (v1 = manual post-log, C mode).

**Spending:** user inputs actual duration/quantity → `floor(quantity / cost_per_unit)` = points spent.  
Example: watch 30 min YouTube → `floor(30 / 10)` = 3 points spent.

**Spending modes:**

| Mode | Flow |
|---|---|
| This device | Log want → deduct points → start timer → alarm notification when done (Phase 2) |
| Other device / physical | Log want → deduct points → done |

Timer enforcement (Android overlay, Phase 5+): shows overlay notification when timer ends to prompt user to close the want app. Requires `SYSTEM_ALERT_WINDOW` permission — user must grant manually. iOS: timer + notification only (no overlay).

**Default spend rates (user-customizable):**
```
SOCIAL MEDIA
  Scroll (reel/TikTok/short)    → 1 pt / 1 min
  Browse Twitter/X              → 1 pt / 2 min
  Browse Instagram feed         → 1 pt / 2 min

VIDEO
  YouTube long-form             → 1 pt / 10 min
  YouTube shorts                → 1 pt / 1 min
  Netflix / streaming           → 1 pt / 15 min

GAMING
  Casual mobile game            → 1 pt / 5 min
  Valorant Deathmatch           → 1 pt / match
  Valorant Ranked               → 3 pts / match
  Long gaming session (PC)      → 1 pt / 10 min

SHOPPING
  Online browse (Shopee/Tokped) → 1 pt / 5 min
  Actual purchase session       → 2 pts / session

FOOD INDULGENCE
  Junk food / fast food         → 2 pts / meal
  Sugary drinks                 → 1 pt / drink
  Eat donut / dessert           → 1 pt / piece
```

### 2.3 Point Balance

- **Rollover cap:** unspent points carry to next day, max = `daily_earn_cap × 2`
- **Rollover resets:** every Monday 00:00
- **Point balance** = always computed from log history, never stored as a field
- **Negative balance allowed:** if user spends more than they earn, `balance = earned - spent` can go below zero. UI shows debt state (error color + "Point Balance (debt)" label). v1 does not block want-logging when balance ≤ 0 — relies on user self-awareness. Hard lock can be added later.
- **Per-habit daily earn cap:** a single habit cannot contribute more than its `daily_target` to daily/weekly earned totals. Logging past the target is still recorded (for exchange-rate algorithm data) but contributes 0 points.

### 2.4 Progress Bar

Per habit, shows: `completions_today / daily_target`  
Example: `[██░] 2/3 — 1 more for goal`  
Hitting daily_target = goal complete. Further completions still logged (no reward).

---

## 3. Streak System

### 3.1 Overall Streak (v1)

**Rule:** Log ALL habits at least once per day (no need to hit daily_target).

**Day states:**
| State | Color | Condition |
|---|---|---|
| Complete | Green | All habits logged |
| Frozen | Blue | Missed 1+ habit — streak survives if isolated |
| Broken | Red | 2nd consecutive miss — streak reset marker |
| No activity | Gray | No logs (past abandoned day, or today not yet logged) |

Today's square = gray with outline ring ("still in play"). Past gray = flat fill.

**Visual examples:**
```
Full streak:    🟩🟩🟩🟩🟩🟩🟩
With freezes:   🟩🟦🟩🟩🟦🟩🟩  ← non-consecutive, streak survives
Streak broken:  🟩🟦🟥⬜⬜🟩🟩  ← red = break point, new streak starts day 6
```

**Never miss twice rule:** 1 blue day = warning, streak survives. 2 consecutive blue days = red on 2nd day, streak resets to 0.

**Streak number** = consecutive days since last red (or since start if no red).

**30-day view:** grid of colored rounded squares, streak count above.  
Tap widget → opens app to streak/history screen.

### 3.2 Per-Habit Streak (Phase 2+)

Each habit gets its own streak widget. Same 3-state system. Added after overall streak is validated.

---

## 4. Identity & Habit Recommendations

### 4.1 Onboarding Flow

```
Screen 1 (required, no skip): "Who do you want to become?"
  → Pick exactly 1 identity from hardcoded list
  → User can add more identities from home page after onboarding

Screen 2 (skippable): Recommended Need habits for selected identity
  → Pick 2–3 habits max (lower friction = higher completion)
  → Each shows threshold + Two Minutes Rule framing:
    "Start with just 3 pages. That's it."
  → Set daily_target per habit (suggest defaults, user can adjust)
  → Skip → lands in app with prompt to add habits

Screen 3 (skippable): "What do you do for fun?"
  → Pick Want activities from default list
  → Shows spend rate per activity
  → User can add custom want activities
  → Skip → no want activities configured yet

Then: guided first log → earn first point → celebrate (retention hook)
```

### 4.2 Hardcoded Seed Data

Identities, recommended habits, and default want activities seeded in `supabase/seed.sql`.  
Users can add custom habits and custom want activities beyond defaults.

---

## 5. Log Integrity

**Append-only with 5-minute undo window.**

- Logs are permanent after 5 minutes of creation
- Within 5 minutes: user can undo (soft delete, marked `deleted_at`)
- Applies to both habit logs and want logs
- Deleted logs excluded from all point/streak computations
- Undo window prevents gaming (earn → spend → delete spend log → points restored) while covering genuine mistakes

**No hard deletes.** `deleted_at` timestamp propagates to Supabase as tombstone during sync.

---

## 6. Exchange Rate Algorithm (Phase 6)

Adjusts `threshold_per_point` based on rolling completion rate.

```
window = last 7 days per habit
completion_rate = days_with_any_completion / 7

If completion_rate >= 0.80 for 2 consecutive weeks:
  threshold_per_point *= 1.20  (round up to nearest whole unit)
  notify: "You're crushing [habit]. Raising the bar."

If completion_rate < 0.50 for 1 week:
  threshold_per_point *= 0.90  (floor = original threshold at habit creation)
  notify: "Rough week. Bar drops slightly."
```

**Note:** Parameters (80%, 20%, etc.) are unvalidated hypotheses. Tune post-launch with real data.

---

## 7. Notifications

**Phase 3 (with streak):**
- Daily reminder — user sets preferred time; fires only if nothing logged yet
- Streak at risk — 9pm fallback if still no log that day
- Streak frozen — "Missed yesterday. Don't miss today."
- Streak reset — "Streak reset. Start fresh today."
- Want timer end — alarm-style notification when on-device timer expires (Phase 2 timer feature)

**Phase 6 (with exchange rate):**
- Friction increase — "Raising the bar on [habit]."
- Milestones — 7-day, 30-day, 100-day streak

**Implementation:**
- KMP shared module: timing logic, notification content, trigger conditions
- Android: WorkManager dispatch
- iOS: UNUserNotificationCenter dispatch

---

## 8. Architecture

### 8.1 Platform

| Layer | Technology |
|---|---|
| Android app | Kotlin + Compose |
| iOS app | Swift + SwiftUI |
| Shared logic | KMP shared module (Kotlin) |
| Android widget | Compose Glance |
| iOS widget | SwiftUI WidgetKit (iOS 17+) |
| Local DB | SQLDelight (offline cache) |
| Remote DB | Supabase (Postgres + Auth + RLS) |
| Network client | Ktor (in shared module) |

### 8.2 Project Structure (Monorepo)

```
habit-tracker/
├── mobile/
│   ├── shared/
│   │   ├── data/
│   │   │   ├── remote/        # Ktor Supabase client
│   │   │   ├── local/         # SQLDelight
│   │   │   └── repository/
│   │   ├── domain/
│   │   │   ├── model/
│   │   │   └── usecase/
│   │   └── presentation/      # Shared ViewModels
│   ├── androidApp/
│   │   ├── ui/                # Compose screens
│   │   └── widget/            # Compose Glance
│   └── iosApp/
│       ├── ui/                # SwiftUI screens
│       └── widget/            # WidgetKit extension
├── supabase/
│   ├── migrations/
│   ├── seed.sql               # Identities + default habits + default want activities
│   └── functions/             # Reserved, empty v1
└── docs/
    └── superpowers/specs/
```

### 8.3 Offline-First Sync

**Principle:** Local DB is source of truth. Supabase is backup + cross-device sync. **Auth is optional** — the app is fully usable without sign-in.

**Guest mode (no auth):**
- On first launch, app generates a random UUID (`localUserId`) and persists it in platform settings (Android DataStore / iOS UserDefaults).
- All local rows (`habits`, `habit_logs`, `want_logs`, `want_activities` with `isCustom`) store `user_id = localUserId`.
- Everything works offline: onboarding, log need/want, point balance, undo, streak.
- Supabase client is created but not called; no network required.

**Authenticated mode (opt-in, for sync across devices):**
- User reaches `AuthScreen` from Home → "Sign in to sync" CTA (not forced on launch).
- After successful sign-in/sign-up, `currentUserId` switches from `localUserId` to Supabase `auth.uid()`.
- Phase 4 sync: migrate local rows where `user_id = localUserId` → remote `auth.uid()` on first sign-in, then push unsynced rows. Local DB remains source of truth.
- Sign-out: revert to `localUserId` (local data untouched); Phase 4 handles detach/reattach semantics.

**`currentUserId()` contract:** returns the remote user id if a Supabase session is active, otherwise the persisted `localUserId`. Never null once the app has launched (local id is generated before first read).

**Startup navigation:**
- If local habits exist for `currentUserId` → `HomeScreen`.
- Else → `OnboardingScreen`.
- `AuthScreen` is never a mandatory gate.

**Auth state transitions:**

*Guest → Authenticated (sign-in / sign-up):*
1. User reaches `AuthScreen` from Home's "Sign in to sync" CTA.
2. On success, Supabase session is stored. `currentUserId()` now returns `auth.uid()`.
3. One-time migration: `UPDATE habits/habit_logs/want_logs/want_activities SET user_id = auth.uid() WHERE user_id = localUserId`. Runs inside a single SQL transaction.
4. `synced_at` stays null on migrated rows — Phase 4 sync will push them.
5. On sign-in from a second device (cloud data already exists): pull remote rows, merge by `id`; local guest data is still migrated and pushed.
6. `localUserId` is preserved in settings so sign-out can revert to the same guest identity.

*Authenticated → Guest (sign-out):*
1. Confirmation dialog: "Sign out? Local data on this device will be cleared. Cloud data stays."
2. Before clearing, push any `synced_at = null` rows. If push fails (offline, network error): dialog becomes "N unsynced logs will be lost. Sign out anyway?" — user can retry or force.
3. On confirm: clear all local rows where `user_id = auth.uid()` (habits, habit_logs, want_logs, custom want_activities, local identities).
4. Clear Supabase session. `currentUserId()` reverts to the persisted `localUserId`.
5. App returns to Onboarding (local DB is empty under `localUserId` again).

*Re-sign-in after sign-out:* Phase 4 pulls from Supabase into local DB under `auth.uid()`. Instant recovery of cloud data.

**Log record structure:**
```
HabitLog {
  id: UUID (client-generated)
  habit_id: UUID
  quantity: Float
  logged_at: Timestamp (client clock)
  deleted_at: Timestamp? (null = active, set = soft-deleted within undo window)
  synced_at: Timestamp? (null until pushed to Supabase)
}
```

**Computed state (never stored):**
- Point balance = `SUM(active logs)` computed at read time
- Streak = computed from active log history
- Completion rate = computed from active log history

**Sync flow:**
1. On app open: push all `synced_at = null` logs (including tombstones) to Supabase
2. Pull all logs since `last_pull_timestamp`
3. Recompute balance + streak from full local active log history

### 8.4 Supabase Schema (Core Tables)

```sql
-- Identities (seeded, not user-created in v1)
identities (id, name, description, icon)

-- Goals = user's selected identities
goals (id, user_id, identity_id, created_at)

-- Habit templates (seeded recommendations + custom)
habit_templates (
  id, name, unit, default_threshold, default_daily_target,
  is_custom, created_by_user_id  -- null = seeded
)

-- Identity ↔ habit template mapping (many-to-many)
identity_habits (identity_id, habit_template_id)

-- User's active habits
habits (
  id, user_id, template_id,
  threshold_per_point, daily_target,
  created_at
)

-- Need habit logs
habit_logs (
  id UUID PRIMARY KEY,          -- client-generated
  user_id, habit_id,
  quantity FLOAT,
  logged_at TIMESTAMPTZ,        -- client clock
  deleted_at TIMESTAMPTZ,       -- null = active
  synced_at TIMESTAMPTZ
)

-- Want activity definitions (seeded + custom)
want_activities (
  id, name, unit, cost_per_unit,
  is_custom, created_by_user_id  -- null = seeded
)

-- Want activity logs
want_logs (
  id UUID PRIMARY KEY,
  user_id, activity_id,
  quantity FLOAT,               -- minutes or match count
  device_mode TEXT,             -- 'this_device' | 'other'
  logged_at TIMESTAMPTZ,
  deleted_at TIMESTAMPTZ,
  synced_at TIMESTAMPTZ
)
```

All tables: RLS `user_id = auth.uid()` (Supabase side). Local SQLite holds `user_id` as a plain TEXT — it can be either `auth.uid()` (authenticated) or a client-generated `localUserId` (guest). The sync layer (Phase 4) maps `localUserId → auth.uid()` on first sign-in.

---

## 9. UI Design System

**Platform:** Material 3 (Android), equivalent design tokens (iOS)  
**Modes:** Dark + Light from day 1  
**Standards:**
- WCAG AA minimum (contrast ratio ≥ 4.5:1 text, ≥ 3:1 UI components)
- No gradients
- Spacing scale: multiples of 2dp/pt only (2, 4, 8, 12, 16, 24, 32, 48, 64)
- No arbitrary values

**Streak color tokens:**
```
streak_complete   → success color role (green, WCAG-accessible)
streak_frozen     → primary/info color role (blue, WCAG-accessible)
streak_broken     → error color role (red, WCAG-accessible)
streak_empty      → surface variant (gray, no activity)
streak_today      → surface variant + outline border (today, not yet logged)
```

---

## 10. Widgets

**Two separate widgets, both iOS 17+:**

**Widget 1: Point Balance + Quick Log**
- Shows: current point balance, daily earn progress
- Shows: today's incomplete habits as tap targets
- Tap habit → opens app to log screen for that habit (no inline form in widget)
- All habits complete → shows "All done" state

**Widget 2: 30-Day Streak Grid**
- Shows: 30 colored rounded squares (green / blue / gray) + streak number
- Tap → opens app to streak/history screen

---

## 11. Value Chain

```
Identity Goal declared
    ↓ (onboarding)
Recommended Habits selected
    ↓ (daily loop)
Log Need → Earn Points → Log Want → Spend Points
    ↓ (visibility)
Streak + Progress (proof of identity)
    ↓ (Phase 6)
Exchange Rate Increase (bar rises because you rose)
    ↓ (long-term)
Identity Reinforced by data
```

**Moat:** log history. After 90 days, users don't switch apps.  
**Core risk:** honor system. Enforcement is behavioral, not technical in v1.

---

## 12. Development Phases

| Phase | Scope | Timeline |
|---|---|---|
| 1 | Supabase schema + RLS + auth, KMP skeleton, SQLDelight setup, Ktor client, UI theme (Material 3 + tokens + dark/light), offline-first log model with soft delete | Week 1–2 |
| 2 | Core habit loop (Android): onboarding, identity→habit setup, log need→earn, log want→spend (both modes), point balance, 5-min undo | Week 3–5 |
| 3 | Streak 30-day view (3 states, rounded squares), progress bar per habit, basic notifications | Week 6–7 |
| 4 | Android widgets: point balance + quick log, 30-day streak grid | Week 8 |
| 4+ | On-device timer for want activities (Android), alarm notification on timer end | Week 8–9 |
| 5 | iOS: SwiftUI screens + WidgetKit extensions (same shared KMP logic) | Week 9–11 |
| 5+ | Android overlay enforcement (SYSTEM_ALERT_WINDOW) — backlog, post-iOS validation | TBD |
| 6 | Exchange rate algorithm, friction increase + milestone notifications | Week 12 |

**Guardrails:**
- No iOS work until Phase 4 complete
- No exchange rate until Phase 6 — needs real usage data
- Widgets after core app stable, not before
- Android overlay = Phase 5+ backlog, not core scope
- Per-habit streak widget = Phase 2+ backlog
