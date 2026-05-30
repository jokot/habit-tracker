# Phase 9.1 — WantTimer UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land the full-screen `WantTimerScreen` + reconcile the running banner on Want detail + spec the live notification visual, per the canvas v5 designer pass.

**Architecture:** Lift `WantTimerController` to `AppContainer` (single source) and add a `cancelWithPartialLog` path used by all three cancel surfaces (banner pill, full-screen CTA, notification action). Add a new `WantTimerScreen` reached via a `want-timer/{activityId}` nav route + deep link. Update `WantDetailScreen` to render the 4 timer states from canvas; the running banner taps into the full-screen and Cancel pill triggers partial-log. Update `WantTimerService` notification body + progress bar + new `ACTION_STOP_PARTIAL_LOG` so the notification's Cancel action also runs the partial-log path. No schema, no new channels, no new catalog entries.

**Tech Stack:** Kotlin · Compose Material 3 · Compose Navigation (deep links) · WorkManager + Foreground Service · NotificationCompat · kotlinx-datetime · Robolectric.

**Spec ref:** `docs/superpowers/specs/2026-05-30-phase9-1-timer-ux-redesign-design.md`.
**Design ref:** Claude Design bundle extracted at `/tmp/habitto-design/habitto/project/{canvas.html, screens.jsx, screens-v2.jsx, screens-v4.jsx}`.
**Worktree:** `.worktrees/phase9-notifications-timer`.
**Branch:** `feature/phase9-notifications-timer` — same branch as Phase 9 (extends PR #23, ships as follow-up commits).

---

## File Structure

**Create:**
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/CancelResult.kt` — small return-type ADT for the cancel path.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerScreen.kt` — full-bleed Compose screen (running + orphan states).
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerViewModel.kt` — 1Hz observe + cancel path; exposes UI state for ring/points/CTA.

**Modify:**
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerController.kt` — add `LogWantUseCase` + `WantActivityRepository` to constructor, add `cancelWithPartialLog`, separate `signalServiceStop`.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerService.kt` — notification body `"X min left · −Y pt spent"`, determinate `setProgress`, deep-link `setContentIntent`, new `ACTION_STOP_PARTIAL_LOG` handler.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` — construct + expose `wantTimerController` so both VMs share the same instance.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt` — overlap state (`pendingOverlap`), `requestStartTimer(durationSec)` w/ overlap detection, `confirmReplace`, `dismissOverlap`. Use shared controller. Nav callback for opening full-screen.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt` — 4 timer slot states per canvas (`idle-min` / `idle-nonmin` / `active-this` / `active-other`) + replace dialog (a/b variants).
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt` — `composable("want-timer/{activityId}", deepLinks = ...)` route.
- `mobile/androidApp/src/androidMain/AndroidManifest.xml` — second `<intent-filter>` on MainActivity for the `want-timer` deep-link host.
- `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/timer/WantTimerControllerTest.kt` — new cases for `cancelWithPartialLog`.

**Unchanged:**
- `WantTimer` domain model, `WantTimerRepository`, SQLDelight schema (no migration 10).
- `NotificationChannels` (no new channel).
- `NotificationTypeId` catalog (no `want_timer_running` entry — service-internal).
- `WantTimerRecovery`.
- `NotificationFiringDateStore`.

---

### Task 1: Lift `WantTimerController` + add `cancelWithPartialLog`

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/CancelResult.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerController.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/timer/WantTimerControllerTest.kt`

- [ ] **Step 1: Create `CancelResult.kt`**

```kotlin
package com.jktdeveloper.habitto.timer

sealed interface CancelResult {
    object NoActiveTimer : CancelResult
    object Discarded : CancelResult
    data class Logged(val minutes: Int, val pointsSpent: Int) : CancelResult
}
```

- [ ] **Step 2: Extend `WantTimerControllerTest.kt` with failing partial-log cases**

Append to the existing test class (after the existing 2 cases). Add the import `import org.junit.Assert.assertTrue`:

```kotlin
    @Test fun `cancelWithPartialLog logs floor(elapsed) for min-unit and returns Logged`() = runTest {
        val now = kotlinx.datetime.Clock.System.now()
        val fiveMinAgo = now.minus(5, kotlinx.datetime.DateTimeUnit.MINUTE)
        val activityId = "test-min"
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = activityId, userId = "u1", name = "TikTok", unit = "min",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now.toEpochMilliseconds(),
            iconKey = null, hiddenAt = null,
        )
        db.habitTrackerDatabaseQueries.insertWantTimer(
            id = "t-elapsed",
            userId = "u1",
            activityId = activityId,
            durationSec = 900,
            startedAt = fiveMinAgo.toEpochMilliseconds(),
            endsAt = (fiveMinAgo + kotlin.time.Duration.parse("15m")).toEpochMilliseconds(),
            state = "RUNNING",
        )

        val result = controller.cancelWithPartialLog("u1")

        assertTrue(result is CancelResult.Logged)
        val logged = result as CancelResult.Logged
        assertEquals(5, logged.minutes)
        assertEquals(WantTimerState.CANCELLED, repo.getById("t-elapsed")?.state)
    }

    @Test fun `cancelWithPartialLog returns Discarded for elapsed lt 1 minute`() = runTest {
        val now = kotlinx.datetime.Clock.System.now()
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = "a-min", userId = "u1", name = "TikTok", unit = "min",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now.toEpochMilliseconds(),
            iconKey = null, hiddenAt = null,
        )
        db.habitTrackerDatabaseQueries.insertWantTimer(
            id = "t-fresh", userId = "u1", activityId = "a-min",
            durationSec = 600,
            startedAt = now.toEpochMilliseconds(),
            endsAt = (now + kotlin.time.Duration.parse("10m")).toEpochMilliseconds(),
            state = "RUNNING",
        )
        val result = controller.cancelWithPartialLog("u1")
        assertEquals(CancelResult.Discarded, result)
        assertEquals(WantTimerState.CANCELLED, repo.getById("t-fresh")?.state)
    }

    @Test fun `cancelWithPartialLog returns Discarded for non-min unit`() = runTest {
        val now = kotlinx.datetime.Clock.System.now()
        val tenMinAgo = now.minus(10, kotlinx.datetime.DateTimeUnit.MINUTE)
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = "a-cup", userId = "u1", name = "Coffee", unit = "cup",
            unitsPerPoint = 1, isCustom = 0, updatedAt = now.toEpochMilliseconds(),
            iconKey = null, hiddenAt = null,
        )
        db.habitTrackerDatabaseQueries.insertWantTimer(
            id = "t-cup", userId = "u1", activityId = "a-cup",
            durationSec = 900,
            startedAt = tenMinAgo.toEpochMilliseconds(),
            endsAt = (tenMinAgo + kotlin.time.Duration.parse("15m")).toEpochMilliseconds(),
            state = "RUNNING",
        )
        val result = controller.cancelWithPartialLog("u1")
        assertEquals(CancelResult.Discarded, result)
    }

    @Test fun `cancelWithPartialLog returns NoActiveTimer when none running`() = runTest {
        val result = controller.cancelWithPartialLog("u1")
        assertEquals(CancelResult.NoActiveTimer, result)
    }
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.timer.WantTimerControllerTest"
```

Expected: FAIL with `Unresolved reference: cancelWithPartialLog`.

- [ ] **Step 4: Rewrite `WantTimerController.kt`**

Replace the file's contents:

```kotlin
package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.LogWantUseCase
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WantTimerController(
    private val context: Context,
    private val repository: WantTimerRepository,
    private val wantActivityRepository: WantActivityRepository,
    private val logWantUseCase: LogWantUseCase,
    private val clock: Clock = Clock.System,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun start(userId: String, activityId: String, durationSec: Int) {
        require(durationSec in 1..(24 * 60 * 60))
        val now = clock.now()
        val timer = WantTimer(
            id = Uuid.random().toString(),
            userId = userId,
            activityId = activityId,
            durationSec = durationSec,
            startedAt = now,
            endsAt = now + durationSec.seconds,
            state = WantTimerState.RUNNING,
        )
        repository.startReplacing(timer)
        startService(Intent(context, WantTimerService::class.java).apply {
            action = WantTimerService.ACTION_START
            putExtra(WantTimerService.EXTRA_TIMER_ID, timer.id)
        })
    }

    /**
     * Flips active timer → CANCELLED. For `unit == "min"` with elapsed ≥ 1 min,
     * logs partial duration via [LogWantUseCase]. Returns [CancelResult] for UI
     * feedback. Does NOT signal the service to stop — callers must invoke
     * [signalServiceStop] separately.
     */
    suspend fun cancelWithPartialLog(userId: String): CancelResult {
        val active = repository.getActive(userId) ?: return CancelResult.NoActiveTimer
        val elapsedSec = (clock.now() - active.startedAt).inWholeSeconds.coerceAtLeast(0)
        val elapsedMin = (elapsedSec / 60).toInt()
        val activity = wantActivityRepository
            .getAllWantActivitiesForUser(userId)
            .firstOrNull { it.id == active.activityId }

        val loggedPoints: Int? = if (activity != null && activity.unit == "min" && elapsedMin >= 1) {
            logWantUseCase.execute(
                userId = userId,
                activityId = active.activityId,
                taps = elapsedMin,
                deviceMode = DeviceMode.THIS_DEVICE,
            ).fold(
                onSuccess = { it.pointsSpent },
                onFailure = { null },
            )
        } else null

        repository.setState(active.id, WantTimerState.CANCELLED)
        return if (loggedPoints != null) {
            CancelResult.Logged(elapsedMin, loggedPoints)
        } else {
            CancelResult.Discarded
        }
    }

    fun signalServiceStop() {
        startService(Intent(context, WantTimerService::class.java).apply {
            action = WantTimerService.ACTION_STOP
        })
    }

    private fun startService(intent: Intent) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
```

- [ ] **Step 5: Update `WantTimerControllerTest.kt` setup for new constructor + replace old `cancel` test**

Replace the field declarations at the top of the class:

```kotlin
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, "test-want-timer.db")
    private val db = HabitTrackerDatabase(driver)
    private val repo = LocalWantTimerRepository(db)
    private val wantActivityRepo = com.habittracker.data.repository.LocalWantActivityRepository(db)
    private val wantLogRepo = com.habittracker.data.repository.LocalWantLogRepository(db)
    private val habitLogRepo = com.habittracker.data.repository.LocalHabitLogRepository(db)
    private val habitRepo = com.habittracker.data.repository.LocalHabitRepository(db)
    private val streakUseCase = com.habittracker.domain.usecase.ComputeStreakUseCase(habitLogRepo, habitRepo)
    private val getStreakOnDay = com.habittracker.domain.usecase.GetUserStreakOnDayUseCase(streakUseCase)
    private val getBalance = com.habittracker.domain.usecase.GetPointBalanceUseCase(
        habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo,
        getUserStreakOnDayUseCase = getStreakOnDay,
    )
    private val logWantUseCase = com.habittracker.domain.usecase.LogWantUseCase(
        wantLogRepository = wantLogRepo,
        wantActivityRepository = wantActivityRepo,
        getPointBalanceUseCase = getBalance,
        getUserStreakOnDayUseCase = getStreakOnDay,
    )
    private val controller = WantTimerController(context, repo, wantActivityRepo, logWantUseCase)
```

Replace the body of the existing `cancel marks the active timer CANCELLED` test:

```kotlin
    @Test fun `cancelWithPartialLog flips active to CANCELLED when no want activity row exists`() = runTest {
        controller.start(userId = "u1", activityId = "a1", durationSec = 300)
        controller.cancelWithPartialLog("u1")
        assertNull(repo.getActive("u1"))
    }
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.timer.WantTimerControllerTest"
```

Expected: PASS, 5/5 green.

- [ ] **Step 7: Wire shared `WantTimerController` into `AppContainer.kt`**

In `AppContainer.kt`, add a property after `wantTimerRepository`:

```kotlin
val wantTimerController = com.jktdeveloper.habitto.timer.WantTimerController(
    context = appContext,
    repository = wantTimerRepository,
    wantActivityRepository = wantActivityRepository,
    logWantUseCase = logWantUseCase,
)
```

- [ ] **Step 8: Build to verify compile**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/CancelResult.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerController.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/timer/WantTimerControllerTest.kt
git commit -m "feat(timer): cancelWithPartialLog + share WantTimerController via AppContainer"
```

---

### Task 2: WantTimerService — notification body, progress bar, deep-link, ACTION_STOP_PARTIAL_LOG

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerService.kt`

- [ ] **Step 1: Add `Uri` import**

```kotlin
import android.net.Uri
```

- [ ] **Step 2: Add `ACTION_STOP_PARTIAL_LOG` to the companion**

Replace the existing companion object with:

```kotlin
companion object {
    const val ACTION_START = "com.jktdeveloper.habitto.timer.START"
    const val ACTION_STOP = "com.jktdeveloper.habitto.timer.STOP"
    const val ACTION_STOP_PARTIAL_LOG = "com.jktdeveloper.habitto.timer.STOP_PARTIAL_LOG"
    const val EXTRA_TIMER_ID = "timer_id"
    const val NOTIF_RUNNING_ID = 4201
    const val NOTIF_END_ID = 4202

    fun formatMmSs(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }
}
```

- [ ] **Step 3: Handle `ACTION_STOP_PARTIAL_LOG` in `onStartCommand`**

Replace the `when (intent?.action) { ... }` block:

```kotlin
when (intent?.action) {
    ACTION_START -> {
        val id = intent.getStringExtra(EXTRA_TIMER_ID) ?: run { stopSelf(); return START_NOT_STICKY }
        startForegroundForTimer()
        tickJob?.cancel()
        tickJob = lifecycleScope.launch { runUntilEnd(id) }
    }
    ACTION_STOP -> {
        tickJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    ACTION_STOP_PARTIAL_LOG -> {
        tickJob?.cancel()
        val container = (applicationContext as HabitTrackerApplication).container
        lifecycleScope.launch {
            runCatching { container.wantTimerController.cancelWithPartialLog(container.currentUserId()) }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
}
```

- [ ] **Step 4: Replace `buildRunningNotification` with the wider signature**

Delete the existing `buildRunningNotification(remaining, activityName)` and replace with:

```kotlin
private fun buildRunningNotification(
    activityName: String?,
    activityId: String?,
    minLeft: Int,
    elapsedMin: Int,
    totalMin: Int,
    pointsSpent: Int,
): Notification {
    val cancelIntent = Intent(this, WantTimerService::class.java).apply { action = ACTION_STOP_PARTIAL_LOG }
    val cancelPi = PendingIntent.getService(
        this, 0, cancelIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val title = activityName?.let { "$it timer" } ?: "Want timer"
    val body = "$minLeft min left · −$pointsSpent pt spent"
    return NotificationCompat.Builder(this, NotificationChannels.WANT_TIMER_RUNNING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setProgress(totalMin.coerceAtLeast(1), elapsedMin, false)
        .setContentIntent(openTimerScreenPendingIntent(activityId))
        .addAction(0, "Cancel", cancelPi)
        .build()
}
```

- [ ] **Step 5: Replace `startForegroundForTimer` to seed initial values**

```kotlin
private fun startForegroundForTimer() {
    val n = buildRunningNotification(
        activityName = null,
        activityId = null,
        minLeft = 0,
        elapsedMin = 0,
        totalMin = 1,
        pointsSpent = 0,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIF_RUNNING_ID, n,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
        startForeground(NOTIF_RUNNING_ID, n)
    }
}
```

- [ ] **Step 6: Replace `openAppPendingIntent` with the deep-link variant**

Delete `openAppPendingIntent()`. Add:

```kotlin
private fun openTimerScreenPendingIntent(activityId: String?): PendingIntent {
    val uri = if (activityId != null) {
        Uri.parse("com.jktdeveloper.habitto://want-timer/$activityId")
    } else {
        Uri.parse("com.jktdeveloper.habitto://want-timer")
    }
    val intent = Intent(Intent.ACTION_VIEW, uri)
        .setClass(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val requestCode = (activityId?.hashCode() ?: 0) and 0xffff
    return PendingIntent.getActivity(
        this, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

- [ ] **Step 7: Update `runUntilEnd` to call the new `buildRunningNotification`**

Find the existing line inside the while-loop:

```kotlin
NotificationManagerCompat.from(applicationContext)
    .notify(NOTIF_RUNNING_ID, buildRunningNotification(formatMmSs(remainingSec.toInt()), activityName))
```

Replace with:

```kotlin
val totalMin = (initial.durationSec / 60).coerceAtLeast(1)
val minLeft = ((remainingSec + 59) / 60).toInt()
val elapsedMin = (totalMin - minLeft).coerceAtLeast(0)
val pointsSpent = elapsedMin / ((activity?.unitsPerPoint ?: 1).coerceAtLeast(1))
NotificationManagerCompat.from(applicationContext)
    .notify(
        NOTIF_RUNNING_ID,
        buildRunningNotification(
            activityName = activityName,
            activityId = initial.activityId,
            minLeft = minLeft,
            elapsedMin = elapsedMin,
            totalMin = totalMin,
            pointsSpent = pointsSpent,
        ),
    )
```

- [ ] **Step 8: Update `onTimerFinished` end-notif tap target**

Find the existing line:

```kotlin
.setContentIntent(openAppPendingIntent())
```

Replace with:

```kotlin
.setContentIntent(openTimerScreenPendingIntent(null))
```

- [ ] **Step 9: Build + run timer tests**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:compileDebugKotlinAndroid
./gradlew :mobile:androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.timer.*"
```

Expected: BUILD SUCCESSFUL, tests green.

- [ ] **Step 10: Commit**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerService.kt
git commit -m "feat(timer): notif body + progress + deep-link + ACTION_STOP_PARTIAL_LOG"
```

---

### Task 3: AndroidManifest — `want-timer` deep-link host

**Files:**
- Modify: `mobile/androidApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Add a sibling intent-filter to MainActivity**

In `AndroidManifest.xml`, find the existing intent-filter with `android:host="auth-callback"`. Add a sibling intent-filter inside the same `<activity>` block (after the existing one):

```xml
<intent-filter android:autoVerify="false">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="com.jktdeveloper.habitto" android:host="want-timer" />
</intent-filter>
```

- [ ] **Step 2: Build manifest**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:processDebugMainManifest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git add mobile/androidApp/src/androidMain/AndroidManifest.xml
git commit -m "feat(timer): deep-link intent-filter for want-timer/{activityId}"
```

---

### Task 4: `WantTimerScreen` + `WantTimerViewModel`

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerViewModel.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerScreen.kt`

- [ ] **Step 1: Create the ViewModel**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerViewModel.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantTimer
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.timer.CancelResult
import com.jktdeveloper.habitto.timer.WantTimerController
import com.jktdeveloper.habitto.timer.WantTimerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class WantTimerUi(
    val isLoading: Boolean = true,
    val state: ScreenState = ScreenState.Orphan,
    val want: WantActivity? = null,
    val remainingMmSs: String = "--:--",
    val totalMin: Int = 0,
    val elapsedMin: Int = 0,
    val pointsSpentSoFar: Int = 0,
    val elapsedFraction: Float = 0f,
    val toast: String? = null,
) {
    enum class ScreenState { Running, Orphan }
}

class WantTimerViewModel(
    private val timerController: WantTimerController,
    private val timerRepo: WantTimerRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(WantTimerUi())
    val state: StateFlow<WantTimerUi> = _state.asStateFlow()

    constructor(container: AppContainer) : this(
        timerController = container.wantTimerController,
        timerRepo = container.wantTimerRepository,
        wantActivityRepo = container.wantActivityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { observe() }

    private fun observe() {
        viewModelScope.launch {
            while (true) {
                val userId = userIdProvider()
                val active: WantTimer? = timerRepo.getActive(userId)
                if (active == null) {
                    _state.update { it.copy(isLoading = false, state = WantTimerUi.ScreenState.Orphan, want = null) }
                } else {
                    val want = wantActivityRepo
                        .getAllWantActivitiesForUser(userId)
                        .firstOrNull { it.id == active.activityId }
                    val now = clock.now()
                    val remainingSec = (active.endsAt - now).inWholeSeconds.coerceAtLeast(0).toInt()
                    val totalMin = (active.durationSec / 60).coerceAtLeast(1)
                    val elapsedMin = (totalMin - ((remainingSec + 59) / 60)).coerceAtLeast(0)
                    val unitsPerPoint = (want?.unitsPerPoint ?: 1).coerceAtLeast(1)
                    val pointsSpent = elapsedMin / unitsPerPoint
                    _state.update {
                        it.copy(
                            isLoading = false,
                            state = WantTimerUi.ScreenState.Running,
                            want = want,
                            remainingMmSs = WantTimerService.formatMmSs(remainingSec),
                            totalMin = totalMin,
                            elapsedMin = elapsedMin,
                            pointsSpentSoFar = pointsSpent,
                            elapsedFraction = (elapsedMin.toFloat() / totalMin.toFloat()).coerceIn(0f, 1f),
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            val result = timerController.cancelWithPartialLog(userIdProvider())
            timerController.signalServiceStop()
            val toast = when (result) {
                is CancelResult.Logged -> "Logged ${result.minutes} min · −${result.pointsSpent} pt"
                CancelResult.Discarded -> "Timer cancelled"
                CancelResult.NoActiveTimer -> null
            }
            _state.update { it.copy(toast = toast) }
        }
    }

    fun consumeToast() { _state.update { it.copy(toast = null) } }
}
```

- [ ] **Step 2: Create the screen**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerScreen.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.want

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktdeveloper.habitto.ui.components.resolveWantIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantTimerScreen(viewModel: WantTimerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val toast = state.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (state.state) {
                WantTimerUi.ScreenState.Orphan -> OrphanContent(onBack)
                WantTimerUi.ScreenState.Running -> RunningContent(state, viewModel::cancel)
            }
        }
    }
}

@Composable
private fun OrphanContent(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.TimerOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("No timer running", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nothing is being tracked right now. Start one from any want.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Spacer(Modifier.height(28.dp))
        FilledTonalButton(onClick = onBack, modifier = Modifier.height(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Back")
        }
    }
}

@Composable
private fun RunningContent(state: WantTimerUi, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    resolveWantIcon(state.want?.iconKey, state.want?.name ?: ""),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                state.want?.name ?: "Timer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(40.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(228.dp), contentAlignment = Alignment.Center) {
                val surfaceVar = MaterialTheme.colorScheme.surfaceVariant
                val errorColor = MaterialTheme.colorScheme.error
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 10.dp.toPx()
                    val diameter = size.minDimension - strokePx
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)
                    drawArc(
                        color = surfaceVar,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx),
                    )
                    drawArc(
                        color = errorColor,
                        startAngle = -90f,
                        sweepAngle = 360f * state.elapsedFraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "REMAINING",
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.remainingMmSs,
                        fontSize = 56.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "of ${state.totalMin} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "−${state.pointsSpentSoFar} pt",
                        fontSize = 26.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "spent so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cancel & log ${state.elapsedMin} min", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Logs the ${state.elapsedMin} min spent so far. The timer keeps running if you go back.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 3: Build to verify compile**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerScreen.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantTimerViewModel.kt
git commit -m "feat(timer): WantTimerScreen full-bleed (running + orphan) + WantTimerViewModel"
```

---

### Task 5: Wire `want-timer` route in `AppNavigation`

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

- [ ] **Step 1: Locate existing routes**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
grep -n "Screen.\|notifications-settings\|composable(" mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
```

Identify the `Screen` sealed class entries and the `NavHost { ... }` block.

- [ ] **Step 2: Add `Screen.WantTimer` entry**

Inside the `sealed class Screen(...)` block, add:

```kotlin
data class WantTimer(val activityId: String) : Screen("want-timer/$activityId") {
    companion object {
        const val route = "want-timer/{activityId}"
        const val argKey = "activityId"
    }
}
```

(If `Screen` uses a different pattern in the project, adapt — match the existing convention exactly.)

- [ ] **Step 3: Add the composable**

Inside the `NavHost { ... }` block, beside other `composable(...)` entries, add:

```kotlin
composable(
    route = com.jktdeveloper.habitto.ui.navigation.Screen.WantTimer.route,
    deepLinks = listOf(
        androidx.navigation.navDeepLink { uriPattern = "com.jktdeveloper.habitto://want-timer/{activityId}" },
    ),
) {
    val vm = remember { com.jktdeveloper.habitto.ui.want.WantTimerViewModel(container) }
    com.jktdeveloper.habitto.ui.want.WantTimerScreen(
        viewModel = vm,
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 4: Build to verify**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
git commit -m "feat(timer): nav route want-timer/{activityId} with deep link"
```

---

### Task 6: `WantDetailViewModel` — overlap detection + nav callback + shared controller

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt`

- [ ] **Step 1: Add required imports**

```kotlin
import com.jktdeveloper.habitto.timer.CancelResult
import com.jktdeveloper.habitto.timer.WantTimerService
```

- [ ] **Step 2: Replace `WantDetailUi` + add `PendingOverlap`**

Replace the existing `WantDetailUi` data class with:

```kotlin
data class WantDetailUi(
    val isLoading: Boolean = true,
    val want: WantActivity? = null,
    val totalSpent7d: Int = 0,
    val timesLogged7d: Int = 0,
    val timeline: List<DayLogs> = emptyList(),
    val toast: String? = null,
    val activeTimer: WantTimer? = null,
    val timerRemainingMmSs: String? = null,
    val activeTimerActivityName: String? = null,
    val activeTimerElapsedMin: Int = 0,
    val activeTimerMinutesLeft: Int = 0,
    val showDurationSheet: Boolean = false,
    val pendingOverlap: PendingOverlap? = null,
    val navigateToTimerActivityId: String? = null,
)

data class PendingOverlap(
    val otherWantName: String,
    val elapsedMin: Int,
    val minutesLeft: Int,
    val desiredDurationSec: Int,
)
```

- [ ] **Step 3: Replace class shell to use shared controller**

Replace the existing class header + secondary constructor + `init` + `showDurationSheet` + `dismissDurationSheet` + `startTimer` + `cancelTimer` + `observeTimer` with:

```kotlin
class WantDetailViewModel private constructor(
    private val activityId: String,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val timerController: com.jktdeveloper.habitto.timer.WantTimerController,
    private val timerRepo: com.habittracker.data.repository.WantTimerRepository,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
    private val tz: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(WantDetailUi())
    val state: StateFlow<WantDetailUi> = _state.asStateFlow()

    constructor(activityId: String, container: AppContainer) : this(
        activityId = activityId,
        wantActivityRepo = container.wantActivityRepository,
        wantLogRepo = container.wantLogRepository,
        timerController = container.wantTimerController,
        timerRepo = container.wantTimerRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { reload(); observeTimer() }

    fun showDurationSheet() { _state.update { it.copy(showDurationSheet = true) } }
    fun dismissDurationSheet() { _state.update { it.copy(showDurationSheet = false) } }
    fun dismissOverlap() { _state.update { it.copy(pendingOverlap = null) } }
    fun consumeNavigation() { _state.update { it.copy(navigateToTimerActivityId = null) } }

    fun requestStartTimer(durationSec: Int) {
        viewModelScope.launch {
            val userId = userIdProvider()
            val active = timerRepo.getActive(userId)
            if (active != null && active.activityId != activityId) {
                val otherWant = wantActivityRepo
                    .getAllWantActivitiesForUser(userId)
                    .firstOrNull { it.id == active.activityId }
                val elapsedMin = ((clock.now() - active.startedAt).inWholeSeconds / 60).coerceAtLeast(0).toInt()
                val minutesLeft = ((active.endsAt - clock.now()).inWholeSeconds / 60).coerceAtLeast(0).toInt()
                _state.update {
                    it.copy(
                        showDurationSheet = false,
                        pendingOverlap = PendingOverlap(
                            otherWantName = otherWant?.name ?: "another want",
                            elapsedMin = elapsedMin,
                            minutesLeft = minutesLeft,
                            desiredDurationSec = durationSec,
                        ),
                    )
                }
            } else {
                doStart(durationSec)
            }
        }
    }

    fun confirmReplace() {
        viewModelScope.launch {
            val pending = _state.value.pendingOverlap ?: return@launch
            timerController.cancelWithPartialLog(userIdProvider())
            timerController.signalServiceStop()
            _state.update { it.copy(pendingOverlap = null) }
            doStart(pending.desiredDurationSec)
        }
    }

    private suspend fun doStart(durationSec: Int) {
        timerController.start(userIdProvider(), activityId, durationSec)
        _state.update {
            it.copy(
                showDurationSheet = false,
                navigateToTimerActivityId = activityId,
            )
        }
    }

    fun cancelTimer() {
        viewModelScope.launch {
            val result = timerController.cancelWithPartialLog(userIdProvider())
            timerController.signalServiceStop()
            val toast = when (result) {
                is CancelResult.Logged -> "Logged ${result.minutes} min · −${result.pointsSpent} pt"
                CancelResult.Discarded -> "Timer cancelled"
                CancelResult.NoActiveTimer -> null
            }
            _state.update { it.copy(toast = toast) }
        }
    }

    fun openTimerScreen() {
        _state.update { it.copy(navigateToTimerActivityId = activityId) }
    }

    private data class TimerSnapshot(
        val remainingMmSs: String?,
        val otherName: String?,
        val elapsedMin: Int,
        val minLeft: Int,
    )

    private suspend fun snapshot(userId: String, active: WantTimer?): TimerSnapshot {
        if (active == null) return TimerSnapshot(null, null, 0, 0)
        val remainSec = (active.endsAt - clock.now()).inWholeSeconds.coerceAtLeast(0).toInt()
        val totalMin = (active.durationSec / 60).coerceAtLeast(1)
        val minLeft = ((remainSec + 59) / 60)
        val elapsedMin = (totalMin - minLeft).coerceAtLeast(0)
        val otherName = if (active.activityId == activityId) null else {
            wantActivityRepo.getAllWantActivitiesForUser(userId)
                .firstOrNull { it.id == active.activityId }?.name
        }
        return TimerSnapshot(
            remainingMmSs = WantTimerService.formatMmSs(remainSec),
            otherName = otherName,
            elapsedMin = elapsedMin,
            minLeft = minLeft,
        )
    }

    private fun observeTimer() {
        viewModelScope.launch {
            while (true) {
                val userId = userIdProvider()
                val active = timerRepo.getActive(userId)
                val snap = snapshot(userId, active)
                _state.update {
                    it.copy(
                        activeTimer = active,
                        timerRemainingMmSs = snap.remainingMmSs,
                        activeTimerActivityName = snap.otherName,
                        activeTimerElapsedMin = snap.elapsedMin,
                        activeTimerMinutesLeft = snap.minLeft,
                    )
                }
                delay(1000L)
            }
        }
    }
```

Keep the existing `reload`, `consumeToast`, `hide`, `delete` methods unchanged.

- [ ] **Step 4: Build to verify compile**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt
git commit -m "feat(timer): WantDetailViewModel overlap detection + shared controller + nav signal"
```

---

### Task 7: `WantDetailScreen` — 4 timer states + replace dialog + auto-nav

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt`

- [ ] **Step 1: Add nav callback parameter**

Change the function signature:

```kotlin
fun WantDetailScreen(
    viewModel: WantDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTimer: (activityId: String) -> Unit,
)
```

- [ ] **Step 2: Add nav side-effect**

Inside the composable body, after the existing `LaunchedEffect(toast) { ... }` block, add:

```kotlin
val navTarget = state.navigateToTimerActivityId
LaunchedEffect(navTarget) {
    if (navTarget != null) {
        viewModel.consumeNavigation()
        onOpenTimer(navTarget)
    }
}
```

- [ ] **Step 3: Add imports**

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
```

- [ ] **Step 4: Replace the existing banner-or-Start block**

Find the existing `state.activeTimer?.let { ... }` running-banner block AND the subsequent `if (state.activeTimer == null) { ... }` Start-timer block. Replace BOTH with the new 4-state shape. Insert directly after the `HeroCard(...)` call (and the existing `Spacer(8.dp)` that follows it):

```kotlin
val isActiveThisWant = state.activeTimer != null && state.activeTimer!!.activityId == want.id
val isMinUnit = want.unit == "min"

when {
    isActiveThisWant -> {
        Spacer(Modifier.height(8.dp))
        ActiveThisWantBanner(
            remainingMmSs = state.timerRemainingMmSs ?: "--:--",
            onBannerTap = viewModel::openTimerScreen,
            onCancel = viewModel::cancelTimer,
        )
    }
    isMinUnit -> {
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(
            onClick = viewModel::showDurationSheet,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start timer", fontWeight = FontWeight.SemiBold)
        }
    }
    else -> {
        Spacer(Modifier.height(8.dp))
        IdleNonMinPlaceholder(unit = want.unit)
    }
}
```

- [ ] **Step 5: Add the two helper composables**

After `relativeDayLabel(...)` at the bottom of the file, add:

```kotlin
@Composable
private fun ActiveThisWantBanner(
    remainingMmSs: String,
    onBannerTap: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBannerTap)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        "Timer running",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        remainingMmSs,
                        fontSize = 22.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(76.dp)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.StopCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Cancel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleNonMinPlaceholder(unit: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "No timer — \"$unit\" wants are logged manually.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 6: Update duration-sheet chip click**

Find the existing `ModalBottomSheet` block. Replace the AssistChip onClick:

```kotlin
onClick = { viewModel.requestStartTimer(mins * 60) },
```

- [ ] **Step 7: Add replace dialog**

After the existing `if (state.showDurationSheet) { ... }` block, add:

```kotlin
state.pendingOverlap?.let { p ->
    AlertDialog(
        onDismissRequest = viewModel::dismissOverlap,
        title = { Text("Replace running timer?") },
        text = {
            val tail = if (p.elapsedMin >= 1) {
                "Starting a new one will log ${p.elapsedMin} min and end it."
            } else {
                "Starting a new one will discard it."
            }
            Text("You have a ${p.minutesLeft} min timer for ${p.otherWantName}. $tail")
        },
        confirmButton = {
            Button(onClick = viewModel::confirmReplace) { Text("Replace") }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissOverlap) { Text("Keep") }
        },
    )
}
```

- [ ] **Step 8: Update AppNavigation caller of `WantDetailScreen`**

In `AppNavigation.kt`, locate the `composable("want-detail/{activityId}", ...) { ... WantDetailScreen(...) ... }` block (or however WantDetailScreen is invoked). Pass the new callback:

```kotlin
onOpenTimer = { id -> navController.navigate(com.jktdeveloper.habitto.ui.navigation.Screen.WantTimer(id).route) }
```

- [ ] **Step 9: Build full app**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt
git commit -m "feat(timer): WantDetail 4 timer states + replace dialog + auto-nav to full-screen"
```

---

### Task 8: Full build + test sweep + push

- [ ] **Step 1: Full Android build**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full unit-test sweep**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
./gradlew :mobile:androidApp:testDebugUnitTest :mobile:shared:jvmTest
```

Expected: PASS.

- [ ] **Step 3: Manual smoke**

1. Clear app data + reinstall.
2. Onboarding → identity + habit + want activity with `unit="min"` + want activity with `unit="cup"`.
3. Open the cup-unit want detail → dashed "No timer — `cup` wants are logged manually." placeholder. No Start button.
4. Open the min-unit want detail → Start timer → 5 min → auto-navigate to full-screen.
5. Confirm progress ring + MM:SS + "−Y pt spent so far" pill + "Cancel & log 0 min" button (label increments as minutes elapse).
6. Back → return to Want detail. Banner "Timer running · MM:SS" + Cancel pill.
7. Tap banner body → returns to full-screen.
8. Notification shade → "{Want} timer" + progress bar + "X min left · −Y pt spent" + Cancel action. Tap notif body → opens full-screen via deep link.
9. Notification Cancel action → timer transitions to CANCELLED, partial-log if elapsed ≥ 1 min, foreground notif removed.
10. Start another timer for ~1 min → open a DIFFERENT min-unit want → Start → duration → confirm-replace dialog. Replace → old logs partial, new starts.
11. Orphan test: `am start -W -a android.intent.action.VIEW -d "com.jktdeveloper.habitto://want-timer/orphan-id"` while no timer running → orphan screen.

- [ ] **Step 4: Confirm tree clean**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git status
```

- [ ] **Step 5: Push (updates PR #23 in-place)**

```bash
cd /Users/jokot/dev/habit-tracker/.worktrees/phase9-notifications-timer
git push
```

---

## Self-review notes

**Spec coverage:**

- Surface model "both with roles": Want detail 4 states (Task 7) + full-screen WantTimerScreen (Task 4) + auto-nav-on-start + banner-tap nav + deep-link nav (Tasks 2, 5, 7).
- Live notif redesign: title `{Want} timer`, body `X min left · −Y pt spent`, determinate progress, deep-link content intent, Cancel via `ACTION_STOP_PARTIAL_LOG` (Task 2). Channel + LOW unchanged.
- Cancel partial-log: `cancelWithPartialLog` (Task 1) used by banner pill, full-screen CTA, notification action (Task 2). InsufficientPointsException swallowed.
- Overlap confirm-replace: `requestStartTimer` → overlap dialog (Tasks 6, 7) → `confirmReplace`. Same-want overlap silently restarts.
- Non-min units: `WantDetailScreen` renders `IdleNonMinPlaceholder` instead of Start (Task 7). Controller unit-agnostic. Service `onTimerFinished` keeps `if (activity.unit == "min")` guard from Phase 9.
- Lift controller to AppContainer: Task 1.
- Deep-link intent-filter: Task 3.

**Type consistency:**
- `CancelResult` defined Task 1 → consumed Tasks 4, 6, 2.
- `WantTimerService.ACTION_STOP_PARTIAL_LOG` defined Task 2 → used by notif Cancel action.
- `Screen.WantTimer` defined Task 5 → used Tasks 5, 7.
- `PendingOverlap` defined Task 6 → used Task 7.

**Out of scope (per spec):**
- bar_raised / bar_dropped, Pause/Resume, Snooze, concurrent timers, iOS, widgets, sound/vibrate UI, cross-device sync, Phase 4 inline-notification-toggle cleanup.
