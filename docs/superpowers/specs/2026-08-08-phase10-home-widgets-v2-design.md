# Phase 10 (revised) — Home-screen widgets: design

**Supersedes** `2026-08-06-phase10-home-widgets-design.md`. That spec shipped a single widget showing today's habits + point balance. This revision replaces it with four independently-pinnable widgets, adds wants, and replaces the periodic-refresh model with event-driven updates.

This is a revision of Phase 10, not a new phase: nothing has merged to `main`, and the v1 manual QA (Task 5) was never signed off. The v1 widget matches neither the old design nor the new one, so it is reworked rather than kept alongside.

## Why revise

Three gaps in v1, all confirmed against the shipped code:

1. **Wants are absent.** The core loop is two-sided — habits earn points, wants spend them. `HabitWidget` renders only `getTodayHabitsUseCase` output and the balance. A widget surfacing only the earning half hides why points matter.
2. **One layout, one widget.** v1 is a single list. Users with few habits and long names want rows; users with many want an icon grid. Someone who only wants the number shouldn't have to pin a scroll list.
3. **Updates are not live.** v1 leaned on the OS periodic floor (~30 min). During a running want timer the balance visibly drains, and a widget 30 minutes stale is worse than no widget.

## Scope

Four separate `GlanceAppWidget`s, each with its own receiver, widget-info XML, and manifest entry:

| # | Widget | Min | Expanded | Content |
|---|---|---|---|---|
| 1 | Balance | 2×2 (110×110dp) | 4×2 (250×110dp) | Balance numeral + streak flame |
| 2 | Quick log — list | 4×2 (250×110dp) | 4×5 (250×320dp) | Balance header, habit rows, divider, want rows |
| 3 | Quick log — grid | 4×2 | 4×4 (250×250dp) | Fixed icon tile grid, 3 across |
| 4 | Streak | 4×2 | 4×3 (250×180dp) | Streak count + dense heat grid |

Out of scope, unchanged from v1: no widget-config screen, no per-widget item picker, no multi-instance customization. Guest users behave identically to logged-in users via the existing `currentUserId()`.

### Sizing deviations from the design brief

Two sizes changed during design and are adopted here:

- **Widget 1 min is 2×2, not the 4×1 the brief specified.** A 4×1 cell is ~40dp tall and cannot hold a 40px numeral. 2×2 is the smallest cell that renders the widget's only job.
- **Widget 2 expanded is 4×5, not 4×4.** Measured content is ~305dp (header 32 + two habit rows at 48 + divider 9 + three want rows at 48 + padding 24), which exceeds a 4×4 cell's ~250dp. Declaring 4×4 would clip the last want row.

## Refresh model

v1 rejected a `WorkManager` refresh cycle in favor of the OS floor. That decision is reversed in effect but not in mechanism — no `WorkManager`, and no `AlarmManager` either. Three sources, in order of importance:

