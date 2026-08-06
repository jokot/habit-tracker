# Phase 10 — Home-screen widget: design

## Why

Want-timer status already surfaces on the app's Home screen so points-draining is never invisible. Daily habit logging has no equivalent outside the app — a user has to open Habitto to log anything. An Android home-screen widget puts the daily-logging core loop (today's habits + point balance) one tap away from the device home screen, no app launch required.

## Scope

- One resizable, scrollable widget: today's active habits (current user/identity) + current point balance.
- Tap a habit row → logs it instantly (no app open).
- Tap outside a row (header, balance, empty state) → opens app to Home.
- No widget-config screen, no multi-instance support, no per-widget habit picker — v1 shows everything, ordered same as Home. Add configurability later if requested.
- Works for guest users identically to logged-in users (same `currentUserId()` resolution the rest of the app already uses).

## Approach: Jetpack Glance

`androidx.glance:glance-appwidget` — Compose-style widget DSL. Chosen over classic `RemoteViews`/`AppWidgetProvider` because the codebase is already all-in on Compose; Glance reuses the same mental model, provides `GlanceLazyColumn` for the resizable scroll list, `ActionCallback` for suspend-function tap handlers, and `GlanceTheme` for dynamic/dark-mode color — all without hand-rolled XML layouts or `PendingIntent` wiring.

Rejected: a `WorkManager`-driven custom refresh cycle on top of Glance — the OS's built-in periodic-update floor (~30 min) plus tap-triggered updates is enough; no evidence more frequent background refresh is needed.

## Architecture

New package `com.jktdeveloper.habitto.widget` (androidApp):

- `HabitWidget.kt` — `GlanceAppWidget`. `provideGlance()` fetches a one-shot snapshot (not a live Flow — the widget doesn't need reactive ticking) of today's habits + point balance and renders them.
- `HabitWidgetReceiver.kt` — `GlanceAppWidgetReceiver`.
- `LogHabitAction.kt` — `ActionCallback`, handles a habit-row tap.
- `res/xml/habit_widget_info.xml` — provider metadata: resizable, min 2x2.
- `AndroidManifest.xml` — register the receiver + widget-info.
- New Gradle dep in `androidApp/build.gradle.kts`: `androidx.glance:glance-appwidget`.

Data access: the widget reads `(applicationContext as HabitTrackerApplication).container` — the same `AppContainer` singleton every ViewModel already uses. No new DI surface.

## Data flow

1. `provideGlance()` runs on: widget pin, OS periodic refresh (~30 min floor), and any tap-triggered update.
2. Calls `GetTodayHabitsUseCase.execute(userId)` for today's habits + progress, and the existing `GetPointBalanceUseCase.execute(userId)` (same use case the want-timer balance gate already uses) for the point balance. No new balance-calculation logic.
3. Habit-row tap → `LogHabitAction` → `container.logHabitUseCase.execute(userId, habitId, habit.dailyTarget * habit.thresholdPerPoint)`. `dailyTarget` is a **points** cap (`PointCalculator.pointsEarned = quantity / thresholdPerPoint`), not a raw quantity — the logged quantity has to be back-converted through `thresholdPerPoint` so one tap lands exactly on the daily point target. Triggers a widget update so the row reflects the new state.
4. Tap outside row zones (header, balance, empty state) → opens app to Home via existing launch intent.

### New shared use case: `GetTodayHabitsUseCase`

`HomeViewModel` currently computes "today's per-habit progress" (points logged today vs `habit.dailyTarget`) inline, inside its `observeHomeUiState()` flow — not exposed as a reusable use case. The widget needs the identical computation. Rather than duplicating this business rule in two places (risking drift), extract it into `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetTodayHabitsUseCase.kt`:

- Input: `userId`, today's local day boundaries.
- Output: `List<HabitWithProgress>` (habit, pointsToday, doneToday).
- `HomeViewModel` is refactored to call this use case instead of inlining the computation — the inline block it replaces is data-only (no UI concerns), so the refactor is a pure extraction, not a behavior change.
- Widget calls the same use case.

## Error handling

- **Empty state** (no active habits) — widget renders "No habits yet — open app", tap opens app. No crash, no blank widget.
- **Log-tap failure** (rare — db/suspend exception inside the tap handler) — `runCatching` around the use-case call inside `LogHabitAction`. On failure: no-op, next periodic refresh reconciles state. This is a real process boundary (system-triggered callback, no in-widget error UI to show), so it gets an explicit guard rather than being treated as "can't happen."
- **Guest users** — same `currentUserId()` fallback the rest of the app uses; a guest local id is always resolvable, so no separate guest empty-state is needed.

## Testing

- `GetTodayHabitsUseCase` — shared KMP `commonTest`: pure logic (habits + logs + dailyTarget → progress/doneToday), same style as the existing `PointCalculatorTest`.
- Widget rendering and `LogHabitAction` — no automated UI test. Glance widgets aren't practically unit-testable, and this codebase has no Compose screenshot tests elsewhere either. Covered by manual device QA (same pattern as the Phase 9 timer work).
- No dedicated test for `LogHabitAction` itself — it's a thin wrapper over the already-tested `LogHabitUseCase`.
