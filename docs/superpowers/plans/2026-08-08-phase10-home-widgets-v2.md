# Phase 10 (revised) — Home-screen Widgets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `HabitWidget` with four independently-pinnable Glance widgets — balance, quick-log list, quick-log grid, streak — that surface both habits and wants and update on every mutation instead of waiting for the OS refresh floor.

**Architecture:** One shared, testable selector (`WidgetItemSelector`) plus one shared loader (`GetWidgetDataUseCase`) in `commonMain` feed all four widgets, so no widget assembles domain data itself. Each widget is a `GlanceAppWidget` with `SizeMode.Responsive` branching between a min and an expanded layout. Updates come from three sources: an Application-scoped `WidgetRefresher` collecting the repository Flows (DB-derived changes), a hook in the existing `WantTimerService` minute tick (time-derived drain), and `updatePeriodMillis` as a midnight/process-death backstop.

**Tech Stack:** Kotlin Multiplatform (`mobile/shared` commonMain/commonTest), Jetpack Glance 1.1.1 (`androidx.glance:glance-appwidget`, `glance-material3`), SQLDelight-backed repositories, kotlinx-coroutines Flow, `kotlin.test` for shared tests.

## Global Constraints

Copied from `docs/superpowers/specs/2026-08-08-phase10-home-widgets-v2-design.md`. Every task's requirements implicitly include this section.

- **Package for all Android widget code:** `com.jktdeveloper.habitto.widget`. Shared domain code goes in `com.habittracker.domain.usecase`.
- **`minSdk` is 26.** `GlanceModifier.cornerRadius()` is API 31+ and is a silent no-op below it. Never rely on it for a shape that must exist.
- **No border modifier in Glance.** Do not attempt outlines.
- **No opacity modifier on `Row`/`Column`.** Disabled state must be baked into the color value.
- **No `LazyVerticalGrid`.** Widget 3's grid is fixed `Row`s of `Column`s. `LazyColumn` exists and is used by widget 2 only.
- **No `aspectRatio`.** Explicit dp per cell.
- **No animation, no transition.** Every state is a static render.
- **Tap targets ≥48dp and rectangular.**
- **Affordability is uniform: `balance <= 0` disables every want.** There is no per-want cost. `PointCalculator.pointsSpent(taps) = taps` — one tap costs one point for every want. Never implement a per-want affordability formula. Never reference `want.cost` or `ESTIMATED_SESSION_MINUTES`; neither exists.
- **Item order is the app's existing order, truncated — not usage-ranked.** The spec says "most-used, stable ordering"; the domain layer has no usage count, and neither `SelectHabitsForUser` nor `SelectWantActivitiesForUser` carries an `ORDER BY`, so both return insertion order — the same order Home and the Want list already show. `WidgetItemSelector` therefore takes the first N. Ranking by log count would mean loading and grouping every log on every widget render; add it only if a user asks for it. State this deviation in the QA doc.
- **Minute-unit wants (`unit == "min"`) never start a timer from the widget.** They deep-link to `com.jktdeveloper.habitto://want-timer/$activityId`.
- **Habits and wants carry no color coding.** Direction is carried by sign and value text (`+1` vs `−1 pt`). The flame is the only accent. All surfaces neutral.
- **No icons in widgets.** `habitIcon(name)` and `resolveWantIcon(iconKey, name)` return `androidx.compose.ui.graphics.vector.ImageVector`, which Glance cannot render — Glance `Image` needs an `ImageProvider` backed by a drawable resource or bitmap. Widgets are text-first; grid tiles use the item name's first letter as the glyph. This is a deliberate deviation from the mockup and must be stated in the QA doc, not silently worked around with bitmap rasterization.
- **Theme:** wrap every widget's content in `GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme))`, matching the shipped `HabitWidget`.
- **Data access:** `(context.applicationContext as HabitTrackerApplication).container`. No new DI surface.
- **Every `ActionCallback` wraps its use-case call in `runCatching`.** These are system-triggered callbacks with no in-widget error surface.
- **`updatePeriodMillis="1800000"` on all four widget-info XMLs** — backstop only, not the primary refresh mechanism.
- **Commit trailer:** every commit message ends with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.
- **Shell commands are prefixed `rtk`** per `CLAUDE.md`, including inside `&&` chains. Use `rtk proxy <cmd>` when raw output is needed.

---

## File Structure

**Created — shared (`mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/`):**

| File | Responsibility |
|---|---|
| `WidgetItemSelector.kt` | Pure selection + truncation + affordability. Holds `WidgetHabitItem`, `WidgetWantItem`, `WidgetItems`, `WidgetData`, `WidgetItemSelector`. The only widget logic with branching worth testing. |
| `GetWidgetDataUseCase.kt` | One suspend call that gathers balance, streak, habits and wants and runs them through the selector. Every widget's `provideGlance` calls this and nothing else. |

**Created — shared test (`mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/`):**

| File | Responsibility |
|---|---|
| `WidgetItemSelectorTest.kt` | Covers the affordability rule, truncation, hidden-want filtering, `logQuantity`, and `isTimed`. |

**Created — androidApp (`mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/`):**

| File | Responsibility |
|---|---|
| `WidgetComponents.kt` | Shared composables: surface container, balance header, habit row, want row, grid tile, empty state, heat color. No widget owns a copy. |
| `WidgetUpdates.kt` | `suspend fun updateAll(context)` — the single fan-out point for all four widgets. Every refresh source calls this. |
| `BalanceWidget.kt` + `BalanceWidgetReceiver.kt` | Widget 1. |
| `QuickLogListWidget.kt` + `QuickLogListWidgetReceiver.kt` | Widget 2. |
| `QuickLogGridWidget.kt` + `QuickLogGridWidgetReceiver.kt` | Widget 3. |
| `StreakWidget.kt` + `StreakWidgetReceiver.kt` | Widget 4. |
| `LogWantAction.kt` | Instant (non-minute) want tap → `LogWantUseCase`. |
| `WidgetRefresher.kt` | Application-scoped Flow collector; the DB-derived refresh source. |

**Created — res (`mobile/androidApp/src/androidMain/res/xml/`):** `balance_widget_info.xml`, `quick_log_list_widget_info.xml`, `quick_log_grid_widget_info.xml`, `streak_widget_info.xml`.

**Created — docs:** `docs/qa/2026-08-08-phase10-widgets-v2-qa.md`.

**Modified:** `AppContainer.kt` (add `getWidgetDataUseCase`), `HabitTrackerApplication.kt` (start `WidgetRefresher`), `WantTimerService.kt` (tick hook), `AndroidManifest.xml` (swap one receiver for four), `res/values/strings.xml` (four descriptions), `LogHabitAction.kt` (fan-out).

**Deleted:** `HabitWidget.kt`, `HabitWidgetReceiver.kt`, `res/xml/habit_widget_info.xml`, the `habit_widget_description` string, and the `.widget.HabitWidgetReceiver` manifest entry.

### Behavior change to state explicitly

`HabitWidget.HabitRow` logs `(habit.dailyTarget * habit.thresholdPerPoint).nextUp()` — **one tap fills the entire daily target**. The design shows `+1`. This plan changes the logged quantity to `habit.thresholdPerPoint.nextUp()` — **one tap, one point**. The `nextUp()` ULP correction is kept and carried into `WidgetItemSelector` with its reasoning intact, because `thresholdPerPoint` values like `1/3` round the same way. This is a deliberate behavior change from v1, not a regression; the QA doc records it.

