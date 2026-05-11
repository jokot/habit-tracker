# Phase 7 Pivot — Want Point Model Refactor (units-per-point)

**Status:** in design (pivot of in-progress Phase 7).

**Pivot of:** [`2026-05-10-phase7-want-crud-design.md`](./2026-05-10-phase7-want-crud-design.md). Folds into the same branch (`feature/phase7-want-crud` / PR #21) before merge.

## Why

The shipped Phase 7 model uses `WantActivity.costPerUnit: Double` and computes per-log spend as `ceil(quantity * costPerUnit * rate)`. This breaks decimal-cost wants:

- 10 taps at 0.1 pt each evaluates to `10 × ceil(0.1) = 10 pt` instead of `ceil(10 × 0.1) = 1 pt`.
- Decimals become decorative; user-perceived spend ≠ actual spend.
- WantList catalog shows `−0.1 pt` while a single Today tap actually deducts 1 pt.

Habits already use a clean threshold model: `thresholdPerPoint: Double` interpreted as "X units = +1 pt" (`pointsEarned = floor(quantity / threshold)`). Wants should mirror this.

## Goal

One sentence: **a single tap is always ±1 pt, both for habits and wants.** Each `WantActivity` declares `unitsPerPoint: Int` — how many units of the activity equal one point of spend.

## Domain model

```kotlin
data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,                  // free text ("min", "meal", "match")
    val unitsPerPoint: Int,            // NEW — replaces costPerUnit. Always ≥ 1.
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val iconKey: String? = null,
    val hiddenAt: Instant? = null,
    val updatedAt: Instant = ...,
    val syncedAt: Instant? = null,
)

data class WantLog(
    val id: String,
    val userId: String,
    val activityId: String,
    val quantity: Double,
    val pointsSpent: Int,              // NEW — stamped at write, never recomputed.
    val deviceMode: DeviceMode,
    val loggedAt: Instant,
    val deletedAt: Instant? = null,
    val syncedAt: Instant? = null,
)
```

`costPerUnit: Double` is removed entirely. No transition aliasing.

## Math

```kotlin
object PointCalculator {
    // Habit side — unchanged.
    fun pointsEarned(quantity: Double, threshold: Double): Int =
        (quantity / threshold).toInt()

    // Want side — new.
    fun pointsSpent(taps: Int): Int = taps

    fun effectiveUnitsPerPoint(unitsPerPoint: Int, rate: Double): Int =
        (unitsPerPoint / rate).toInt().coerceAtLeast(1)
}
```

Drop `pointsSpent(quantity, costPerUnit)` and `pointsSpentWithRate(quantity, costPerUnit, rate)`.

### Rate ladder applies via `effectiveUnitsPerPoint`

Rate scales the *units* needed per point, not the points spent. At a higher tier, fewer units per −1 pt → wants are "more expensive". Tap is always −1 pt; the unit count behind a tap shrinks.

| Want | unitsPerPoint | Tier 1 (1.0×) | Tier 5 (2.0×) |
|--|--|--|--|
| YouTube long-form | 10 min | 10 min/−1pt | 5 min/−1pt |
| YouTube Shorts | 1 min | 1 min/−1pt | 1 min/−1pt (clamp) |
| Coffee | 1 cup | 1 cup/−1pt | 1 cup/−1pt (clamp) |
| Hypothetical heavy gaming | 20 min | 20 min/−1pt | 10 min/−1pt |

Items at `unitsPerPoint = 1` are unaffected by rate (clamped to ≥ 1). Items with `unitsPerPoint > 1` get squeezed.

## Use case

```kotlin
class LogWantUseCase(...) {
    suspend fun execute(
        userId: String,
        activityId: String,
        taps: Int = 1,
        deviceMode: DeviceMode,
    ): Result<LogWantResult> = runCatching {
        val activity = wantActivityRepo.getById(activityId)
        val rate = exchangeRateProvider.currentRate(userId)
        val effUnits = PointCalculator.effectiveUnitsPerPoint(activity.unitsPerPoint, rate)
        val quantity = (effUnits * taps).toDouble()
        val pointsSpent = PointCalculator.pointsSpent(taps)
        wantLogRepository.insertLog(
            id = Uuid.random().toString(),
            userId = userId,
            activityId = activityId,
            quantity = quantity,
            pointsSpent = pointsSpent,
            deviceMode = deviceMode,
            loggedAt = clock.now(),
        )
    }
}
```

`WantLogRepository.insertLog` signature gains a `pointsSpent: Int` parameter.

`getDayPointsUseCase` / `getPointBalanceUseCase` switch from recomputing via cost × rate to summing the persisted `pointsSpent`. Once stamped, the historical record is stable across rate changes.

## Schema

### Local — SQLDelight migration `7.sqm`

```sql
-- Replace cost_per_unit with units_per_point.
ALTER TABLE LocalWantActivity ADD COLUMN units_per_point INTEGER NOT NULL DEFAULT 1;
ALTER TABLE LocalWantActivity DROP COLUMN cost_per_unit;

-- Stamp pointsSpent on each log.
ALTER TABLE LocalWantLog ADD COLUMN points_spent INTEGER NOT NULL DEFAULT 1;
DELETE FROM LocalWantLog;
```

Wipe is per locked decision (b) — solo dev, no production logs to preserve.

### Server — Supabase migration

```sql
-- want_activities: replace cost_per_unit with units_per_point.
alter table want_activities add column units_per_point integer not null default 1;

-- Repair user-claimed seed rows: map known names to the rescaled Int.
update want_activities set units_per_point = case lower(name)
    when 'tiktok' then 1
    when 'youtube shorts' then 1
    when 'youtube' then 10
    when 'netflix' then 15
    when 'twitter/x' then 2
    when 'instagram' then 2
    when 'reddit' then 2
    when 'gaming' then 10
    when 'online shopping' then 5
    when 'junk food' then 1
    when 'snacks' then 1
    when 'sweets' then 1
    when 'sugary drinks' then 1
    when 'coffee' then 1
    else 1
end where is_custom = false;

alter table want_activities drop column cost_per_unit;

-- want_logs: wipe (per locked decision b) and stamp pointsSpent on each future log.
truncate table want_logs;
alter table want_logs add column points_spent integer not null default 1;
```

The `cost_per_unit ≥ 0` check constraint added by `20260510000000_phase7_want_activities.sql` is dropped with the column.

## Sync DTO

```kotlin
@Serializable
private data class WantActivityDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val unit: String,
    @SerialName("units_per_point") val unitsPerPoint: Int,   // CHANGED
    @SerialName("is_custom") val isCustom: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("icon_key") val iconKey: String? = null,
    @SerialName("hidden_at") val hiddenAt: String? = null,
)

@Serializable
private data class WantLogDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("activity_id") val activityId: String,
    val quantity: Double,
    @SerialName("points_spent") val pointsSpent: Int,         // NEW
    @SerialName("device_mode") val deviceMode: String,
    @SerialName("logged_at") val loggedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("synced_at") val syncedAt: String? = null,
)
```

## Seed list (rescaled per locked decision A)

| Name | unit | unitsPerPoint | Display |
|--|--|--|--|
| TikTok | min | 1 | 1 min = −1 pt |
| YouTube Shorts | min | 1 | 1 min = −1 pt |
| YouTube | min | 10 | 10 min = −1 pt |
| Netflix | min | 15 | 15 min = −1 pt |
| Twitter/X | min | 2 | 2 min = −1 pt |
| Instagram | min | 2 | 2 min = −1 pt |
| Reddit | min | 2 | 2 min = −1 pt |
| Gaming | min | 10 | 10 min = −1 pt |
| Online shopping | min | 5 | 5 min = −1 pt |
| Junk food | meal | 1 | 1 meal = −1 pt |
| Snacks | serving | 1 | 1 serving = −1 pt |
| Sweets | piece | 1 | 1 piece = −1 pt |
| Sugary drinks | drink | 1 | 1 drink = −1 pt |
| Coffee | cup | 1 | 1 cup = −1 pt |

Notes:
- Wants previously priced > 1 pt/unit (Junk food 2 pt/meal, Valorant ranked 3 pt/match, Purchase session 2 pt/session) become 1 pt/unit. The "expensive" intent is dropped per locked decision A.
- Valorant Ranked, Casual mobile game, PC gaming session collapse into one "Gaming" seed (`unit=min, unitsPerPoint=10`).
- Custom wants users add via WantForm follow the same Int rule.

## UI changes

### WantList
Row subtitle: `"$unitsPerPoint $unit = −1 pt"`. No decimals.

### WantDetail
Hero subtitle: `"$unitsPerPoint $unit = −1 pt"`. Stats:
- "Total spent" = sum of `pointsSpent` across last 7 days.
- "Times logged" = count of logs across last 7 days.

Recent activity rows: `time · ${log.quantity / log.pointsSpent} ${unit} · −${log.pointsSpent} pt` (showing per-tap unit count effective at log time, derived from stored quantity ÷ pointsSpent).

### WantForm
- Cost field replaced with **Int stepper for `unitsPerPoint`**. ±1 step. Min 1. No decimals.
- Unit field becomes free-text `OutlinedTextField` (was FilterChip-only). Suggestion chips remain as quick-pick above the field.
- Cost-edit retro warning is dropped. Historical `pointsSpent` is persisted; future logs use the new value. No retro rewrite.

### Home want chip
- Idle subtitle: `"$unitsPerPoint $unit · −1 pt"`.
- Pending tap: `"−$tapCount pt total · $afterBalance after"` (per-tap pt is constant 1).

### ExchangeRate comparison rows
Per tier, show `effectiveUnitsPerPoint(seed.unitsPerPoint, tier.rate) $unit / −1 pt`.

E.g. YouTube row across tiers:
- T1: 10 min / −1 pt
- T2: 8 min / −1 pt   `(10/1.2).toInt() = 8`
- T3: 7 min / −1 pt   `(10/1.4).toInt() = 7`
- T4: 6 min / −1 pt   `(10/1.6).toInt() = 6`
- T5: 5 min / −1 pt   `(10/2.0).toInt() = 5`

Hidden wants stay excluded (already implemented).

## Migration choreography

1. **SQLDelight migration 7** (schema bump 6 → 7): adds `units_per_point` to `LocalWantActivity`, drops `cost_per_unit`; adds `points_spent` to `LocalWantLog`; deletes all rows from `LocalWantLog`.
2. **AppContainer reconcile rebuild**: `SeedData.wantActivities` rebuilt with `unitsPerPoint: Int`. `SetupUserWantActivitiesUseCase.reconcile` keeps the name-match strategy added in the previous fix; new seeds get fresh per-user UUIDs.
3. **Server migration** (`supabase/migrations/20260511000000_phase7_pivot_units_per_point.sql`): adds `units_per_point` to `want_activities`, drops `cost_per_unit`; truncates `want_logs`, adds `points_spent`. Existing user-claimed `want_activities` rows have their `units_per_point` repaired by an UPDATE step in the migration that maps known seed names to the new Int (rows that don't match by name retain the default of 1).
4. **DTO swap**: `cost_per_unit` → `units_per_point` (Int). New `points_spent` on WantLogDto. Sync engine pushes/pulls these.
5. **Display + form rewrite**: WantList, WantDetail, WantForm, HomeScreen want chip, ExchangeRate comparison rows.
6. **Test rebaseline**: every test that referenced `costPerUnit` or `pointsSpentWithRate`.

User-side: clear app data + reinstall (same as before — local DB needs rebuild against schema 7).

## Tests

- `PointCalculatorTest`: drop `pointsSpentWithRate` cases; add `pointsSpent(taps)` and `effectiveUnitsPerPoint(units, rate)` cases. Verify clamp at 1 and `(10 / 2.0).toInt() == 5`.
- `LogWantUseCaseTest`: assert `quantity == effUnits × taps`, `pointsSpent == taps`. Add rate-applied case.
- `GetDayPointsUseCaseTest` / `GetPointBalanceUseCaseTest`: switch to summing `pointsSpent`.
- `ExchangeRateViewModelTest`: rebaseline comparison rows (Int math).
- `WantListViewModelTest`, `WantDetailViewModelTest` (if present), `WantFormViewModelTest`: replace `costPerUnit` with `unitsPerPoint`. Drop cost-edit warning case from form test.
- `SetupUserWantActivitiesUseCaseTest`: rebuild fixtures with `unitsPerPoint`.
- Repo tests (`LocalWantActivityRepositoryTest`, `LocalWantLogRepositoryTest`): update SQL fixtures.

## Acceptance

- 10 taps on YouTube (unitsPerPoint=10) at tier 1 → 10 pt deducted, 100 minutes logged.
- 10 taps on YouTube at tier 5 → 10 pt deducted, 50 minutes logged. Tier squeezes units per −1 pt.
- 1 tap on Coffee (unitsPerPoint=1) at any tier → 1 pt deducted, 1 cup logged.
- WantList row never shows decimals.
- Today and WantList show identical `unitsPerPoint $unit · −1 pt` framing.
- Custom want creation rejects `unitsPerPoint < 1`.
- Reinstall + onboard yields 14 seeded wants with the new `unitsPerPoint` Ints, fresh per-user UUIDs, no sync errors.

## Out of scope

- Habit threshold model (already correct).
- WantTimer (deferred V2+).
- Per-identity rate ladders (still deferred).
- Pagination / sync hardening (separate ticket).

## Open risks

- **Existing server `want_activities` rows after column swap**: server migration includes a name-mapped UPDATE so user-claimed seed rows get the new `unitsPerPoint` Int. Custom rows fall through to the default 1 — acceptable since custom wants haven't been logged in production.
- **Phase 7 PR #21 is open**: the pivot extends the same branch. PR description must be rewritten to reflect the actual shipped model before merge.
- **Compose recomposition cost** of replacing decimal cost stepper with Int stepper is negligible.
