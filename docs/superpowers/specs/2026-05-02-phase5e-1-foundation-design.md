# Phase 5e-1 — Habit CRUD Foundation

> **Branch:** `feature/phase5e-habit-crud`. **Worktree:** `.worktrees/phase5e-habit-crud`.

## Goal

Add `effective_from` + `effective_to` columns to `habits` and `habit_identities` (Postgres + SQLite). Refactor the streak engine to filter habits per-day by their effective window. Backfill existing rows so past streak data stays stable. **No new UI.**

This is the foundation for the larger Habit CRUD work. Sub-phases 5e-2 (read-only browse) and 5e-3 (CRUD UI) build on top.

## Non-goals

- Habit list / detail / form screens — deferred to 5e-2 + 5e-3.
- Delete UI — `markHabitDeleted` query lands now (consumed in 5e-3).
- Custom-habit form integration into AddIdentityFlow Step 2 — deferred to 5e-3.
- Per-log target snapshot (alternative design for past-data stability) — rejected; this phase uses `effective_from / effective_to` instead.
- Drag-reorder of habits — undesigned, separate phase.

## Architecture

Schema migration adds two nullable timestamp columns (`effective_from`, `effective_to`) to both `habits` and `habit_identities` tables on local SQLite and Supabase. `LocalHabitRepository` and `LocalIdentityRepository` round-trip the new fields via existing repository methods. `HabitDto` and `HabitIdentityDto` carry them with `@EncodeDefault` (per the Phase 5c-2 lesson — kotlinx-serialization drops default-valued fields without it). The streak engine (`ComputeStreakUseCase` and `ComputeIdentityStatsUseCase`) gains a per-day filter that excludes habits whose effective window doesn't overlap the start of the day. Backfill runs once during the migration, setting `effective_from` to the earliest log on the habit (or `createdAt` if no logs exist), and `habit_identities.effective_from` to the link's existing `addedAt`.

## Schema migration

### Local — `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/3.sqm`

```sql
-- Migrate HabitTrackerDatabase from version 2 → 3
-- Adds effectiveFrom + effectiveTo to LocalHabit and LocalHabitIdentity for
-- past-streak-stability when habits or identity links are added/removed.

ALTER TABLE LocalHabit ADD COLUMN effectiveFrom INTEGER;
ALTER TABLE LocalHabit ADD COLUMN effectiveTo INTEGER;
ALTER TABLE LocalHabitIdentity ADD COLUMN effectiveFrom INTEGER;
ALTER TABLE LocalHabitIdentity ADD COLUMN effectiveTo INTEGER;

-- Backfill habits: earliest log date OR createdAt fallback.
-- COALESCE picks the non-null subquery result; falls back to createdAt when no logs.
UPDATE LocalHabit SET effectiveFrom = COALESCE(
    (SELECT MIN(loggedAt) FROM HabitLog WHERE HabitLog.habitId = LocalHabit.id AND HabitLog.deletedAt IS NULL),
    createdAt
) WHERE effectiveFrom IS NULL;

-- Backfill habit_identities: link's existing addedAt.
UPDATE LocalHabitIdentity SET effectiveFrom = addedAt WHERE effectiveFrom IS NULL;
```

`HabitTrackerDatabase.sq` `LocalHabit` and `LocalHabitIdentity` definitions gain the two columns each (for fresh installs). Existing queries (`upsertHabit`, `mergePulledHabit`, `upsertHabitIdentity`, `mergePulledHabitIdentity`) extend their column lists + bind parameters to include the two new fields.

New query (used by future Habit CRUD delete UI in 5e-3):

```sql
markHabitDeleted:
UPDATE LocalHabit SET effectiveTo = ?, syncedAt = NULL WHERE id = ? AND userId = ?;
```

SQLDelight database version: bump from 2 → 3.

### Supabase

```sql
ALTER TABLE public.habits ADD COLUMN effective_from TIMESTAMPTZ;
ALTER TABLE public.habits ADD COLUMN effective_to TIMESTAMPTZ;
ALTER TABLE public.habit_identities ADD COLUMN effective_from TIMESTAMPTZ;
ALTER TABLE public.habit_identities ADD COLUMN effective_to TIMESTAMPTZ;

UPDATE public.habits SET effective_from = COALESCE(
    (SELECT MIN(logged_at) FROM public.habit_logs
     WHERE habit_id = habits.id AND deleted_at IS NULL),
    created_at
) WHERE effective_from IS NULL;

UPDATE public.habit_identities SET effective_from = added_at WHERE effective_from IS NULL;
```

