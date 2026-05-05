# Phase 6 — Exchange Rate Design

**Status:** Approved
**Date:** 2026-05-05
**Branch:** feature/phase6-exchange-rate

## Goal

Add a stepped point-spending multiplier ("exchange rate") that scales with the user-level streak. Wants cost more as streaks grow; habit earning is unchanged. Surfaces on a dedicated `ExchangeRate` screen reachable from YouHub and the Home Balance card.

## Architecture

Pure stateless rate function + historical streak lookup + integration into existing want spend math. Single source of truth for the formula in `ExchangeRateCalculator`. Past spends stay accurate to the streak that existed on the log day (rate-at-log-day, not rate-at-render-time).

## Decisions Log

| Decision | Choice | Rationale |
|---|---|---|
| Rate formula | Stepped (5 tiers: 1.0 / 1.1 / 1.2 / 1.3 / 1.4) | Hits canvas-promised 1.4× at Day 30; capped; no daily drift; memorable milestones; trivial to implement and visualize. |
| Rate scope | User-level (single rate from `ComputeStreakUseCase.currentStreak`) | Matches canvas YouHub copy "Point exchange rate · 1.4× · earned by 30-day streak"; wants aren't identity-tied today. |
| Past spend math | Recompute with rate-at-log-day (Q3 option B) | Past balance stays accurate to history; no schema change; uses existing engine. |
| Affects | Want spending only | Canvas line 1210: "You earn the same — but spending power is steeper." Habit earning untouched. |
| Tier-change notification | Defer to follow-up | Phase tightness; visual reinforcement on YouHub + ExchangeRate screen sufficient for v1. |
| Entry points | YouHub row + Home Balance card tap | Per IA diagram; Home banner explicitly removed in canvas. |
| Screen content | Hero + tier ladder + comparison (Q6 option B) | Stepped formula doesn't fit a smooth curve — tier ladder visualizes the levels accurately. Comparison still works against existing seeded wants without Phase 7 (Want CRUD). |

## Stepped Formula

```kotlin
object ExchangeRateCalculator {
    // Tier ladder — single source of truth.
    val tiers: List<RateTier> = listOf(
        RateTier(level = 1, rate = 1.0, minStreak = 0,  maxStreak = 6),
        RateTier(level = 2, rate = 1.1, minStreak = 7,  maxStreak = 13),
        RateTier(level = 3, rate = 1.2, minStreak = 14, maxStreak = 20),
        RateTier(level = 4, rate = 1.3, minStreak = 21, maxStreak = 29),
        RateTier(level = 5, rate = 1.4, minStreak = 30, maxStreak = null), // capped
    )

    fun rateFor(streak: Int): Double = tierFor(streak).rate

    fun tierFor(streak: Int): RateTier = tiers.first { tier ->
        streak >= tier.minStreak && (tier.maxStreak == null || streak <= tier.maxStreak)
    }

    /** Days remaining until the user's current tier becomes the next tier. Null if at max tier. */
    fun daysToNextTier(streak: Int): Int? {
        val current = tierFor(streak)
        val next = tiers.firstOrNull { it.level == current.level + 1 } ?: return null
        return next.minStreak - streak
    }
}

data class RateTier(
    val level: Int,
    val rate: Double,
    val minStreak: Int,
    val maxStreak: Int?,
)
```

| Streak | Tier | Rate |
|---|---|---|
| 0–6 | 1 | 1.0× |
| 7–13 | 2 | 1.1× |
| 14–20 | 3 | 1.2× |
| 21–29 | 4 | 1.3× |
| 30+ | 5 | 1.4× |

## Components

### New (shared/commonMain)

- `ExchangeRateCalculator` — pure object with `tiers`, `rateFor`, `tierFor`, `daysToNextTier`
- `RateTier` — data class
- `GetUserStreakOnDayUseCase`
  - `suspend fun execute(userId: String, date: LocalDate): Int`
  - Walks backward from `date` using `ComputeStreakUseCase.computeNow` over a 365-day window, counting consecutive COMPLETE days inclusive of `date`. Returns 0 if `date` itself is not COMPLETE or has no logs.

### Modified (shared/commonMain)

- `LogWantUseCase`
  - Pull current user-level streak via `ComputeStreakUseCase.observeCurrent.first()`.
  - `points = ceil(quantity × costPerUnit × rate).coerceAtLeast(1)`
  - `WantLog` row stays raw (`quantity` only). The `pointsSpent` returned to the caller is rate-applied.
- `GetPointBalanceUseCase`
  - For each want log, look up `streakOnLogDay` via `GetUserStreakOnDayUseCase`, derive `rate`, multiply.
