# Phase 10 — Home-screen widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an Android home-screen widget (Jetpack Glance) showing today's habits + point balance, with tap-to-log directly from the widget.

**Architecture:** New `com.jktdeveloper.habitto.widget` package in `androidApp` renders a `GlanceAppWidget` off the existing `AppContainer` singleton. A new shared KMP use case (`GetTodayHabitsUseCase`) is extracted from `HomeViewModel`'s inline today-progress calculation so both the Home screen and the widget compute "is this habit done today" identically.

**Tech Stack:** Jetpack Glance (`androidx.glance:glance-appwidget`, `androidx.glance:glance-material3`), existing KMP shared domain layer, existing `AppContainer` DI.

## Global Constraints

- minSdk 26, compileSdk 35, targetSdk 35 (from `mobile/androidApp/build.gradle.kts` — unchanged by this work).
- No widget-config screen, no multi-instance support — v1 shows all active habits for the current user, ordered same as Home (spec §Scope).
- Widget refresh: tap-triggered update + OS periodic floor only, no custom `WorkManager` job (spec §Approach).
- `dailyTarget` on `Habit` is a **points** cap, not a raw quantity — any "log the full day" tap must convert via `dailyTarget * thresholdPerPoint` before calling `LogHabitUseCase` (spec §Data flow, caught in spec self-review).
- No automated UI test for the widget or `LogHabitAction` — this codebase has no Compose/Glance screenshot tests anywhere; covered by manual QA (spec §Testing).
- Reuse `GlanceTheme`/`ColorProviders` from `androidx.glance:glance-material3` — don't hand-roll widget colors from `ui/theme/Color.kt`.

---