---

### Task 1: Shared widget data layer

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/WidgetItemSelector.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetWidgetDataUseCase.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/WidgetItemSelectorTest.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` (add one property after the `logWantUseCase` block that starts at line 150)

**Interfaces:**
- Consumes: `GetTodayHabitsUseCase.execute(userId: String): List<HabitWithProgress>`; `WantActivityRepository.getWantActivities(userId: String): List<WantActivity>`; `GetPointBalanceUseCase.execute(userId: String): Result<PointBalance>`; `ComputeStreakUseCase.computeSummaryNow(userId: String): StreakSummary`; `ExchangeRateCalculator.rateFor(streak: Int): Double`; `PointCalculator.effectiveUnitsPerPoint(unitsPerPoint: Int, rate: Double): Int`; `PointCalculator.pointsEarned(quantity: Double, threshold: Double): Int`.
- Produces: `WidgetData(balance: Int, currentStreak: Int, items: WidgetItems)`; `WidgetItems(habits: List<WidgetHabitItem>, wants: List<WidgetWantItem>)`; `WidgetHabitItem(habitId, name, progressText, isGoalMet, logQuantity)`; `WidgetWantItem(activityId, name, rateText, isTimed, enabled)`; `WidgetItemSelector.select(...)`; `WidgetItemSelector.MINUTE_UNIT`; `GetWidgetDataUseCase.execute(userId: String, habitSlots: Int, wantSlots: Int): WidgetData`; `AppContainer.getWidgetDataUseCase`. Tasks 2–7 consume these.

- [ ] **Step 1: Write the failing test**

Create `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/WidgetItemSelectorTest.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun habit(
    id: String,
    name: String,
    thresholdPerPoint: Double = 1.0,
    dailyTarget: Int = 3,
    pointsToday: Int = 0,
) = HabitWithProgress(
    habit = Habit(
        id = id,
        userId = "u1",
        templateId = null,
        name = name,
        unit = "rep",
        thresholdPerPoint = thresholdPerPoint,
        dailyTarget = dailyTarget,
        createdAt = Instant.fromEpochSeconds(0),
        updatedAt = Instant.fromEpochSeconds(0),
        syncedAt = null,
        effectiveFrom = Instant.fromEpochSeconds(0),
        effectiveTo = null,
    ),
    pointsToday = pointsToday,
)

private fun want(
    id: String,
    name: String,
    unit: String = "min",
    unitsPerPoint: Int = 10,
    hidden: Boolean = false,
) = WantActivity(
    id = id,
    name = name,
    unit = unit,
    unitsPerPoint = unitsPerPoint,
    isCustom = false,
    createdByUserId = null,
    iconKey = null,
    hiddenAt = if (hidden) Instant.fromEpochSeconds(1) else null,
    updatedAt = Instant.fromEpochSeconds(0),
    syncedAt = null,
)

class WidgetItemSelectorTest {