- `GetDayPointsUseCase`
  - Same — per-day rate lookup. One streak lookup per cursor date per query (the day's all want logs share the rate).

### New (androidApp)

- `ExchangeRateViewModel`
  - State: `currentStreak: Int, currentRate: Double, currentTier: RateTier, daysToNext: Int?, comparison: List<ComparisonRow>`
  - `ComparisonRow(name, icon, unit, baseCostPerUnit, currentCostPerUnit)`
  - Reactive: observes user-level streak + user's WantActivities.
- `ExchangeRateScreen`
  - TopAppBar: back icon, title "Exchange rate"
  - Hero: "TODAY" eyebrow + large numeral (`{rate}×`) + sub-line ("You're at Tier {level} of 5. {daysToNext} days to {nextRate}×." or "Top tier reached." if level=5)
  - Tier ladder: 5 rows top-down (Tier 5 top, Tier 1 bottom), current tier ring-highlighted, completed tiers checked, unreached tiers grayed
  - Comparison: "What it costs now" section, list user's WantActivities. Per row: glyph + name + "per {unit}" + `baseCost (strikethrough) → currentCost pt`. Hide section if user has no wants.

### Modified (androidApp)

- `AppNavigation` — `Screen.ExchangeRate` route + composable mount
- `AppContainer` — wire `getUserStreakOnDayUseCase`
- `YouHubScreen` — add "Earn & spend" section, "Point exchange rate" row showing current rate; tap → nav ExchangeRate
- `HomeScreen` + `HomeViewModel` — Balance card becomes tappable, navs to ExchangeRate. VM exposes `currentRate` for any inline display (Balance card sub-line optional)

## Data Flow

### Want spend (live)

```
User taps Want → HomeViewModel.tapWant
  → confirms → LogWantUseCase.execute(activity, quantity)
    → currentStreak = ComputeStreakUseCase.observeCurrent.first().currentStreak
    → rate = ExchangeRateCalculator.rateFor(currentStreak)
    → points = ceil(quantity × costPerUnit × rate).coerceAtLeast(1)
    → wantLogRepo.insertLog(...)
  → triggerSync
```

`WantLog` schema unchanged — stored quantity stays raw. `pointsSpent` is computed at read time using historical streak/rate.

### Balance read

```
GetPointBalanceUseCase.execute(userId)
  → habit logs sum: PointCalculator.pointsEarned(quantity, threshold)  // unchanged, no rate
  → want logs sum: per log,
    streakOnLogDay = GetUserStreakOnDayUseCase.execute(userId, log.loggedAt.date)
    rate = ExchangeRateCalculator.rateFor(streakOnLogDay)
    spent = ceil(log.quantity × activity.costPerUnit × rate).coerceAtLeast(1)
  → balance = earned - spent
```

### Day points

```
GetDayPointsUseCase.observe(userId, date)
  → earned: same as before (no rate)
  → spent: streakOnDay = GetUserStreakOnDayUseCase.execute(userId, date)
           rate = ExchangeRateCalculator.rateFor(streakOnDay)
           apply rate to all want logs on that date
  → emit daily totals
```

### ExchangeRate screen

```
ExchangeRateViewModel.init
  → combine(streakUseCase.observeCurrent, wantActivityRepo.observeForUser)
    .collect { (streak, wants) ->
      val rate = ExchangeRateCalculator.rateFor(streak.currentStreak)
      val tier = ExchangeRateCalculator.tierFor(streak.currentStreak)
      val daysToNext = ExchangeRateCalculator.daysToNextTier(streak.currentStreak)
      val comparison = wants.map { activity ->
        ComparisonRow(
          name = activity.name,
          icon = activity.icon,
          unit = activity.unit,
          baseCostPerUnit = activity.costPerUnit,
          currentCostPerUnit = activity.costPerUnit * rate,
        )
      }
      _state.value = ExchangeRateState(streak.currentStreak, rate, tier, daysToNext, comparison)
    }
```

## Tier Ladder + Hero UX

**Hero**:
```
TODAY
1.2×                    (large, theme primary color)
You're at Tier 3 of 5. 7 days to 1.3×.
```

When `daysToNext == null` (Tier 5):
```
TODAY
1.4×
Top tier reached.
```

**Tier ladder** (5 rows, top-down 5→1 to mirror canvas curve direction):
```
Tier 5 · 1.4×    30+ days        ─── grayed if not reached
Tier 4 · 1.3×    21–29 days      ─── grayed if not reached
Tier 3 · 1.2×    14–20 days      ─── HIGHLIGHTED + ring (current)
Tier 2 · 1.1×    7–13 days       ─── checked (passed)
Tier 1 · 1.0×    0–6 days        ─── checked (passed)
```

**Comparison list** ("What it costs now"):
- Iterate user's WantActivities
- Per row: glyph + name + sub "per {unit}" + `baseCost (strikethrough) → currentCost (pt, error/primary color)`
- Hide entire section if user has zero wants

**Empty state** (Tier 1, no streak yet):
- Hero shows 1.0×
- Sub: "Complete a 7-day streak to unlock 1.1× rate"
- Tier ladder shows progression
- Comparison list shows wants at base cost (1.0× = no diff to display, render plain `cost pt`)

## `GetUserStreakOnDayUseCase` Detail

```kotlin
class GetUserStreakOnDayUseCase(
    private val streakUseCase: ComputeStreakUseCase,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend fun execute(userId: String, date: LocalDate): Int {
        // 365-day backward window covers practical streaks. Bump if needed.
        val window = DateRange(
            start = date.minus(365, DateTimeUnit.DAY),
            endExclusive = date.plus(1, DateTimeUnit.DAY),
        )
        val result = streakUseCase.computeNow(userId, window)
        val byDate = result.days.associateBy { it.date }
        var run = 0
        var cursor = date
        while (true) {
            val state = byDate[cursor]?.state ?: break
            if (state == StreakDayState.COMPLETE) {
                run += 1
                cursor = cursor.minus(1, DateTimeUnit.DAY)
            } else break
        }
        return run
    }
}
```

Caching: not needed for v1. Each balance query may invoke this once per unique log-date — for typical users (a few logs per day, hundreds of days max) that's still small.

Optimization (later): batch — compute streak series once per balance query, build a `Map<LocalDate, Int>` of streak-on-each-day, look up.

## Error Handling

| Scenario | Handling |
|---|---|
| Streak engine fails | rate falls back to 1.0 (safe — never punish user with higher costs on error) |
| Invalid `costPerUnit` (≤0) | existing `PointCalculator.pointsSpent` already coerces to ≥1pt minimum; rate doesn't change that |
| Past log dated before any habit logs (`firstActivity == null`) | `streakOnDay` returns 0 → rate 1.0 → base cost |
| Streak engine returns NaN/inf rate (impossible by formula but defensive) | `rate.coerceAtLeast(1.0)` |
| User logs want at 23:59:55 then streak ticks to next day at 00:00:00 | log is dated to the LocalDate at insert time; streak lookup uses that date. No race. |

## Sync

- `WantLog` table: no change (still stores raw quantity).
- No new tables, no schema migration, no Supabase migration.
- Existing sync push/pull handles want logs unchanged.
- Rate is computed deterministically from streak + log date — no need to persist or sync the rate.

## Testing

### `commonTest` (use cases + calculator)

- `ExchangeRateCalculatorTest`
  - `rateFor` boundary cases: 0, 6, 7, 13, 14, 20, 21, 29, 30, 100 → expected rates
  - `tierFor` same boundaries → expected tier level
  - `daysToNextTier` — 0 → 7, 6 → 1, 7 → 7, 13 → 1, 30 → null, 100 → null
- `GetUserStreakOnDayUseCaseTest`
  - Empty logs → 0 for any date
  - 5 consecutive COMPLETE days ending today → 5 when querying today, 4 when querying yesterday
  - Mid-week gap (Mon-Wed COMPLETE, Thu missed, Fri-Sat COMPLETE) → query Sat → 2; query Wed → 3
  - Querying past date returns streak ending on that date, not today's streak
- `LogWantUseCaseTest` (modify existing)
  - Streak 0 → rate 1.0 → points unchanged from pre-Phase-6
  - Streak 14 → rate 1.2 → `ceil(qty × cost × 1.2)` matches expected
  - Streak 30 → rate 1.4 → matches
- `GetPointBalanceUseCaseTest` (modify existing)
  - Want logged on Day 5 (streak 0 → 1.0×) and Day 35 (streak ≥30 → 1.4×) → balance correctly applies different rates per log
  - Habit logs unchanged by rate (regression sanity)
- `GetDayPointsUseCaseTest` (modify existing)
  - Same — per-day rate applied to want spend column only

### `androidUnitTest` (ViewModel)

- `ExchangeRateViewModelTest`
  - Streak 0 → state shows tier 1, rate 1.0×, daysToNext=7
  - Streak 22 → tier 4, daysToNext=8
  - Streak 100 → tier 5, daysToNext=null
  - Comparison: 2 wants → 2 rows; rate applied to currentCostPerUnit

## Phase Boundary — Deferred

- **Tier-change push notification** — Phase 6.5 polish.
- **Home rate banner** — design explicitly removed (home.jsx:523).
- **Per-identity rate** — decided user-level only.
- **Past-spend tooltip** ("This was logged at 1.2× rate") — UX polish.
- **Streak history caching** for `GetUserStreakOnDayUseCase` — premature optimization.
- **Want CRUD** — Phase 7. Comparison list still works against existing seeded `WantActivity` rows.

## Files Touched

### Created (shared/commonMain)

- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculator.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/RateTier.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetUserStreakOnDayUseCase.kt`

### Created (shared/commonTest)

- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/ExchangeRateCalculatorTest.kt`
- `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetUserStreakOnDayUseCaseTest.kt`

### Created (androidApp)

- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModel.kt`
- `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/ui/exchange/ExchangeRateViewModelTest.kt`

### Modified (shared/commonMain)

- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/LogWantUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetPointBalanceUseCase.kt`
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetDayPointsUseCase.kt`

### Modified (shared/commonTest)

- `LogWantUseCaseTest.kt`, `GetPointBalanceUseCaseTest.kt`, `GetDayPointsUseCaseTest.kt`

### Modified (androidApp)

- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/YouHubScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeScreen.kt`
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`