### Task 1: Extract `GetTodayHabitsUseCase` (shared KMP)

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetTodayHabitsUseCase.kt`
- Test: `mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetTodayHabitsUseCaseTest.kt`

**Interfaces:**
- Consumes: `HabitRepository.getHabitsForUser(userId: String): List<Habit>` (suspend), `HabitLogRepository.getAllActiveLogsForUser(userId: String): List<HabitLog>` (suspend), `PointCalculator.pointsEarned(quantity: Double, threshold: Double): Int`, `HabitWithProgress(habit: Habit, pointsToday: Int)` (has `isGoalMet`, `progressFraction`, `progressText` computed properties already).
- Produces: `class GetTodayHabitsUseCase(habitRepo: HabitRepository, habitLogRepo: HabitLogRepository, timeZone: TimeZone = TimeZone.currentSystemDefault(), clock: Clock = Clock.System)` with `suspend fun execute(userId: String): List<HabitWithProgress>`. Later tasks (2, 3) call this exact signature.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val TODAY = LocalDate(2026, 4, 22)
private val YESTERDAY = LocalDate(2026, 4, 21)

private fun makeClock(now: Instant): Clock = object : Clock {
    override fun now(): Instant = now
}

private fun at(date: LocalDate, hour: Int = 10): Instant =
    date.atStartOfDayIn(TimeZone.UTC) + hour.hours

class GetTodayHabitsUseCaseTest {
    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val userId = "user1"

    private fun useCase() = GetTodayHabitsUseCase(
        habitRepo, habitLogRepo,
        timeZone = TimeZone.UTC,
        clock = makeClock(at(TODAY, hour = 12)),
    )

    private fun habit(
        id: String,
        threshold: Double = 1.0,
        dailyTarget: Int = 3,
        effectiveTo: Instant? = null,
    ): Habit = Habit(
        id = id, userId = userId, templateId = "tpl", name = id, unit = "units",
        thresholdPerPoint = threshold, dailyTarget = dailyTarget,
        createdAt = at(TODAY), updatedAt = at(TODAY), effectiveTo = effectiveTo,
    )

    @Test
    fun `empty when user has no habits`() = runTest {
        assertEquals(emptyList(), useCase().execute(userId))
    }

    @Test
    fun `pointsToday sums only today's logs, excludes yesterday`() = runTest {
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 5))
        habitLogRepo.insertLog("l0", userId, "h1", 2.0, at(YESTERDAY))
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(TODAY, hour = 9))
        val result = useCase().execute(userId)
        assertEquals(1, result.size)
        assertEquals(3, result.single().pointsToday)
    }

    @Test
    fun `excludes soft-deleted habits`() = runTest {
        habitRepo.saveHabit(habit("h1", effectiveTo = at(TODAY, hour = 1)))
        habitRepo.saveHabit(habit("h2"))
        val result = useCase().execute(userId)
        assertEquals(listOf("h2"), result.map { it.habit.id })
    }

    @Test
    fun `isGoalMet reflects dailyTarget comparison`() = runTest {
        habitRepo.saveHabit(habit("h1", threshold = 1.0, dailyTarget = 3))
        habitLogRepo.insertLog("l1", userId, "h1", 3.0, at(TODAY, hour = 9))
        val result = useCase().execute(userId)
        assertTrue(result.single().isGoalMet)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.GetTodayHabitsUseCaseTest"`
Expected: FAIL — `GetTodayHabitsUseCase` unresolved reference (class doesn't exist yet).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.domain.model.HabitWithProgress
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Today's habits + points logged today, for the widget and Home screen alike. */
class GetTodayHabitsUseCase(
    private val habitRepo: HabitRepository,
    private val habitLogRepo: HabitLogRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    suspend fun execute(userId: String): List<HabitWithProgress> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val dayStart = today.atStartOfDayIn(timeZone)
        val dayEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

        val habits = habitRepo.getHabitsForUser(userId).filter { it.effectiveTo == null }
        val logs = habitLogRepo.getAllActiveLogsForUser(userId)

        return habits.map { habit ->
            val pointsToday = logs
                .filter { it.habitId == habit.id && it.loggedAt >= dayStart && it.loggedAt < dayEnd }
                .sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
            HabitWithProgress(habit, pointsToday)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :mobile:shared:testDebugUnitTest --tests "com.habittracker.domain.usecase.GetTodayHabitsUseCaseTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/usecase/GetTodayHabitsUseCase.kt mobile/shared/src/commonTest/kotlin/com/habittracker/domain/usecase/GetTodayHabitsUseCaseTest.kt
rtk git commit -m "feat(habits): add GetTodayHabitsUseCase"
```

---

### Task 2: Wire `GetTodayHabitsUseCase` into `AppContainer` and refactor `HomeViewModel`

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt:26-50` (imports), `:108` (use-case wiring, next to `computeStreakUseCase`)
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt:219-228`

**Interfaces:**
- Consumes: `GetTodayHabitsUseCase` from Task 1 (`execute(userId: String): List<HabitWithProgress>`).
- Produces: `container.getTodayHabitsUseCase` — Task 3/4 (widget) will call this same property.

- [ ] **Step 1: Add the use case to `AppContainer`**

In `AppContainer.kt`, add the import next to the other `domain.usecase` imports (after line 30, `import com.habittracker.domain.usecase.GetDayPointsUseCase`):

```kotlin
import com.habittracker.domain.usecase.GetTodayHabitsUseCase
```

Then add the wiring right after `computeStreakUseCase` (currently `AppContainer.kt:108`):

```kotlin
    val computeStreakUseCase = ComputeStreakUseCase(habitLogRepository, habitRepository)
    val getTodayHabitsUseCase = GetTodayHabitsUseCase(habitRepository, habitLogRepository)
```

- [ ] **Step 2: Replace the inline computation in `HomeViewModel`**

In `HomeViewModel.kt`, find this block (currently lines 219-228):

```kotlin
                            val habitsWithProgress = habits.map { habit ->
                                val pointsToday = habitLogs
                                    .filter {
                                        it.habitId == habit.id && it.loggedAt >= dayStart && it.loggedAt < dayEnd
                                    }
                                    .sumOf {
                                        PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
                                    }
                                HabitWithProgress(habit, pointsToday)
                            }
```

Replace it with:

```kotlin
                            val habitsWithProgress = container.getTodayHabitsUseCase.execute(userId)
```

Leave the `habits` and `habitsById` values above this line untouched — they're still used by the weekly `earned`/`spent` calculations further down in the same block. `combine`'s transform lambda is already `suspend`, so calling `execute()` here needs no other change.

- [ ] **Step 3: Build and run the existing shared + androidApp unit test suites**

Run: `./gradlew :mobile:shared:testDebugUnitTest :mobile:androidApp:testDebugUnitTest`
Expected: PASS, no regressions. (No dedicated `HomeViewModel` test exists in this codebase today, so this step is a compile + regression check, not new coverage — the behavior itself is exercised via Task 5's manual QA.)

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt
rtk git commit -m "refactor(home): use GetTodayHabitsUseCase instead of inline calc"
```

---

### Task 3: Glance widget core — `HabitWidget` + `LogHabitAction`

**Files:**
- Modify: `gradle/libs.versions.toml` (add `glance` version + `glance-appwidget`/`glance-material3` libraries)
- Modify: `mobile/androidApp/build.gradle.kts:55-73` (add the two new `implementation(...)` lines)
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/HabitWidget.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/LogHabitAction.kt`

**Interfaces:**
- Consumes: `container.getTodayHabitsUseCase.execute(userId): List<HabitWithProgress>` (Task 1/2), `container.getPointBalanceUseCase.execute(userId): Result<PointBalance>` (existing), `container.logHabitUseCase.execute(userId, habitId, quantity): Result<LogHabitResult>` (existing), `container.currentUserId(): String` (existing), `(context.applicationContext as HabitTrackerApplication).container` (existing DI pattern).
- Produces: `class HabitWidget : GlanceAppWidget()` and `class LogHabitAction : ActionCallback` with `companion object { val habitIdKey: ActionParameters.Key<String>; val quantityKey: ActionParameters.Key<Double> }`. Task 4 (`HabitWidgetReceiver`) references `HabitWidget` directly.

- [ ] **Step 1: Add the Glance dependency**

In `gradle/libs.versions.toml`, add to `[versions]` (near `compose-bom`):

```toml
glance = "1.1.1"
```

Add to `[libraries]` (near the other `compose-*` entries):

```toml
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
```

In `mobile/androidApp/build.gradle.kts`, add inside the `dependencies { ... }` block (next to the other `compose-*` implementations, around line 61):

```kotlin
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
```

- [ ] **Step 2: Verify the dependency resolves**

Run: `./gradlew :mobile:androidApp:dependencies --configuration debugRuntimeClasspath | grep glance`
Expected: `androidx.glance:glance-appwidget` and `androidx.glance:glance-material3` listed, no resolution errors.

- [ ] **Step 3: Write `HabitWidget.kt`**

```kotlin
package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.GlanceLazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.PointBalance
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.ui.theme.DarkColorScheme
import com.jktdeveloper.habitto.ui.theme.LightColorScheme

class HabitWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as HabitTrackerApplication).container
        val userId = container.currentUserId()
        val habits = container.getTodayHabitsUseCase.execute(userId)
        val balance = container.getPointBalanceUseCase.execute(userId)
            .getOrDefault(PointBalance(0, 0, 0))

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                WidgetContent(habits = habits, balance = balance.balance)
            }
        }
    }
}