Apply via dashboard SQL Editor before deploying the app build (otherwise pushes from updated app would fail to populate the new columns).

### Sync mapper updates

`HabitDto` and `HabitIdentityDto` in `PostgrestSupabaseSyncClient.kt`:

```kotlin
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class HabitDto(
    // ... existing fields ...
    @kotlinx.serialization.EncodeDefault @SerialName("effective_from") val effectiveFrom: String? = null,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_to") val effectiveTo: String? = null,
)
```

Same shape for `HabitIdentityDto`. Both `toDto()` and `toDomain()` round-trip the new fields. `@EncodeDefault` is required so unset values still serialize (avoids the 5c-2 bug where omitted fields preserved stale server values on upsert).

## Domain model + repository updates

### `Habit` data class

```kotlin
data class Habit(
    val id: String,
    val userId: String,
    val templateId: String,
    val name: String,
    val unit: String,
    val thresholdPerPoint: Double,
    val dailyTarget: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncedAt: Instant?,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
)
```

### `HabitIdentityRow` data class

```kotlin
data class HabitIdentityRow(
    val habitId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
    val effectiveFrom: Instant? = null,
    val effectiveTo: Instant? = null,
)
```

### LocalHabitRepository + LocalIdentityRepository

Round-trip the new fields in:
- `LocalHabitRepository.upsertHabit(habit)` / `mergePulledHabit(habit)` / `getHabitsForUser(userId)` mapping
- `LocalIdentityRepository.linkHabitToIdentities(...)` (sets `effectiveFrom = clock.now()` on insert)
- `LocalIdentityRepository.mergePulledHabitIdentity(row)` / `getUnsyncedHabitIdentitiesFor(userId)`

Add new repository method (for 5e-3 consumption):

```kotlin
interface HabitRepository {
    // ... existing methods ...
    suspend fun markHabitDeleted(habitId: String, userId: String, effectiveTo: Instant)
}
```

`LocalHabitRepository.markHabitDeleted(...)` calls `q.markHabitDeleted(...)`. `FakeHabitRepository` implements as a list mutation.

## Habit creation paths — set `effectiveFrom = now`

Update these use cases so newly created habits and habit_identity links have `effectiveFrom = clock.now()`:

1. **`AddIdentityWithHabitsUseCase`** (5c-2):
   - When constructing a new `Habit`, set `effectiveFrom = now`.
   - When inserting habit_identity link via `linkHabitToIdentities`, the repository method sets it.

2. **`SetupUserHabitsUseCase`** (onboarding):
   - Habits inserted during onboarding step 2 get `effectiveFrom = now`.

3. **`LinkOnboardingHabitsToIdentitiesUseCase`** (onboarding):
   - Links inserted get `effectiveFrom = now`.

In all three: the habit's `createdAt` and `effectiveFrom` will be the same value at creation time. They diverge later if a habit is "resurrected" (effective_to cleared) — out of scope for 5e-1.

## Streak engine refactor

### Per-day active filter

Add a helper used by both streak use cases:

```kotlin
internal fun habitActiveOn(habit: Habit, dayStart: Instant, dayEnd: Instant): Boolean =
    (habit.effectiveFrom?.let { it <= dayStart } ?: true) &&
    (habit.effectiveTo?.let { it > dayStart } ?: true)
```

**Semantics in plain English:** habit X is required for day Y iff X existed at the start of Y AND X wasn't removed before Y started.

- Habit added at 14:00 today: `effectiveFrom > dayStart(today)` → NOT required for today. Required from tomorrow onwards.
- Habit deleted at 14:00 today: `effectiveTo > dayStart(today)` (14:00 > 00:00) → still required for today. Not required tomorrow.
- Backfilled habit (`effectiveFrom = first log or createdAt`): past days BEFORE that timestamp don't require it; days AT or AFTER do.

### `ComputeStreakUseCase` changes