    @Test
    fun `zero balance disables every want`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube"), want("w2", "Soda", unit = "cup")),
            balance = 0,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertEquals(2, result.wants.size)
        assertTrue(result.wants.none { it.enabled })
    }

    @Test
    fun `negative balance disables every want`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube")),
            balance = -4,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertFalse(result.wants.single().enabled)
    }

    @Test
    fun `positive balance disables no want`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube"), want("w2", "Soda", unit = "cup")),
            balance = 1,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertTrue(result.wants.all { it.enabled })
    }

    @Test
    fun `truncates to slot counts`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Run"), habit("h2", "Read"), habit("h3", "Meditate")),
            wants = listOf(want("w1", "A"), want("w2", "B"), want("w3", "C"), want("w4", "D")),
            balance = 10,
            rate = 1.0,
            habitSlots = 2,
            wantSlots = 3,
        )
        assertEquals(listOf("h1", "h2"), result.habits.map { it.habitId })
        assertEquals(listOf("w1", "w2", "w3"), result.wants.map { it.activityId })
    }

    @Test
    fun `fewer items than slots returns all of them`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Run")),
            wants = listOf(want("w1", "A")),
            balance = 10,
            rate = 1.0,
            habitSlots = 3,
            wantSlots = 3,
        )
        assertEquals(1, result.habits.size)
        assertEquals(1, result.wants.size)
    }

    @Test
    fun `hidden wants are excluded before truncation`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "A", hidden = true), want("w2", "B"), want("w3", "C")),
            balance = 10,
            rate = 1.0,
            habitSlots = 0,
            wantSlots = 2,
        )
        assertEquals(listOf("w2", "w3"), result.wants.map { it.activityId })
    }

    @Test
    fun `logQuantity is exactly one point worth of the habit unit`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Read", thresholdPerPoint = 0.7, dailyTarget = 3)),
            wants = emptyList(),
            balance = 0,
            rate = 1.0,
            habitSlots = 1,
            wantSlots = 0,
        )
        val logged = result.habits.single().logQuantity
        assertEquals(1, PointCalculator.pointsEarned(logged, 0.7))
    }

    @Test
    fun `logQuantity survives a repeating threshold`() {
        val third = 1.0 / 3.0
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Water", thresholdPerPoint = third, dailyTarget = 3)),
            wants = emptyList(),
            balance = 0,
            rate = 1.0,
            habitSlots = 1,
            wantSlots = 0,
        )
        assertEquals(1, PointCalculator.pointsEarned(result.habits.single().logQuantity, third))
    }

    @Test
    fun `progressText comes from HabitWithProgress`() {
        val result = WidgetItemSelector.select(
            habits = listOf(habit("h1", "Run", dailyTarget = 3, pointsToday = 2)),
            wants = emptyList(),
            balance = 0,
            rate = 1.0,
            habitSlots = 1,
            wantSlots = 0,
        )
        assertEquals("2 / 3", result.habits.single().progressText)
        assertFalse(result.habits.single().isGoalMet)
    }

    @Test
    fun `rateText scales units per point by the exchange rate`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube", unit = "min", unitsPerPoint = 10)),
            balance = 5,
            rate = 2.0,
            habitSlots = 0,
            wantSlots = 1,
        )
        // effectiveUnitsPerPoint(10, 2.0) == 5
        assertEquals("5 min", result.wants.single().rateText)
    }

    @Test
    fun `isTimed is true only for the min unit`() {
        val result = WidgetItemSelector.select(
            habits = emptyList(),
            wants = listOf(want("w1", "YouTube", unit = "min"), want("w2", "Soda", unit = "cup")),
            balance = 5,
            rate = 1.0,
            habitSlots = 0,
            wantSlots = 2,
        )
        assertTrue(result.wants[0].isTimed)
        assertFalse(result.wants[1].isTimed)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
rtk proxy ./gradlew :shared:allTests --tests "com.habittracker.domain.usecase.WidgetItemSelectorTest"
```

Expected: FAIL — `Unresolved reference: WidgetItemSelector`.

- [ ] **Step 3: Write `WidgetItemSelector.kt`**

Create `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/WidgetItemSelector.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity
import kotlin.math.nextUp

/** One habit as a widget renders it. */
data class WidgetHabitItem(
    val habitId: String,
    val name: String,
    val progressText: String,
    val isGoalMet: Boolean,
    val logQuantity: Double,
)

/** One want as a widget renders it. */
data class WidgetWantItem(
    val activityId: String,
    val name: String,
    val rateText: String,
    val isTimed: Boolean,
    val enabled: Boolean,
)

data class WidgetItems(
    val habits: List<WidgetHabitItem>,
    val wants: List<WidgetWantItem>,
)

data class WidgetData(
    val balance: Int,
    val currentStreak: Int,
    val items: WidgetItems,
)

/**
 * Turns domain models into the rows a widget can draw, applying the two rules that
 * are easy to get wrong: how many items fit, and which wants are affordable.
 *
 * Affordability is uniform. PointCalculator.pointsSpent(taps) = taps, so every want
 * costs exactly one point per tap regardless of unitsPerPoint — which sets how many
 * units a point buys, not what it costs. This mirrors the gate WantTimerController
 * already enforces: `if (balance <= 0) throw InsufficientPointsException`.
 */
object WidgetItemSelector {

    /** Wants measured in this unit run on a timer instead of logging instantly. */
    const val MINUTE_UNIT: String = "min"

    fun select(
        habits: List<HabitWithProgress>,
        wants: List<WantActivity>,
        balance: Int,
        rate: Double,
        habitSlots: Int,
        wantSlots: Int,
    ): WidgetItems {
        val habitItems = habits
            .take(habitSlots.coerceAtLeast(0))
            .map { hp ->
                WidgetHabitItem(
                    habitId = hp.habit.id,
                    name = hp.habit.name,
                    progressText = hp.progressText,
                    isGoalMet = hp.isGoalMet,
                    // ponytail: one tap = one point. thresholdPerPoint can land a hair under
                    // the true value once it round-trips through a Double (e.g. 1/3 stored as
                    // 0.3333333333333333), which then floors to zero points in
                    // PointCalculator.pointsEarned. nextUp() nudges by a single ULP to clear
                    // that noise without perturbing the logged quantity. Carried over from the
                    // v1 HabitWidget fix, which applied it to dailyTarget * thresholdPerPoint.
                    logQuantity = hp.habit.thresholdPerPoint.nextUp(),
                )
            }

        val affordable = balance > 0
        val wantItems = wants
            .filter { it.hiddenAt == null }
            .take(wantSlots.coerceAtLeast(0))
            .map { want ->
                val units = PointCalculator.effectiveUnitsPerPoint(want.unitsPerPoint, rate)
                WidgetWantItem(
                    activityId = want.id,
                    name = want.name,
                    rateText = "$units ${want.unit}",
                    isTimed = want.unit == MINUTE_UNIT,
                    enabled = affordable,
                )
            }

        return WidgetItems(habits = habitItems, wants = wantItems)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
rtk proxy ./gradlew :shared:allTests --tests "com.habittracker.domain.usecase.WidgetItemSelectorTest"
```

Expected: PASS, 11 tests.

- [ ] **Step 5: Write `GetWidgetDataUseCase.kt`**

Create `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetWidgetDataUseCase.kt`:

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository

/**
 * The single data entry point for every home-screen widget. Widgets call this once
 * from provideGlance and render the result — they never assemble domain data.
 *
 * Slot counts differ per widget and per size, so they are passed in rather than
 * baked in here.
 */
class GetWidgetDataUseCase(
    private val getTodayHabitsUseCase: GetTodayHabitsUseCase,
    private val wantActivityRepository: WantActivityRepository,
    private val getPointBalanceUseCase: GetPointBalanceUseCase,
    private val computeStreakUseCase: ComputeStreakUseCase,
) {
    suspend fun execute(userId: String, habitSlots: Int, wantSlots: Int): WidgetData {
        val habits = getTodayHabitsUseCase.execute(userId)
        val wants = wantActivityRepository.getWantActivities(userId)
        val balance = getPointBalanceUseCase.execute(userId).getOrNull()?.balance ?: 0
        val currentStreak = computeStreakUseCase.computeSummaryNow(userId).currentStreak
        val rate = ExchangeRateCalculator.rateFor(currentStreak)
        return WidgetData(
            balance = balance,
            currentStreak = currentStreak,
            items = WidgetItemSelector.select(
                habits = habits,
                wants = wants,
                balance = balance,
                rate = rate,
                habitSlots = habitSlots,
                wantSlots = wantSlots,
            ),
        )
    }
}
```

- [ ] **Step 6: Wire it into `AppContainer`**

In `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`, add the import and a property directly after the existing `logWantUseCase` declaration block (which starts at line 150):

```kotlin
import com.habittracker.domain.usecase.GetWidgetDataUseCase
```

```kotlin
    val getWidgetDataUseCase = GetWidgetDataUseCase(
        getTodayHabitsUseCase = getTodayHabitsUseCase,
        wantActivityRepository = wantActivityRepository,
        getPointBalanceUseCase = getPointBalanceUseCase,
        computeStreakUseCase = computeStreakUseCase,
    )
```

- [ ] **Step 7: Verify the app still compiles**

```bash
rtk proxy ./gradlew :androidApp:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/WidgetItemSelector.kt mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetWidgetDataUseCase.kt mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/WidgetItemSelectorTest.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt
rtk git commit -m "$(cat <<'EOF'
feat(widget): add shared widget item selector and data use case

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Four widget scaffolds replacing HabitWidget

Every later task fills in one widget's content. This task makes all four pinnable and near-empty, deletes v1, and creates the fan-out point Task 4's actions need.

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/WidgetComponents.kt`
- Create: `.../widget/WidgetUpdates.kt`
- Create: `.../widget/BalanceWidget.kt`, `.../widget/BalanceWidgetReceiver.kt`
- Create: `.../widget/QuickLogListWidget.kt`, `.../widget/QuickLogListWidgetReceiver.kt`
- Create: `.../widget/QuickLogGridWidget.kt`, `.../widget/QuickLogGridWidgetReceiver.kt`
- Create: `.../widget/StreakWidget.kt`, `.../widget/StreakWidgetReceiver.kt`
- Create: `mobile/androidApp/src/androidMain/res/xml/balance_widget_info.xml`, `quick_log_list_widget_info.xml`, `quick_log_grid_widget_info.xml`, `streak_widget_info.xml`
- Modify: `mobile/androidApp/src/androidMain/res/values/strings.xml`
- Modify: `mobile/androidApp/src/androidMain/AndroidManifest.xml` (replace the `.widget.HabitWidgetReceiver` block at line 48)
- Modify: `.../widget/LogHabitAction.kt`
- Delete: `.../widget/HabitWidget.kt`, `.../widget/HabitWidgetReceiver.kt`, `res/xml/habit_widget_info.xml`

**Interfaces:**
- Consumes: `AppContainer.getWidgetDataUseCase`, `WidgetData` (Task 1); `HabitTrackerApplication.container`; `AppContainer.currentUserId()`; `MainActivity`; `LightColorScheme` / `DarkColorScheme` from `com.jktdeveloper.habitto.ui.theme`.
- Produces: `WidgetSurface(modifier, content)`, `WidgetEmpty(message)`; `WidgetUpdates.updateAll(context)`; `BalanceWidget` (`MIN_SIZE`, `EXPANDED_SIZE`), `QuickLogListWidget`, `QuickLogGridWidget`, `StreakWidget`, each with a `MIN_SIZE`/`EXPANDED_SIZE` companion. Tasks 3–7 consume these.

- [ ] **Step 1: Create `WidgetComponents.kt`**

Only what every widget needs lands now; rows, tiles and heat colors arrive in Tasks 4–6.

```kotlin
package com.jktdeveloper.habitto.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jktdeveloper.habitto.MainActivity

/**
 * The card every widget draws inside.
 *
 * ponytail: cornerRadius is API 31+ and a silent no-op on 26–30, where the widget
 * renders square. Accepted — most launchers mask widget corners themselves. Upgrade
 * path if it looks wrong on a real API-28 device: a shape drawable in res/drawable
 * plus res/drawable-night, applied via background(ImageProvider(...)).
 */
@Composable
fun WidgetSurface(
    modifier: GlanceModifier = GlanceModifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        content()
    }
}

/** Shown when a widget has nothing to render. Tapping it opens the app. */
@Composable
fun WidgetEmpty(message: String) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
        )
    }
}
```

- [ ] **Step 2: Create `WidgetUpdates.kt`**

```kotlin
package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Single fan-out point for widget refreshes. Every source — tap actions, the
 * WidgetRefresher Flow collector, the want-timer minute tick — calls this rather
 * than listing the four widgets itself.
 */
object WidgetUpdates {
    suspend fun updateAll(context: Context) {
        BalanceWidget().updateAll(context)
        QuickLogListWidget().updateAll(context)
        QuickLogGridWidget().updateAll(context)
        StreakWidget().updateAll(context)
    }
}
```

- [ ] **Step 3: Create `BalanceWidget.kt`**

This is the shape all four follow. Only `sizeMode`, the slot counts, and the placeholder body differ.

```kotlin
package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.material3.ColorProviders
import com.habittracker.domain.usecase.WidgetData
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

class BalanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 0,
            wantSlots = 0,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                WidgetSurface {
                    WidgetEmpty("${data.balance} pts")
                }
            }
        }
    }

    companion object {
        val MIN_SIZE = DpSize(110.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 110.dp)
    }
}
```

- [ ] **Step 4: Create the other three widget scaffolds**

All three carry the same import block as `BalanceWidget.kt` in Step 3.

`QuickLogListWidget.kt`:

```kotlin
package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.material3.ColorProviders
import com.habittracker.domain.usecase.WidgetData
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

class QuickLogListWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 2,
            wantSlots = 3,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                WidgetSurface {
                    WidgetEmpty("Quick log")
                }
            }
        }
    }

    companion object {
        val MIN_SIZE = DpSize(250.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 320.dp)
    }
}
```

`QuickLogGridWidget.kt` — same package and import block, differing in class name, slot counts and companion:

```kotlin
class QuickLogGridWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 3,
            wantSlots = 3,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                WidgetSurface {
                    WidgetEmpty("Quick log")
                }
            }
        }
    }

    companion object {
        val MIN_SIZE = DpSize(250.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 250.dp)
    }
}
```

`StreakWidget.kt`:

```kotlin
class StreakWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MIN_SIZE, EXPANDED_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val data: WidgetData = container.getWidgetDataUseCase.execute(
            userId = container.currentUserId(),
            habitSlots = 0,
            wantSlots = 0,
        )
        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                WidgetSurface {
                    WidgetEmpty("${data.currentStreak} day streak")
                }
            }
        }
    }

    companion object {
        val MIN_SIZE = DpSize(250.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 180.dp)
    }
}
```

`data` is deliberately unused in the two quick-log scaffolds — Tasks 4 and 5 render it. If the compiler warns, leave the call in place; deleting it only forces the next task to re-add it.

- [ ] **Step 5: Create the four receivers**

`BalanceWidgetReceiver.kt`:

```kotlin
package com.jktdeveloper.habitto.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class BalanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BalanceWidget()
}
```

`QuickLogListWidgetReceiver.kt`, `QuickLogGridWidgetReceiver.kt`, `StreakWidgetReceiver.kt` — same package and imports:

```kotlin
class QuickLogListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogListWidget()
}
```

```kotlin
class QuickLogGridWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickLogGridWidget()
}
```

```kotlin
class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
```

- [ ] **Step 6: Create the four widget-info XMLs**

`res/xml/balance_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:description="@string/balance_widget_description"
    android:initialLayout="@layout/widget_loading"
    android:widgetCategory="home_screen" />
```

`res/xml/quick_log_list_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:description="@string/quick_log_list_widget_description"
    android:initialLayout="@layout/widget_loading"
    android:widgetCategory="home_screen" />
```

`res/xml/quick_log_grid_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:description="@string/quick_log_grid_widget_description"
    android:initialLayout="@layout/widget_loading"
    android:widgetCategory="home_screen" />
```

`res/xml/streak_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:description="@string/streak_widget_description"
    android:initialLayout="@layout/widget_loading"
    android:widgetCategory="home_screen" />
```

`@layout/widget_loading` is the existing v1 placeholder layout (`res/layout/widget_loading.xml`) — keep it, all four reuse it, and it is the only reason a freshly-pinned widget is not a blank frame before `provideGlance` returns.

- [ ] **Step 7: Update strings**

In `res/values/strings.xml`, delete the `habit_widget_description` line and add:

```xml
    <string name="balance_widget_description">Point balance</string>
    <string name="quick_log_list_widget_description">Quick log — list</string>
    <string name="quick_log_grid_widget_description">Quick log — grid</string>
    <string name="streak_widget_description">Streak history</string>
```

- [ ] **Step 8: Update the manifest**

In `AndroidManifest.xml`, replace the `.widget.HabitWidgetReceiver` receiver block (line 48) with four blocks.

`android:exported="true"` is required, not a copy-paste habit: an app-widget provider receives `APPWIDGET_UPDATE` from the system's AppWidget host, which is a different process. `exported="false"` compiles, ships, and then the widget never updates. The shipped v1 receiver is exported with an `android:label`; the four below keep both.

```xml
        <receiver
            android:name=".widget.BalanceWidgetReceiver"
            android:exported="true"
            android:label="@string/balance_widget_description">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/balance_widget_info" />
        </receiver>

        <receiver
            android:name=".widget.QuickLogListWidgetReceiver"
            android:exported="true"
            android:label="@string/quick_log_list_widget_description">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/quick_log_list_widget_info" />
        </receiver>

        <receiver
            android:name=".widget.QuickLogGridWidgetReceiver"
            android:exported="true"
            android:label="@string/quick_log_grid_widget_description">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/quick_log_grid_widget_info" />
        </receiver>

        <receiver
            android:name=".widget.StreakWidgetReceiver"
            android:exported="true"
            android:label="@string/streak_widget_description">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/streak_widget_info" />
        </receiver>
```

- [ ] **Step 9: Repoint `LogHabitAction` and delete v1**

In `LogHabitAction.kt`, replace the last statement of `onAction`, `HabitWidget().update(context, glanceId)`, with:

```kotlin
        WidgetUpdates.updateAll(context)
```

Remove the now-unused `HabitWidget` import. Keep the `glanceId` parameter — it is part of the `ActionCallback` interface.

Then delete the three v1 files:

```bash
rtk proxy rm mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/HabitWidget.kt \
  mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/HabitWidgetReceiver.kt \
  mobile/androidApp/src/androidMain/res/xml/habit_widget_info.xml
```

- [ ] **Step 10: Build and confirm v1 is fully gone**

```bash
rtk proxy ./gradlew :androidApp:assembleDebug && rtk proxy grep -rn "HabitWidget" mobile/androidApp/src
```

Expected: BUILD SUCCESSFUL, and the grep prints nothing (exit 1 is the expected outcome for a clean removal).

- [ ] **Step 11: Commit**

```bash
rtk git add -A mobile/androidApp/src/androidMain
rtk git commit -m "$(cat <<'EOF'
feat(widget): scaffold four Glance widgets, replacing HabitWidget

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Balance widget content

**Files:**
- Modify: `.../widget/WidgetComponents.kt` (add `BalanceHeader`)
- Modify: `.../widget/BalanceWidget.kt`

**Interfaces:**
- Consumes: `WidgetData.balance`, `WidgetData.currentStreak`, `WidgetSurface`, `BalanceWidget.MIN_SIZE`.
- Produces: `BalanceHeader(balance: Int, currentStreak: Int, compact: Boolean = false)` — Tasks 4 and 5 reuse it as their header.

- [ ] **Step 1: Add `BalanceHeader` to `WidgetComponents.kt`**

```kotlin
/**
 * Balance numeral plus streak flame. `compact` drops to a single small line for the
 * quick-log widgets, which need the vertical space for rows.
 *
 * The flame is an emoji rendered as text — Glance cannot draw the app's ImageVector
 * icons, and text sidesteps that entirely.
 */
@Composable
fun BalanceHeader(balance: Int, currentStreak: Int, compact: Boolean = false) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            balance.toString(),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (compact) 20.sp else 40.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            " pts",
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (compact) 12.sp else 14.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (currentStreak > 0) {
            Text(
                "🔥 $currentStreak",
                style = TextStyle(
                    color = ColorProvider(day = FlameOrange, night = FlameOrangeDark),
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}
```

Add these imports to `WidgetComponents.kt`: `androidx.glance.layout.Row`, `androidx.glance.layout.fillMaxWidth`, `androidx.glance.text.FontWeight`, `androidx.glance.unit.ColorProvider`, `com.jktdeveloper.habitto.ui.theme.FlameOrange`, `com.jktdeveloper.habitto.ui.theme.FlameOrangeDark`.

- [ ] **Step 2: Render it in `BalanceWidget`**

Replace the `WidgetSurface { WidgetEmpty(...) }` body inside `provideContent`:

```kotlin
                val compactWidth = LocalSize.current.width <= MIN_SIZE.width
                WidgetSurface {
                    if (compactWidth) {
                        // 110dp cannot hold numeral, unit and flame on one line — stack them.
                        Text(
                            data.balance.toString(),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Text(
                            "pts",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp,
                            ),
                        )
                        if (data.currentStreak > 0) {
                            Text(
                                "🔥 ${data.currentStreak}",
                                style = TextStyle(
                                    color = ColorProvider(day = FlameOrange, night = FlameOrangeDark),
                                    fontSize = 13.sp,
                                ),
                            )
                        }
                    } else {
                        BalanceHeader(balance = data.balance, currentStreak = data.currentStreak)
                    }
                }
```

`WidgetSurface` is already a `Column`, so the stacked branch needs no extra wrapper.

Imports to add to `BalanceWidget.kt`: `androidx.compose.ui.unit.sp`, `androidx.glance.LocalSize`, `androidx.glance.text.FontWeight`, `androidx.glance.text.Text`, `androidx.glance.text.TextStyle`, `androidx.glance.unit.ColorProvider`, `com.jktdeveloper.habitto.ui.theme.FlameOrange`, `com.jktdeveloper.habitto.ui.theme.FlameOrangeDark`.

There is no empty state here: `0 pts` is the correct render at zero balance, and the header already taps through to the app.

- [ ] **Step 3: Build**

```bash
rtk proxy ./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget
rtk git commit -m "$(cat <<'EOF'
feat(widget): render balance widget at both sizes

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Quick-log list widget and want actions

**Files:**
- Create: `.../widget/LogWantAction.kt`
- Modify: `.../widget/WidgetComponents.kt` (add `wantTimerIntent`, `HabitRow`, `WantRow`)
- Modify: `.../widget/QuickLogListWidget.kt`

**Interfaces:**
- Consumes: `WidgetHabitItem`, `WidgetWantItem` (Task 1); `BalanceHeader` (Task 3); `WidgetSurface`, `WidgetEmpty`, `WidgetUpdates.updateAll` (Task 2); `LogHabitAction.habitIdKey`, `LogHabitAction.quantityKey`; `LogWantUseCase.execute(userId: String, activityId: String, taps: Int, deviceMode: DeviceMode): Result<LogWantResult>`; `DeviceMode.OTHER`.
- Produces: `LogWantAction.activityIdKey`; `wantTimerIntent(context: Context, activityId: String): Intent`; `HabitRow(item: WidgetHabitItem)`; `WantRow(item: WidgetWantItem)`. Task 5 consumes all of these.

- [ ] **Step 1: Create `LogWantAction.kt`**

Instant (non-minute) wants log one tap directly. `DeviceMode.OTHER` matches what `HomeViewModel.kt:393` passes for an in-app instant want log; `DeviceMode.THIS_DEVICE` is reserved for the timer path (`WantTimerController.kt:71`).

```kotlin
package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.habittracker.domain.model.DeviceMode
import com.jktdeveloper.habitto.HabitTrackerApplication

class LogWantAction : ActionCallback {

    companion object {
        val activityIdKey = ActionParameters.Key<String>("activityId")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val activityId = parameters[activityIdKey] ?: return
        val container = (context.applicationContext as HabitTrackerApplication).container
        runCatching {
            container.logWantUseCase.execute(
                userId = container.currentUserId(),
                activityId = activityId,
                taps = 1,
                deviceMode = DeviceMode.OTHER,
            )
        }
        WidgetUpdates.updateAll(context)
    }
}
```

`LogWantUseCase.execute` returns a `Result` and already refuses an unaffordable spend, so a stale widget tapped at zero balance fails safely; `runCatching` covers the DB/suspend boundary above that.

- [ ] **Step 2: Add the deep link and the two rows to `WidgetComponents.kt`**

```kotlin
/** Deep link to the want-timer screen; the intent-filter is AndroidManifest.xml:36. */
fun wantTimerIntent(context: Context, activityId: String): Intent =
    Intent(
        Intent.ACTION_VIEW,
        Uri.parse("com.jktdeveloper.habitto://want-timer/$activityId"),
    ).apply {
        setPackage(context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

/** One habit. Tapping logs a single point. 48dp tall to meet the tap-target floor. */
@Composable
fun HabitRow(item: WidgetHabitItem) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(
                actionRunCallback<LogHabitAction>(
                    actionParametersOf(
                        LogHabitAction.habitIdKey to item.habitId,
                        LogHabitAction.quantityKey to item.logQuantity,
                    ),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            item.progressText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (item.isGoalMet) FontWeight.Bold else FontWeight.Normal,
            ),
        )
        Text(
            "  +1",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

/**
 * One want. A minute-unit want opens the want-timer screen — the widget has no
 * sensible way to invent a duration, and an unattended drain started by a mis-tap is
 * not recoverable from the home screen.
 *
 * Disabled state is a dimmer color, not opacity: Glance has no opacity modifier on a
 * Row. An unaffordable want still taps through to the app rather than going inert.
 */
@Composable
fun WantRow(item: WidgetWantItem) {
    val context = LocalContext.current
    val action = when {
        !item.enabled -> actionStartActivity<MainActivity>()
        item.isTimed -> actionStartActivity(wantTimerIntent(context, item.activityId))
        else -> actionRunCallback<LogWantAction>(
            actionParametersOf(LogWantAction.activityIdKey to item.activityId),
        )
    }
    val nameColor =
        if (item.enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            item.name,
            maxLines = 1,
            style = TextStyle(color = nameColor, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            item.rateText,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
        Text(
            if (item.enabled) "  −1 pt" else "  no pts",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}
```

Imports to add: `android.content.Context`, `android.content.Intent`, `android.net.Uri`, `androidx.glance.LocalContext`, `androidx.glance.action.actionParametersOf`, `androidx.glance.appwidget.action.actionRunCallback`, `androidx.glance.layout.height`, `com.habittracker.domain.usecase.WidgetHabitItem`, `com.habittracker.domain.usecase.WidgetWantItem`.

- [ ] **Step 3: Render `QuickLogListWidget`**

Replace the `WidgetSurface { WidgetEmpty("Quick log") }` body inside `provideContent`:

```kotlin
                val expanded = LocalSize.current.height > MIN_SIZE.height
                WidgetSurface {
                    if (data.items.habits.isEmpty() && data.items.wants.isEmpty()) {
                        WidgetEmpty("No habits yet — open app")
                    } else {
                        BalanceHeader(
                            balance = data.balance,
                            currentStreak = data.currentStreak,
                            compact = true,
                        )
                        LazyColumn {
                            items(
                                items = data.items.habits,
                                itemId = { it.habitId.hashCode().toLong() },
                            ) { HabitRow(it) }
                            if (expanded && data.items.wants.isNotEmpty()) {
                                item(itemId = DIVIDER_ITEM_ID) {
                                    Box(
                                        modifier = GlanceModifier.fillMaxWidth().height(9.dp),
                                    ) {}
                                }
                                items(
                                    items = data.items.wants,
                                    itemId = { it.activityId.hashCode().toLong() },
                                ) { WantRow(it) }
                            }
                        }
                    }
                }
```

Add to the companion object: `private const val DIVIDER_ITEM_ID = -1L`.

Slot counts stay as scaffolded (`habitSlots = 2, wantSlots = 3`). At min size the `LazyColumn` scrolls rather than overflowing, so one habit row is visible; the expanded 4×5 (320dp) holds header 32 + two 48dp habit rows + 9dp divider + three 48dp want rows + 24dp padding = 305dp.

Imports to add: `androidx.compose.ui.unit.dp`, `androidx.glance.GlanceModifier`, `androidx.glance.LocalSize`, `androidx.glance.appwidget.lazy.LazyColumn`, `androidx.glance.appwidget.lazy.item`, `androidx.glance.appwidget.lazy.items`, `androidx.glance.layout.Box`, `androidx.glance.layout.fillMaxWidth`, `androidx.glance.layout.height`.

- [ ] **Step 4: Build**

```bash
rtk proxy ./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget
rtk git commit -m "$(cat <<'EOF'
feat(widget): quick-log list widget with habit and want rows

One tap logs one point instead of the full daily target, matching the
design. Minute-unit wants deep-link to the want-timer screen rather than
starting an unattended drain from the home screen.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: Quick-log grid widget

**Files:**
- Modify: `.../widget/WidgetComponents.kt` (add `GridTile`)
- Modify: `.../widget/QuickLogGridWidget.kt`

**Interfaces:**
- Consumes: everything Task 4 produced, plus `BalanceHeader` and the `WidgetData` shape.
- Produces: `GridTile(label: String, caption: String, enabled: Boolean, action: Action, modifier: GlanceModifier)`. Nothing later depends on it.

- [ ] **Step 1: Add `GridTile` to `WidgetComponents.kt`**

```kotlin
/**
 * One icon-sized tile. The glyph is the item name's first letter — Glance cannot
 * render the app's ImageVector icons, and a bare letter reads better at tile size
 * than a truncated name. See the plan's Global Constraints.
 */
@Composable
fun GridTile(
    label: String,
    caption: String,
    enabled: Boolean,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
) {
    val fg = if (enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant
    Column(
        modifier = modifier
            .height(72.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(12.dp)
            .padding(6.dp)
            .clickable(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.take(1).uppercase(),
            style = TextStyle(color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            label,
            maxLines = 1,
            style = TextStyle(color = fg, fontSize = 10.sp),
        )
        Text(
            caption,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
        )
    }
}
```

Import to add: `androidx.glance.action.Action`.

- [ ] **Step 2: Render `QuickLogGridWidget`**

Fixed rows of columns — Glance has no `LazyVerticalGrid`. Min shows one row of three tiles and no balance header (three 72dp tiles do not fit under a header in a 110dp cell). Expanded shows the compact header plus two rows of three.

Add above the class, in the same file:

```kotlin
private data class GridEntry(
    val label: String,
    val caption: String,
    val enabled: Boolean,
    val action: Action,
)
```

Replace the `WidgetSurface { WidgetEmpty("Quick log") }` body inside `provideContent`:

```kotlin
                val expanded = LocalSize.current.height > MIN_SIZE.height
                val context = LocalContext.current
                val tiles = buildList {
                    data.items.habits.forEach { h ->
                        add(
                            GridEntry(
                                label = h.name,
                                caption = "+1",
                                enabled = true,
                                action = actionRunCallback<LogHabitAction>(
                                    actionParametersOf(
                                        LogHabitAction.habitIdKey to h.habitId,
                                        LogHabitAction.quantityKey to h.logQuantity,
                                    ),
                                ),
                            ),
                        )
                    }
                    data.items.wants.forEach { w ->
                        add(
                            GridEntry(
                                label = w.name,
                                caption = if (w.enabled) "−1 pt" else "no pts",
                                enabled = w.enabled,
                                action = when {
                                    !w.enabled -> actionStartActivity<MainActivity>()
                                    w.isTimed -> actionStartActivity(
                                        wantTimerIntent(context, w.activityId),
                                    )
                                    else -> actionRunCallback<LogWantAction>(
                                        actionParametersOf(
                                            LogWantAction.activityIdKey to w.activityId,
                                        ),
                                    )
                                },
                            ),
                        )
                    }
                }
                WidgetSurface {
                    if (tiles.isEmpty()) {
                        WidgetEmpty("No habits yet — open app")
                    } else {
                        if (expanded) {
                            BalanceHeader(
                                balance = data.balance,
                                currentStreak = data.currentStreak,
                                compact = true,
                            )
                        }
                        repeat(if (expanded) 2 else 1) { rowIndex ->
                            Row(modifier = GlanceModifier.fillMaxWidth()) {
                                repeat(COLUMNS) { col ->
                                    val entry = tiles.getOrNull(rowIndex * COLUMNS + col)
                                    if (entry == null) {
                                        Box(modifier = GlanceModifier.defaultWeight()) {}
                                    } else {
                                        GridTile(
                                            label = entry.label,
                                            caption = entry.caption,
                                            enabled = entry.enabled,
                                            action = entry.action,
                                            modifier = GlanceModifier
                                                .defaultWeight()
                                                .padding(2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
```

`LocalContext.current` is hoisted above `buildList` because `buildList`'s lambda is not `@Composable`.

Add to the companion object: `private const val COLUMNS = 3`.

Imports to add: `androidx.compose.ui.unit.dp`, `androidx.glance.GlanceModifier`, `androidx.glance.LocalContext`, `androidx.glance.LocalSize`, `androidx.glance.action.Action`, `androidx.glance.action.actionParametersOf`, `androidx.glance.action.actionStartActivity`, `androidx.glance.appwidget.action.actionRunCallback`, `androidx.glance.layout.Box`, `androidx.glance.layout.Row`, `androidx.glance.layout.fillMaxWidth`, `androidx.glance.layout.padding`, `com.jktdeveloper.habitto.MainActivity`.

- [ ] **Step 3: Build**

```bash
rtk proxy ./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget
rtk git commit -m "$(cat <<'EOF'
feat(widget): quick-log grid widget with fixed 3-column tiles

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: Streak widget

**Files:**
- Modify: `.../widget/WidgetComponents.kt` (add `heatColor`)
- Modify: `.../widget/StreakWidget.kt`

**Interfaces:**
- Consumes: `AppContainer.computeStreakUseCase`; `ComputeStreakUseCase.computeNow(userId: String, range: DateRange): StreakRangeResult`; `StreakRangeResult.days: List<StreakDay>`; `StreakDay(date, state, heatLevel)`; `StreakDayState` (`COMPLETE`, `FROZEN`, `BROKEN`, `EMPTY`, `TODAY_PENDING`, `FUTURE`); `DateRange(start, endExclusive)` — half-open; the heat colors in `com.jktdeveloper.habitto.ui.theme`.
- Produces: `heatColor(day: StreakDay): ColorProvider`. Nothing later depends on it.

- [ ] **Step 1: Add `heatColor` to `WidgetComponents.kt`**

```kotlin
/**
 * Heat-grid cell color, reusing the Phase 4 palette so the widget and the in-app
 * streak history read identically for the same day.
 */
fun heatColor(day: StreakDay): ColorProvider = when (day.state) {
    StreakDayState.FROZEN -> ColorProvider(day = StreakFrozen, night = StreakFrozenDark)
    StreakDayState.BROKEN -> ColorProvider(day = StreakBroken, night = StreakBrokenDark)
    StreakDayState.COMPLETE -> when (day.heatLevel) {
        1 -> ColorProvider(day = HeatL1, night = HeatL1Dark)
        2 -> ColorProvider(day = HeatL2, night = HeatL2Dark)
        3 -> ColorProvider(day = HeatL3, night = HeatL3Dark)
        else -> ColorProvider(day = HeatL4, night = HeatL4Dark)
    }
    StreakDayState.EMPTY,
    StreakDayState.TODAY_PENDING,
    StreakDayState.FUTURE,
    -> ColorProvider(day = HeatL0, night = HeatL0Dark)
}
```

Imports to add: `com.habittracker.domain.model.StreakDay`, `com.habittracker.domain.model.StreakDayState`, and from `com.jktdeveloper.habitto.ui.theme`: `HeatL0`, `HeatL0Dark`, `HeatL1`, `HeatL1Dark`, `HeatL2`, `HeatL2Dark`, `HeatL3`, `HeatL3Dark`, `HeatL4`, `HeatL4Dark`, `StreakFrozen`, `StreakFrozenDark`, `StreakBroken`, `StreakBrokenDark`.

- [ ] **Step 2: Render `StreakWidget`**

`GetWidgetDataUseCase` already supplies the streak count; the day cells need a second call because they are range-scoped. Fetch both in `provideGlance` before `provideContent`, and fetch the expanded range once so resizing does not require a reload.

```kotlin
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val userId = container.currentUserId()
        val data = container.getWidgetDataUseCase.execute(userId, habitSlots = 0, wantSlots = 0)
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val range = DateRange(
            start = today.minus(EXPANDED_DAYS - 1, DateTimeUnit.DAY),
            endExclusive = today.plus(1, DateTimeUnit.DAY),
        )
        val days = runCatching { container.computeStreakUseCase.computeNow(userId, range).days }
            .getOrDefault(emptyList())

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                val expanded = LocalSize.current.height > MIN_SIZE.height
                val columns = if (expanded) EXPANDED_COLUMNS else MIN_COLUMNS
                val visible = days.takeLast(if (expanded) EXPANDED_DAYS else MIN_DAYS)
                WidgetSurface {
                    if (visible.isEmpty()) {
                        WidgetEmpty("Start a streak")
                    } else {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .clickable(actionStartActivity<MainActivity>()),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "🔥 ${data.currentStreak}",
                                style = TextStyle(
                                    color = ColorProvider(day = FlameOrange, night = FlameOrangeDark),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Text(
                                " day streak",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                ),
                            )
                        }
                        visible.chunked(columns).forEach { week ->
                            Row(modifier = GlanceModifier.padding(top = 2.dp)) {
                                week.forEach { day ->
                                    Box(
                                        modifier = GlanceModifier
                                            .size(CELL_SIZE)
                                            .padding(1.dp)
                                            .background(heatColor(day))
                                            .cornerRadius(2.dp),
                                    ) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
```

Companion additions:

```kotlin
        private const val MIN_COLUMNS = 12
        private const val MIN_DAYS = 36
        private const val EXPANDED_COLUMNS = 15
        private const val EXPANDED_DAYS = 60
        private val CELL_SIZE = 14.dp
```

12 × 14dp = 168dp and 15 × 14dp = 210dp, both inside a 250dp cell minus 24dp padding. Cells render square below API 31, per the Global Constraints; at 14dp the difference is not legible.

Imports to add: `androidx.compose.ui.unit.sp`, `androidx.glance.GlanceModifier`, `androidx.glance.LocalSize`, `androidx.glance.action.actionStartActivity`, `androidx.glance.action.clickable`, `androidx.glance.appwidget.cornerRadius`, `androidx.glance.background`, `androidx.glance.layout.Alignment`, `androidx.glance.layout.Box`, `androidx.glance.layout.Row`, `androidx.glance.layout.fillMaxWidth`, `androidx.glance.layout.padding`, `androidx.glance.layout.size`, `androidx.glance.text.FontWeight`, `androidx.glance.text.Text`, `androidx.glance.text.TextStyle`, `androidx.glance.unit.ColorProvider`, `com.habittracker.domain.model.DateRange`, `com.jktdeveloper.habitto.MainActivity`, `com.jktdeveloper.habitto.ui.theme.FlameOrange`, `com.jktdeveloper.habitto.ui.theme.FlameOrangeDark`, `kotlinx.datetime.Clock`, `kotlinx.datetime.DateTimeUnit`, `kotlinx.datetime.TimeZone`, `kotlinx.datetime.minus`, `kotlinx.datetime.plus`, `kotlinx.datetime.toLocalDateTime`.

- [ ] **Step 3: Build**

```bash
rtk proxy ./gradlew :androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget
rtk git commit -m "$(cat <<'EOF'
feat(widget): streak widget with dense heat grid

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Event-driven refresh

The part most likely to be wrong and least visible in code review. Three sources, per the spec.

**Files:**
- Create: `.../widget/WidgetRefresher.kt`
- Modify: `.../HabitTrackerApplication.kt`
- Modify: `.../timer/WantTimerService.kt` (the `runUntilEnd()` tick loop; `pointsSpent` recompute is at line 97, the tick delay at line 110)

**Interfaces:**
- Consumes: `AppContainer.authState: StateFlow<AuthState>` (line 197); `AppContainer.habitLogRepository`, `wantLogRepository`, `habitRepository`, `wantActivityRepository`; `HabitLogRepository.observeAllActiveLogsForUser(userId: String): Flow<List<HabitLog>>`; `WantLogRepository.observeAllActiveLogsForUser(userId: String): Flow<List<WantLog>>`; `HabitRepository.observeHabitsForUser(userId: String): Flow<List<Habit>>`; `WantActivityRepository.observeWantActivities(userId: String): Flow<List<WantActivity>>`; `WidgetUpdates.updateAll(context)`.
- Produces: `WidgetRefresher(context, container, scope).start()`. Nothing later depends on it.

`GetPointBalanceUseCase` has no Flow variant — only `execute(userId): Result<PointBalance>` at line 41 — so the collector watches the two log Flows and lets `GetWidgetDataUseCase` re-derive the balance on each widget render. Do not add an `observe` method to `GetPointBalanceUseCase` for this.

- [ ] **Step 1: Create `WidgetRefresher.kt`**

```kotlin
package com.jktdeveloper.habitto.widget

import android.content.Context
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Refresh source (1): anything that writes to the DB.
 *
 * One collector at the point every mutation flows through, rather than an
 * updateAll() call at every mutation site — LogHabitAction, the log ViewModels,
 * timer start/cancel and exchange are all covered without individually calling it.
 *
 * This cannot cover live want-timer drain: points are only written when the timer
 * ends, so nothing here emits mid-run. That is refresh source (2), the minute tick
 * inside WantTimerService.
 */
class WidgetRefresher(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    fun start() {
        scope.launch {
            container.authState
                .map { it.userId }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    combine(
                        container.habitLogRepository.observeAllActiveLogsForUser(userId),
                        container.wantLogRepository.observeAllActiveLogsForUser(userId),
                        container.habitRepository.observeHabitsForUser(userId),
                        container.wantActivityRepository.observeWantActivities(userId),
                    ) { _, _, _, _ -> Unit }
                }
                // A single user action can touch several tables; coalesce the burst.
                .debounce(DEBOUNCE_MS)
                .collect {
                    runCatching { WidgetUpdates.updateAll(context) }
                }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
```

- [ ] **Step 2: Start it in `HabitTrackerApplication`**

In `onCreate()`, after the existing `GlobalScope.launch { runCatching { recovery.scanOnStart() } }` block:

```kotlin
        WidgetRefresher(
            context = this,
            container = container,
            scope = GlobalScope + Dispatchers.Default,
        ).start()
```

Add imports `com.jktdeveloper.habitto.widget.WidgetRefresher` and `kotlinx.coroutines.plus`. The class is already annotated `@OptIn(DelicateCoroutinesApi::class)`, and an Application-lifetime collector is exactly what `GlobalScope` is for here — the same justification the existing `WantTimerRecovery` launch already carries.

- [ ] **Step 3: Hook the want-timer minute tick**

In `WantTimerService.runUntilEnd()`, immediately after the notification is re-issued on each pass (`pointsSpent` is recomputed at line 97; the loop's delay is computed at line 110), add:

```kotlin
                runCatching { WidgetUpdates.updateAll(applicationContext) }
```

Add the import `com.jktdeveloper.habitto.widget.WidgetUpdates`. No new service, coroutine, or wakeup — the foreground service is already running and already ticking at the resolution the data has. Per-minute is correct because want cost is per-minute; a faster tick would render identical output.

- [ ] **Step 4: Build and run the shared test suite**

```bash
rtk proxy ./gradlew :androidApp:assembleDebug :shared:allTests
```

Expected: BUILD SUCCESSFUL, all shared tests pass.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto
rtk git commit -m "$(cat <<'EOF'
feat(widget): event-driven refresh via repository flows and timer tick

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Manual QA checklist

Glance widgets are not practically unit-testable and this codebase has no Compose screenshot tests, so the refresh model and every rendered state are verified by hand. Writing the checklist down makes the pass repeatable and puts the deviations on the record.

**Files:**
- Create: `docs/qa/2026-08-08-phase10-widgets-v2-qa.md`

**Interfaces:** none — documentation only.

- [ ] **Step 1: Write the checklist**

````markdown
# Phase 10 (revised) — Widget manual QA

Device: ______  Android version: ______  Date: ______

## Pinning
- [ ] All four widgets appear in the launcher's widget picker with their descriptions
- [ ] Each pins at its default size and renders without a blank frame
- [ ] Each resizes between min and expanded, and the layout changes rather than scaling

## Widget 1 — Balance
- [ ] 2×2: numeral, `pts`, flame stacked; readable at 110dp
- [ ] 4×2: single-row header
- [ ] Zero balance renders `0 pts`, not an empty state
- [ ] Tap anywhere opens the app
- [ ] Dark mode

## Widget 2 — Quick log (list)
- [ ] Min: balance header + habit row(s)
- [ ] Expanded: header, two habit rows, divider, three want rows
- [ ] Tapping a habit row logs exactly one point (not the full daily target) — verify against Home
- [ ] Tapping an instant want (`unit != "min"`) spends one point
- [ ] Tapping a minute want opens the want-timer screen for that want
- [ ] At zero balance every want reads unavailable and no want tap spends
- [ ] No habits and no wants → "No habits yet — open app"
- [ ] Dark mode

## Widget 3 — Quick log (grid)
- [ ] Min: one row of three tiles, no balance header
- [ ] Expanded: header + two rows of three
- [ ] Same tap behaviors as widget 2
- [ ] Dark mode

## Widget 4 — Streak
- [ ] Min: 36 cells at 12 columns
- [ ] Expanded: 60 cells at 15 columns
- [ ] Cell colors match the in-app streak history for the same days
- [ ] No history → "Start a streak"
- [ ] Dark mode

## Refresh model — the part most likely to be wrong
- [ ] Log a habit in-app; every pinned widget updates without waiting
- [ ] Log a want in-app; every pinned widget updates without waiting
- [ ] Start a want timer; the balance on the widgets drains once per minute while it runs
- [ ] Cancel a timer; widgets reflect the partial spend
- [ ] Force-stop the app, change nothing, wait: widgets reconcile within 30 minutes
- [ ] Cross midnight with a widget pinned: habit progress resets

## Known deviations from the mockup — verify they look acceptable, do not "fix"
- [ ] No icons anywhere. Glance cannot render the app's ImageVector icons; grid tiles
      show the item name's first letter instead.
- [ ] Corners are square below API 31 (`cornerRadius` is API 31+). Check on the oldest
      device available.
- [ ] Disabled wants are dimmed by color, not opacity — Glance has no opacity modifier.
- [ ] Every want reads `−1 pt`; there is no per-want cost in the domain model. The
      differentiator shown is the rate (`5 min`, `1 cup`).
- [ ] Items shown are the first N in the app's own order, not the most-used ones.
      Confirm the widget shows the same leading habits/wants as Home and the Want list.

## Behavior change from v1 to confirm deliberately
- [ ] A widget habit tap logs one point. v1 logged the full daily target in one tap.
````

- [ ] **Step 2: Commit**

```bash
rtk git add docs/qa/2026-08-08-phase10-widgets-v2-qa.md
rtk git commit -m "$(cat <<'EOF'
docs(qa): add phase 10 widget manual QA checklist

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 3: Run the checklist on a device and record the result**

Fill in the device/version/date header and tick every box. Any unticked box is a finding, not a pass. Commit the filled-in file.