@Composable
private fun WidgetContent(habits: List<HabitWithProgress>, balance: Int) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            "$balance pts",
            style = TextStyle(fontWeight = FontWeight.Bold),
        )
        if (habits.isEmpty()) {
            Text("No habits yet — open app")
        } else {
            GlanceLazyColumn {
                items(habits, itemId = { it.habit.id.hashCode().toLong() }) { hp ->
                    HabitRow(hp)
                }
            }
        }
    }
}

@Composable
private fun HabitRow(hp: HabitWithProgress) {
    val habit = hp.habit
    val logQuantity = habit.dailyTarget * habit.thresholdPerPoint
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(
                actionRunCallback<LogHabitAction>(
                    actionParametersOf(
                        LogHabitAction.habitIdKey to habit.id,
                        LogHabitAction.quantityKey to logQuantity,
                    )
                )
            ),
    ) {
        Text(habit.name, modifier = GlanceModifier.defaultWeight())
        Text(hp.progressText)
    }
}
```

- [ ] **Step 4: Write `LogHabitAction.kt`**

```kotlin
package com.jktdeveloper.habitto.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.jktdeveloper.habitto.HabitTrackerApplication

class LogHabitAction : ActionCallback {
    companion object {
        val habitIdKey = ActionParameters.Key<String>("habitId")
        val quantityKey = ActionParameters.Key<Double>("quantity")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[habitIdKey] ?: return
        val quantity = parameters[quantityKey] ?: return
        val container = (context.applicationContext as HabitTrackerApplication).container

        // System-triggered callback with no in-widget error UI — swallow failures,
        // the next periodic refresh reconciles state (spec: Error handling).
        runCatching {
            container.logHabitUseCase.execute(container.currentUserId(), habitId, quantity)
        }

        HabitWidget().update(context, glanceId)
    }
}
```

- [ ] **Step 5: Compile check**

Run: `./gradlew :mobile:androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (No widget-info/manifest registration yet, so nothing renders on-device until Task 4 — this step only confirms the Kotlin compiles against the Glance APIs.)

- [ ] **Step 6: Commit**