- `allHabitsMetTargetOnDay` — accepts `dayStart`, filters `habits` to only those active on that day, then runs the existing all-logged check.
- `bucketFor(...)` — `bareMin` and `targetSum` computed from per-day-active habits, not the full list. Heat bucketing now reflects how many habits were active on each day.
- `summarize(...)` — same per-day filter when building per-day complete check.
- `buildRangeResult(...)` — same.

The `dayStart` for each iteration is already computed by the existing day-walk (`cursor` is a `LocalDate`; `cursor.atStartOfDayIn(timeZone)` produces the `dayStart` Instant). Reuse it for the active filter.

### `ComputeIdentityStatsUseCase` changes

- Per-day filter: a habit is required for an identity's day-X count iff (a) the habit itself is active on X (`Habit.effectiveFrom/to`) AND (b) the habit-identity link is active on X (`HabitIdentityRow.effectiveFrom/to`).
- The use case currently fetches `identityRepo.observeHabitsForIdentity(userId, identityId)` which returns `List<Habit>` via a JOIN with `habit_identities`. Need to extend the query (or add a new query) that returns the habit + the link's effective window so per-day filtering can apply both checks.
- Simplest path: add `getHabitIdentityLinksForUser(userId)` returning `List<HabitIdentityRow>`. The use case fetches both the user's habits and the user's links; per-day, intersects them with active filter applied.

If this proves heavy in implementation, fall back to using only `Habit.effectiveFrom/to` for identity stats (slightly less accurate for users who unlink/re-link habits to identities, but rare). Decide during implementation.

## Testing

### Unit tests (commonTest)

**`ComputeStreakUseCaseTest` — augment:**
- "habit added today not required for today" — habit with `effectiveFrom = now` excluded from today's all-met check.
- "habit added today required for tomorrow" — virtual time + 1 day, habit now required.
- "habit deleted today still required for today" — habit with `effectiveTo = now` still required for today's check.
- "habit deleted today not required for tomorrow" — virtual time + 1 day, habit no longer required.
- "past day before habit existed doesn't require it" — habit with `effectiveFrom = May 5`, day = May 1: not required.
- "backfilled habit with no logs uses createdAt" — sanity check the migration semantics.
- "heat bucket targetSum reflects active habits" — fewer habits active on day X = lower `full` target.

**`ComputeIdentityStatsUseCaseTest` — augment:**
- Same scenarios, scoped via `habit_identities.effectiveFrom/to`.
- "habit linked to identity today not required for that identity's today" — link's `effectiveFrom = now` excludes from today's identity stats.

**`AddIdentityWithHabitsUseCaseTest` — augment:**
- New habits have `effectiveFrom = clock.now()`.
- New habit_identity links have `effectiveFrom = clock.now()`.

**`SetupUserHabitsUseCaseTest` and `LinkOnboardingHabitsToIdentitiesUseCaseTest` — augment if they exist.**

### Manual smoke (pre-merge)

- [ ] Existing user updates app → migration runs (logcat shows version 2→3 migration applied) → past streak heatmap UNCHANGED.
- [ ] Add a new identity via 5c-2 flow → today's streak does NOT reset. Other days unchanged.
- [ ] Sign out → sign in → past streak heatmap consistent (server backfill applied).
- [ ] Multi-device: pull-refresh on second device → no streak shifts vs first device.
- [ ] No regression on simple log-and-streak flow (log a habit, see today's heat bucket update; log all habits, see day go COMPLETE).
- [ ] Identity stats per-identity sparkline + 90d grid render correctly on IdentityDetail.

## Migration / rollout

1. **Apply Supabase migration first** via dashboard SQL Editor — the `ALTER TABLE` + 2 backfill `UPDATE`s.
2. **Verify** existing rows have non-null `effective_from` after backfill.
3. **Deploy app build** with migration `3.sqm` and the engine refactor.
4. On launch, app's local `3.sqm` runs against existing devices' SQLite — adds columns + backfills locally. New rows pulled from server already have the values.

Order matters: app build deployed before Supabase migration would attempt to push rows with `effective_from = null` and the server's column would accept them (no NOT NULL constraint). But pulls would return rows missing the field if the column doesn't exist — DTO has default null so deserialization survives. Safer to apply Supabase first.

## Open questions

None. Design locked.