**1. Event-driven, for anything that writes to the DB.** A single `WidgetRefresher` in `HabitTrackerApplication` observes the repository Flows the app already exposes (point balance, today's habit logs, want logs, habit/want lists) and calls `updateAll` on each of the four widgets when they change. One collector at the point every mutation flows through, rather than a call at every mutation site — `LogHabitAction`, the log ViewModels, timer start/cancel, and exchange all get covered without individually calling it. Debounce so a burst of writes coalesces into one update.

**2. Per-minute tick, for the running want timer.** This is a separate mechanism because live point drain is **time-derived, not DB-derived** — points are only written when the timer ends, so the DB collector above never fires during a run. `WantTimerService.runUntilEnd()` already loops on a minute-aligned tick (`WantTimerService.kt:110`), recomputing `elapsedMin` and `pointsSpent` and re-issuing its notification each pass. Add the widget update to that existing loop. No new service, no new coroutine, no new wakeups — the foreground service is already running and already ticking at exactly the resolution the data has. Per-minute is the correct rate because want cost is per-minute; a faster tick would render identical output.

**3. `updatePeriodMillis` at 30 min, as a backstop only.** It is no longer the primary mechanism. It exists to cover date rollover past midnight and to reconcile a widget whose app process was killed while a source (1) event was pending. Zero new code.

## Architecture

Existing, kept: `GetTodayHabitsUseCase` (shared `commonMain`), `AppContainer` wiring, `LogHabitAction`'s `runCatching` boundary and its `nextUp()` quantity correction (`HabitWidget.kt:84` — the double-rounding fix is load-bearing and carries over unchanged).

Existing, replaced: `HabitWidget.kt` and `HabitWidgetReceiver.kt` become four widget/receiver pairs in `com.jktdeveloper.habitto.widget`:

- `BalanceWidget.kt` / `BalanceWidgetReceiver.kt`
- `QuickLogListWidget.kt` / `QuickLogListWidgetReceiver.kt`
- `QuickLogGridWidget.kt` / `QuickLogGridWidgetReceiver.kt`
- `StreakWidget.kt` / `StreakWidgetReceiver.kt`
- `WidgetRefresher.kt` — the Application-scoped collector from source (1)
- `res/xml/*_widget_info.xml` — one per widget
- Shared composables (surface, balance header, icon tile, row) in a single `WidgetComponents.kt` rather than duplicated per widget

Actions:

- `LogHabitAction` — unchanged behavior, now shared by widgets 2 and 3.
- `LogWantAction` — new. For a want with `unit != "min"`, calls the existing `LogWantUseCase` directly with `taps = 1`. An instant want is not a timer; one tap spends one point.
- **Minute-unit wants do not start a timer from the widget.** Tapping one opens the app at the existing want-timer deep link, `com.jktdeveloper.habitto://want-timer/$activityId` (registered at `AndroidManifest.xml:36`, already used by `WantTimerService`'s notification). Two reasons: `WantTimerController.start` requires a `durationSec` the widget has no sensible way to invent, and a timer is an open-ended point drain — a mis-tap on the home screen would start spending with no in-widget way to see or stop it. The user picks the duration on the screen that exists for it. This uses `actionStartActivity` with the URI; no new action class is needed.

Data access stays `(applicationContext as HabitTrackerApplication).container`. No new DI surface.

### Streak data

Widget 4 needs per-day streak state across 36 cells (min) or 60 (expanded), reusing the Phase 4 model (`COMPLETE` / `FROZEN` / `BROKEN` / `EMPTY` / `TODAY_PENDING` / `FUTURE`) and the colors already in `ui/theme/Color.kt`.

No extraction is needed — `ComputeStreakUseCase` already exposes both suspend entry points in `commonMain`, which is exactly what `provideGlance` needs:

- `computeNow(userId: String, range: DateRange): StreakRangeResult` → `days: List<StreakDay>`, each with `date`, `state`, `heatLevel`
- `computeSummaryNow(userId: String): StreakSummary` → `currentStreak`, `longestStreak`, `totalDaysComplete`, `firstLogDate`

`DateRange` is half-open `[start, endExclusive)`.

## Widget content rules

**Habits vs wants carry no color coding.** Direction is carried by the sign and value text (`+1` versus `−2/min`). Identity tint applies to the habit glyph only; the flame is the single accent. All surfaces neutral. This matches the rest of the app — `WantList` renders wants on neutral `--m-surface-1`, and `--m-error` is reserved for destructive actions.

**Item selection.** Widgets can't show 14 seeded wants. Widget 2 min shows one habit row and no wants; expanded shows two habits and three wants. Widget 3 min is a fixed 3×1, expanded 3×2. Selection is most-used, stable ordering.

**Affordability.** *(Corrected 2026-08-08 against the real domain model — see "Model corrections" below.)* There is no per-want cost. `PointCalculator.pointsSpent(taps) = taps`: one tap is one point for every want, and `WantActivity.unitsPerPoint` controls how many *units* one point buys, not what it costs. Affordability is therefore uniform:

```kotlin
val unaffordable = balance <= 0
```

This is exactly the gate `WantTimerController.start` already enforces (`if (balance <= 0) throw InsufficientPointsException(available = balance, required = 1)`). The widget must render the same rule, not invent a second one. At zero balance every want renders unavailable; above zero, none do.

The mockup's mixed lock state (one want locked, another free at the same balance) is not reproducible and must not be implemented.

**Widget 3 at min size omits the balance header** to fit three 48dp tiles in a 110dp cell. At min it is a quick-log widget only; the balance returns when expanded.

## Glance constraints

These are platform limits, and three of them are violated by the current mockup — the implementation must translate rather than copy:

- **No opacity modifier** on a `Row`/`Column`. The mockup's `opacity: 0.4` for unaffordable items must be baked into the color values instead.
- **No border modifier.** The mockup's `1px solid` surface and running-tile outlines need a drawable background resource.
- **`cornerRadius` is API 31+.** `minSdk` is 26, so rounded corners below 31 need a drawable. Confirm the fallback renders acceptably rather than silently squaring off.
- **No `LazyVerticalGrid`.** Widget 3's grid is a fixed set of `Row`s of `Column`s, not a flowing grid. `LazyColumn` is available and used by widget 2.
- **No `oklch()`, no CSS gradients** on the widget surface. The mockup's wallpaper backdrop is presentation-only and does not ship.
- **No `aspectRatio`** — explicit dp per cell.
- **Tap targets ≥48dp, rectangular.**
- **No animation or transition** — every state is a static render.

## Error handling

- **Empty state** — each widget renders its own: "No habits yet — open app" (2, 3), "0 pts" (1), "Start a streak" (4). Tap opens the app. No blank widget.
- **Tap failure** — `runCatching` around the use-case call inside every action callback, not just `LogHabitAction`. These are system-triggered callbacks with no in-widget error surface; on failure the action no-ops and the next update reconciles.
- **Timer start on zero balance** — `StartWantTimerAction` must respect the existing balance gate rather than reimplementing it. A blocked start no-ops; the want already renders unavailable.
- **Guest users** — unchanged, `currentUserId()` always resolves.

## Testing

- **Widget item selection** — shared `commonTest`, over a pure function taking habits, wants, balance and a slot count, returning the rows to render with their enabled/disabled state. This is where the affordability rule and the truncation rule live, and it is the only widget logic with enough branching to be worth testing. Cover: zero balance disables every want, positive balance disables none, fewer items than slots, more items than slots.
- **Streak** — no new test. `ComputeStreakUseCase` is already covered and the widget only reads it.
- **Widget rendering and action callbacks** — no automated test. Glance widgets aren't practically unit-testable and this codebase has no Compose screenshot tests. Manual device QA, same as v1 and Phase 9.
- **Manual QA must cover the refresh model specifically**, since it is the part most likely to be wrong and the least visible in code review: log a habit in-app and confirm the widget updates without waiting; start a want timer and confirm per-minute drain on the widget; kill the app process and confirm the widget still reconciles within 30 minutes.

## Model corrections

Three things in the first draft of this spec came from the design canvas rather than the codebase, and were corrected on 2026-08-08 after reading the domain layer. Recorded here because the mockup still shows the old behavior:

1. **`want.cost` does not exist.** `WantActivity` is `(id, name, unit, unitsPerPoint, isCustom, createdByUserId, iconKey, hiddenAt, updatedAt, syncedAt)`. Spend is `PointCalculator.pointsSpent(taps) = taps` — one tap, one point, every want. `unitsPerPoint` (scaled by the streak exchange rate via `effectiveUnitsPerPoint`) sets how many units a point buys.
2. **`ESTIMATED_SESSION_MINUTES` was fictional** and is dropped along with the per-want affordability formula. See "Affordability".
3. **Widget row copy.** Because every want costs 1 point per tap, a want row shows `−1 pt` uniformly. The differentiator worth surfacing is the rate — how many units that point buys (e.g. `5 min` vs `1 cup`), which is what `effectiveUnitsPerPoint` returns and what varies between wants.