```bash
rtk git add gradle/libs.versions.toml mobile/androidApp/build.gradle.kts mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/HabitWidget.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/LogHabitAction.kt
rtk git commit -m "feat(widget): add Glance HabitWidget with tap-to-log"
```

---

### Task 4: Register the widget — receiver, provider metadata, manifest

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/HabitWidgetReceiver.kt`
- Create: `mobile/androidApp/src/androidMain/res/xml/habit_widget_info.xml`
- Create: `mobile/androidApp/src/androidMain/res/layout/widget_loading.xml`
- Create: `mobile/androidApp/src/androidMain/res/values/strings.xml` (doesn't exist yet in this module)
- Modify: `mobile/androidApp/src/androidMain/AndroidManifest.xml` (add `<receiver>` inside `<application>`, after the existing `<service>` block)

**Interfaces:**
- Consumes: `HabitWidget` (Task 3).
- Produces: nothing further consumed by other tasks — this is the last piece needed for the widget to be pinnable on a device home screen.

- [ ] **Step 1: Write `HabitWidgetReceiver.kt`**

```kotlin
package com.jktdeveloper.habitto.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
```

- [ ] **Step 2: Write `res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="habit_widget_description">Today\'s habits</string>
</resources>
```

- [ ] **Step 3: Write `res/layout/widget_loading.xml`**

`android:initialLayout` on an `appwidget-provider` is mandatory and must reference a real layout — Glance replaces it after the first `provideGlance()` call, so this only needs to be a harmless placeholder:

```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:text="@string/habit_widget_description" />
```

- [ ] **Step 4: Write `res/xml/habit_widget_info.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="1800000"
    android:description="@string/habit_widget_description"
    android:initialLayout="@layout/widget_loading" />
```

(`updatePeriodMillis` of 30 minutes is the spec's periodic-refresh fallback — Android silently floors any lower value to ~30 min anyway, so this is both the requested value and the practical minimum.)

- [ ] **Step 5: Register the receiver in `AndroidManifest.xml`**

Add inside `<application>`, after the existing `<service android:name=".timer.WantTimerService" ...>` block:

```xml
        <receiver
            android:name=".widget.HabitWidgetReceiver"
            android:exported="false"
            android:label="@string/habit_widget_description">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/habit_widget_info" />
        </receiver>
```

- [ ] **Step 6: Build the full app**

Run: `./gradlew :mobile:androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/widget/HabitWidgetReceiver.kt mobile/androidApp/src/androidMain/res/xml/habit_widget_info.xml mobile/androidApp/src/androidMain/res/layout/widget_loading.xml mobile/androidApp/src/androidMain/res/values/strings.xml mobile/androidApp/src/androidMain/AndroidManifest.xml
rtk git commit -m "feat(widget): register HabitWidget as a pinnable home-screen widget"
```

---

### Task 5: Manual QA pass

**Files:** none — verification only.

- [ ] **Step 1: Install and pin the widget**

Install the debug build on a device/emulator, long-press home screen → Widgets → Habitto → drag "Today's habits" onto the home screen.
Expected: widget renders with point balance + today's habits (or the empty state if no habits exist for the current user), no crash.

- [ ] **Step 2: Verify tap-to-log**

Tap a habit row that hasn't met its daily target.
Expected: row's progress text updates (e.g. `0 / 3` → `3 / 3`) within the widget without opening the app; point balance at the top updates to match.

- [ ] **Step 3: Verify open-app tap zones**

Tap the point-balance header, and (if the list is empty) the empty-state text.
Expected: app opens (MainActivity launches).

- [ ] **Step 4: Verify resize**

Long-press the widget on the home screen → drag resize handles smaller and larger.
Expected: widget honors resize within the declared min/target bounds; list scrolls when it doesn't fit.

- [ ] **Step 5: Verify guest mode**

If signed out (guest), pin the widget fresh.
Expected: same behavior as signed-in — habits/balance for the guest-local user, no sign-in prompt (spec: guest support).

- [ ] **Step 6: Verify dark mode**

Toggle system dark mode.
Expected: widget colors follow `GlanceTheme`/dynamic color, text stays legible in both themes.

- [ ] **Step 7: Verify periodic refresh survives process death**

Force-stop the app from Android Settings, then log a habit from a different device/account path if available, or just wait — confirm the widget doesn't crash and eventually reflects state after the next periodic update or a re-pin.
Expected: no crash, no stale-forever state after a fresh pin.
