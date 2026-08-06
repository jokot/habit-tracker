# Phase 9 — Notifications + Want Timer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship 11 canvas notification types in 4 grouped channels + a foreground-service want timer with mm:ss countdown, all per `docs/superpowers/specs/2026-05-17-phase9-notifications-timer-design.md`.

**Architecture:** Extend existing notification stack (`NotificationChannels`, `NotificationPreferences`, `NotificationScheduler`, `NotificationFiringDateStore`, `DayBoundaryWorker`, `DailyReminderWorker`, `StreakRiskWorker`) — same package `com.jktdeveloper.habitto.notifications`. Add `WantTimerService` (foreground) + SQLDelight `LocalWantTimer` table (local-only, no sync). Per-identity reminders use one PeriodicWorkRequest per active identity, scheduled by a new `PerIdentityReminderScheduler`. Sync-related notifications fire from existing `SyncEngine` state-flow observers in `AppContainer`.

**Tech Stack:** Kotlin Multiplatform · SQLDelight · WorkManager · Android Foreground Service · NotificationManagerCompat · DataStore (preferences) · Compose Material 3 · kotlinx-datetime · Robolectric (tests).

**Spec ref:** `docs/superpowers/specs/2026-05-17-phase9-notifications-timer-design.md`.
**Worktree:** `.worktrees/phase9-notifications-timer`.
**Branch:** `feature/phase9-notifications-timer`.

---

## File Structure

**Create:**
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/9.sqm` — add `LocalWantTimer` table.
- `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantTimer.kt` — domain model.
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantTimerRepository.kt` — interface.
- `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantTimerRepository.kt` — SQLDelight impl.
- `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/LocalWantTimerRepositoryTest.kt` — repo round-trip test.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationTypeId.kt` — type-id catalog enum.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderWorker.kt` — worker.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderScheduler.kt` — per-identity WorkRequest scheduler.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorker.kt` — 7/30/100/365 streak milestones.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounter.kt` — DataStore counter for 3-strike rule.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerService.kt` — foreground service.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerController.kt` — start/cancel facade used by UI.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerRecovery.kt` — app-start recovery for orphaned RUNNING timers.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsScreen.kt` — settings UI.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsViewModel.kt` — VM.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/NotificationPermissionPrompt.kt` — first-launch prompt.
- `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorkerTest.kt`
- `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderSchedulerTest.kt`
- `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounterTest.kt`
- `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/timer/WantTimerControllerTest.kt`

**Modify:**
- `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` — add table DDL + queries.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt` — add 4 grouped + 2 timer channels.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt` — master switch + per-type Map.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt` — new keys for milestone/tier/per-identity.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt` — orchestrate new workers, route master-switch.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt` — channel rebind + copy.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt` — channel rebind + copy.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt` — add `tier_advanced` fire path.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt` — wire WantTimer repo, PerIdentityReminderScheduler, SyncFailureCounter; sync-state observer for system notifications.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HabitTrackerApplication.kt` — call `WantTimerRecovery.scanOnStart()` after container created.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/MainActivity.kt` — show `NotificationPermissionPrompt` on first launch.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt` — duration bottom sheet + running banner.
- `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt` — observe active timer + start/cancel.
- `mobile/androidApp/src/androidMain/AndroidManifest.xml` — register service + add `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` permissions.

---

### Task 1: SQLDelight migration 9 + LocalWantTimer table + queries

**Files:**
- Create: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/9.sqm`
- Modify: `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq`

- [ ] **Step 1: Add migration 9**

Create `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/9.sqm`:

```sql
-- Phase 9: LocalWantTimer (local-only, no sync). Single-row-at-a-time semantics
-- enforced in repo layer — starting a new timer cancels the previous.

CREATE TABLE IF NOT EXISTS LocalWantTimer (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    activityId TEXT NOT NULL,
    durationSec INTEGER NOT NULL,
    startedAt INTEGER NOT NULL,
    endsAt INTEGER NOT NULL,
    state TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wanttimer_user_state ON LocalWantTimer(userId, state);
```

- [ ] **Step 2: Add table + queries to main schema**

Append to `mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq` (after the existing `getHabitsForIdentity` query block at end of file):

```sql
CREATE TABLE IF NOT EXISTS LocalWantTimer (
    id TEXT NOT NULL PRIMARY KEY,
    userId TEXT NOT NULL,
    activityId TEXT NOT NULL,
    durationSec INTEGER NOT NULL,
    startedAt INTEGER NOT NULL,
    endsAt INTEGER NOT NULL,
    state TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wanttimer_user_state ON LocalWantTimer(userId, state);

-- LocalWantTimer queries
insertWantTimer:
INSERT INTO LocalWantTimer (id, userId, activityId, durationSec, startedAt, endsAt, state)
VALUES (?, ?, ?, ?, ?, ?, ?);

getActiveWantTimer:
SELECT * FROM LocalWantTimer WHERE userId = ? AND state = 'RUNNING' LIMIT 1;

getWantTimerById:
SELECT * FROM LocalWantTimer WHERE id = ? LIMIT 1;

getAllRunningTimers:
SELECT * FROM LocalWantTimer WHERE state = 'RUNNING';

updateWantTimerState:
UPDATE LocalWantTimer SET state = ? WHERE id = ?;

deleteFinishedTimersBefore:
DELETE FROM LocalWantTimer WHERE state != 'RUNNING' AND endsAt < ?;
```

- [ ] **Step 3: Build to verify schema**

Run: `rtk ./gradlew :shared:generateCommonMainHabitTrackerDatabaseInterface`
Expected: SUCCESS — generates `HabitTrackerDatabaseQueries.insertWantTimer`, `getActiveWantTimer`, etc.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/HabitTrackerDatabase.sq mobile/shared/src/commonMain/sqldelight/com/habittracker/data/local/migrations/9.sqm
rtk git commit -m "feat(timer): SQLDelight migration 9 — LocalWantTimer table"
```

---

### Task 2: WantTimer domain model + repository

**Files:**
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantTimer.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantTimerRepository.kt`
- Create: `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantTimerRepository.kt`
- Create: `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/LocalWantTimerRepositoryTest.kt`

- [ ] **Step 1: Write the failing repo test**

Create `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/LocalWantTimerRepositoryTest.kt`:

```kotlin
package com.habittracker.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalWantTimerRepositoryTest {

    private lateinit var db: HabitTrackerDatabase
    private lateinit var repo: LocalWantTimerRepository

    @BeforeTest fun setup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HabitTrackerDatabase.Schema.create(driver)
        db = HabitTrackerDatabase(driver)
        repo = LocalWantTimerRepository(db)
    }

    @Test fun `insert then getActive returns inserted RUNNING timer`() = runTest {
        val t = WantTimer(
            id = "t1", userId = "u1", activityId = "a1",
            durationSec = 600,
            startedAt = Instant.fromEpochSeconds(1000),
            endsAt = Instant.fromEpochSeconds(1600),
            state = WantTimerState.RUNNING,
        )
        repo.insert(t)
        assertEquals(t, repo.getActive("u1"))
    }

    @Test fun `startReplacing cancels previous RUNNING timer`() = runTest {
        val t1 = WantTimer("t1", "u1", "a1", 600,
            Instant.fromEpochSeconds(1000), Instant.fromEpochSeconds(1600), WantTimerState.RUNNING)
        val t2 = WantTimer("t2", "u1", "a2", 300,
            Instant.fromEpochSeconds(2000), Instant.fromEpochSeconds(2300), WantTimerState.RUNNING)
        repo.insert(t1)
        repo.startReplacing(t2)
        assertEquals(t2, repo.getActive("u1"))
        assertEquals(WantTimerState.CANCELLED, repo.getById("t1")?.state)
    }

    @Test fun `getActive returns null when none running`() = runTest {
        assertNull(repo.getActive("u1"))
    }

    @Test fun `transition to FINISHED is persisted`() = runTest {
        val t = WantTimer("t1", "u1", "a1", 600,
            Instant.fromEpochSeconds(1000), Instant.fromEpochSeconds(1600), WantTimerState.RUNNING)
        repo.insert(t)
        repo.setState("t1", WantTimerState.FINISHED)
        assertEquals(WantTimerState.FINISHED, repo.getById("t1")?.state)
        assertNull(repo.getActive("u1"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk ./gradlew :shared:jvmTest --tests "com.habittracker.data.repository.LocalWantTimerRepositoryTest"`
Expected: FAIL with "unresolved reference: WantTimer" / "unresolved reference: LocalWantTimerRepository".

- [ ] **Step 3: Create the domain model**

Create `mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantTimer.kt`:

```kotlin
package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantTimer(
    val id: String,
    val userId: String,
    val activityId: String,
    val durationSec: Int,
    val startedAt: Instant,
    val endsAt: Instant,
    val state: WantTimerState,
)

enum class WantTimerState { RUNNING, FINISHED, CANCELLED }
```

- [ ] **Step 4: Create the repository interface**

Create `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantTimerRepository.kt`:

```kotlin
package com.habittracker.data.repository

import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState

interface WantTimerRepository {
    suspend fun insert(timer: WantTimer)
    /** Atomically cancels any RUNNING row for the user, then inserts [timer] as RUNNING. */
    suspend fun startReplacing(timer: WantTimer)
    suspend fun getActive(userId: String): WantTimer?
    suspend fun getById(id: String): WantTimer?
    suspend fun setState(id: String, state: WantTimerState)
    suspend fun getAllRunning(): List<WantTimer>
}
```

- [ ] **Step 5: Create the SQLDelight implementation**

Create `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantTimerRepository.kt`:

```kotlin
package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalWantTimer as Row
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import kotlinx.datetime.Instant

class LocalWantTimerRepository(
    private val db: HabitTrackerDatabase,
) : WantTimerRepository {

    private val q get() = db.habitTrackerDatabaseQueries

    override suspend fun insert(timer: WantTimer) {
        q.insertWantTimer(
            id = timer.id,
            userId = timer.userId,
            activityId = timer.activityId,
            durationSec = timer.durationSec.toLong(),
            startedAt = timer.startedAt.toEpochMilliseconds(),
            endsAt = timer.endsAt.toEpochMilliseconds(),
            state = timer.state.name,
        )
    }

    override suspend fun startReplacing(timer: WantTimer) {
        q.transaction {
            val current = q.getActiveWantTimer(timer.userId).executeAsOneOrNull()
            if (current != null) {
                q.updateWantTimerState(WantTimerState.CANCELLED.name, current.id)
            }
            q.insertWantTimer(
                id = timer.id,
                userId = timer.userId,
                activityId = timer.activityId,
                durationSec = timer.durationSec.toLong(),
                startedAt = timer.startedAt.toEpochMilliseconds(),
                endsAt = timer.endsAt.toEpochMilliseconds(),
                state = timer.state.name,
            )
        }
    }

    override suspend fun getActive(userId: String): WantTimer? =
        q.getActiveWantTimer(userId).executeAsOneOrNull()?.toDomain()

    override suspend fun getById(id: String): WantTimer? =
        q.getWantTimerById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun setState(id: String, state: WantTimerState) {
        q.updateWantTimerState(state.name, id)
    }

    override suspend fun getAllRunning(): List<WantTimer> =
        q.getAllRunningTimers().executeAsList().map { it.toDomain() }

    private fun Row.toDomain(): WantTimer = WantTimer(
        id = id,
        userId = userId,
        activityId = activityId,
        durationSec = durationSec.toInt(),
        startedAt = Instant.fromEpochMilliseconds(startedAt),
        endsAt = Instant.fromEpochMilliseconds(endsAt),
        state = WantTimerState.valueOf(state),
    )
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `rtk ./gradlew :shared:jvmTest --tests "com.habittracker.data.repository.LocalWantTimerRepositoryTest"`
Expected: PASS, 4/4 green.

- [ ] **Step 7: Commit**

```bash
rtk git add mobile/shared/src/commonMain/kotlin/com/habittracker/domain/model/WantTimer.kt mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/WantTimerRepository.kt mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/LocalWantTimerRepository.kt mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/LocalWantTimerRepositoryTest.kt
rtk git commit -m "feat(timer): WantTimer domain + SQLDelight repo"
```

---

### Task 3: NotificationTypeId catalog

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationTypeId.kt`

- [ ] **Step 1: Create the catalog enum**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationTypeId.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

/**
 * Canvas v5 catalog (11 types ship in Phase 9; bar_raised/dropped deferred to Phase 10).
 * hasTime = true means user picks a daily LocalTime for it in NotificationsSettings.
 */
enum class NotificationTypeId(
    val key: String,
    val category: NotificationCategory,
    val defaultEnabled: Boolean,
    val defaultMinutesOfDay: Int? = null,
) {
    DAILY_REMINDER("daily_reminder", NotificationCategory.REMINDER, true, 9 * 60),
    DAILY_REMINDER_PER_IDENTITY("daily_reminder_per_identity", NotificationCategory.REMINDER, false, 17 * 60 + 30),
    STREAK_RISK("streak_risk", NotificationCategory.ALERT, true, 21 * 60),
    WANT_TIMER_END("want_timer_end", NotificationCategory.ALERT, true),
    STREAK_FROZEN("streak_frozen", NotificationCategory.STATUS, true),
    STREAK_RESET("streak_reset", NotificationCategory.STATUS, true),
    TIER_ADVANCED("tier_advanced", NotificationCategory.STATUS, true),
    MILESTONE_STREAK("milestone_streak", NotificationCategory.STATUS, true),
    SESSION_EXPIRED("session_expired", NotificationCategory.SYSTEM, true),
    CLOUD_RESTORE_COMPLETE("cloud_restore_complete", NotificationCategory.SYSTEM, true),
    SYNC_FAILED_PERSISTENT("sync_failed_persistent", NotificationCategory.SYSTEM, true);

    val hasTime: Boolean get() = defaultMinutesOfDay != null
}

enum class NotificationCategory(val displayName: String) {
    REMINDER("Reminders"),
    ALERT("Alerts"),
    STATUS("Status updates"),
    SYSTEM("System"),
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationTypeId.kt
rtk git commit -m "feat(notifications): NotificationTypeId catalog (11 types, 4 categories)"
```

---

### Task 4: Extend NotificationChannels (4 grouped + 2 timer)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt`

- [ ] **Step 1: Rewrite NotificationChannels.kt**

Replace the file's contents entirely:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    // Grouped channels per canvas v5
    const val REMINDER = "reminder"
    const val ALERT = "alert"
    const val STATUS = "status"
    const val SYSTEM = "system"

    // Timer-specific channels (kept separate so users can mute the live countdown
    // without losing the completion alert).
    const val WANT_TIMER_RUNNING = "want_timer_running"
    const val WANT_TIMER_END = "want_timer_end"

    // Legacy Phase 4 ids — aliases so still-in-flight code compiles during rebind.
    @Deprecated("Use REMINDER", ReplaceWith("REMINDER"))
    const val DAILY_REMINDER = REMINDER
    @Deprecated("Use ALERT", ReplaceWith("ALERT"))
    const val STREAK_RISK = ALERT
    @Deprecated("Use STATUS", ReplaceWith("STATUS"))
    const val STREAK_STATUS = STATUS

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return

        mgr.createNotificationChannel(
            NotificationChannel(REMINDER, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Gentle daily nudges to log habits."
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(ALERT, "Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Time-sensitive alerts: streak risk, want-timer end."
                enableVibration(true)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(STATUS, "Status updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Streak frozen/reset, tier advances, milestones."
                setSound(null, null)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(SYSTEM, "System", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Session expired, cloud restore complete, sync failures."
                setSound(null, null)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(WANT_TIMER_RUNNING, "Want timer (running)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown while a want timer is running."
                setShowBadge(false)
                setSound(null, null)
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(WANT_TIMER_END, "Want timer (end)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alert when a want timer finishes."
                enableVibration(true)
            }
        )
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (existing channel-id usages still compile via deprecated aliases).

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationChannels.kt
rtk git commit -m "feat(notifications): 4 grouped channels + 2 timer channels"
```

---

### Task 5: Extend NotificationPreferences (master + per-type Map)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt`
- Modify: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt`

- [ ] **Step 1: Add failing test cases**

Append to `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt` (inside the existing class, before the closing `}`):

```kotlin
    @Test fun `defaults include every NotificationTypeId with the catalog default`() = runTest {
        val snap = prefs.flow.first()
        for (t in NotificationTypeId.values()) {
            assertEquals(t.defaultEnabled, snap.isEnabled(t))
            if (t.hasTime) {
                assertEquals(t.defaultMinutesOfDay, snap.minutesOfDay(t))
            }
        }
    }

    @Test fun `setTypeEnabled persists per-type toggle`() = runTest {
        prefs.setTypeEnabled(NotificationTypeId.MILESTONE_STREAK, false)
        assertEquals(false, prefs.flow.first().isEnabled(NotificationTypeId.MILESTONE_STREAK))
    }

    @Test fun `setTypeMinutesOfDay clamps over-range value`() = runTest {
        prefs.setTypeMinutesOfDay(NotificationTypeId.DAILY_REMINDER, 3000)
        assertEquals(1439, prefs.flow.first().minutesOfDay(NotificationTypeId.DAILY_REMINDER))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.NotificationPreferencesTest"`
Expected: FAIL with "unresolved reference: isEnabled" / "setTypeEnabled".

- [ ] **Step 3: Rewrite NotificationPreferences.kt**

Replace the file's contents entirely:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_prefs",
)

data class NotificationPrefs(
    val masterEnabled: Boolean,
    private val enabledByType: Map<NotificationTypeId, Boolean>,
    private val minutesByType: Map<NotificationTypeId, Int>,
) {
    fun isEnabled(t: NotificationTypeId): Boolean =
        enabledByType[t] ?: t.defaultEnabled

    fun minutesOfDay(t: NotificationTypeId): Int? =
        minutesByType[t] ?: t.defaultMinutesOfDay

    // Back-compat accessors used by existing Phase 4 workers + tests.
    val dailyReminderEnabled: Boolean get() = isEnabled(NotificationTypeId.DAILY_REMINDER)
    val dailyReminderMinutes: Int get() = minutesOfDay(NotificationTypeId.DAILY_REMINDER) ?: (9 * 60)
    val streakRiskEnabled: Boolean get() = isEnabled(NotificationTypeId.STREAK_RISK)
    val streakRiskMinutes: Int get() = minutesOfDay(NotificationTypeId.STREAK_RISK) ?: (21 * 60)
    val streakFrozenEnabled: Boolean get() = isEnabled(NotificationTypeId.STREAK_FROZEN)
    val streakResetEnabled: Boolean get() = isEnabled(NotificationTypeId.STREAK_RESET)

    companion object {
        val DEFAULT = NotificationPrefs(
            masterEnabled = true,
            enabledByType = emptyMap(),
            minutesByType = emptyMap(),
        )
    }
}

class NotificationPreferences(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        fun enabled(t: NotificationTypeId) = booleanPreferencesKey("type_${t.key}_enabled")
        fun minutes(t: NotificationTypeId) = intPreferencesKey("type_${t.key}_minutes")
    }

    val flow: Flow<NotificationPrefs> = context.notificationDataStore.data.map { p ->
        val enabled = NotificationTypeId.values().associateWith { t ->
            p[Keys.enabled(t)] ?: t.defaultEnabled
        }
        val minutes = NotificationTypeId.values()
            .filter { it.hasTime }
            .associateWith { t -> p[Keys.minutes(t)] ?: (t.defaultMinutesOfDay!!) }
        NotificationPrefs(
            masterEnabled = p[Keys.MASTER_ENABLED] ?: true,
            enabledByType = enabled,
            minutesByType = minutes,
        )
    }

    suspend fun current(): NotificationPrefs = flow.first()

    suspend fun setMasterEnabled(enabled: Boolean) = update { it[Keys.MASTER_ENABLED] = enabled }

    suspend fun setTypeEnabled(t: NotificationTypeId, enabled: Boolean) =
        update { it[Keys.enabled(t)] = enabled }

    suspend fun setTypeMinutesOfDay(t: NotificationTypeId, minutes: Int) {
        require(t.hasTime) { "Type ${t.key} has no time" }
        update { it[Keys.minutes(t)] = minutes.coerceIn(0, 1439) }
    }

    // Back-compat setters used by existing Phase 4 tests.
    suspend fun setDailyReminderEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.DAILY_REMINDER, enabled)
    suspend fun setDailyReminderMinutes(minutes: Int) =
        setTypeMinutesOfDay(NotificationTypeId.DAILY_REMINDER, minutes)
    suspend fun setStreakRiskEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.STREAK_RISK, enabled)
    suspend fun setStreakRiskMinutes(minutes: Int) =
        setTypeMinutesOfDay(NotificationTypeId.STREAK_RISK, minutes)
    suspend fun setStreakFrozenEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.STREAK_FROZEN, enabled)
    suspend fun setStreakResetEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.STREAK_RESET, enabled)

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.notificationDataStore.edit { block(it) }
    }
}
```

- [ ] **Step 4: Update the existing `defaults match spec` test to use accessors**

In `NotificationPreferencesTest.kt`, replace the body of `defaults match spec`:

```kotlin
    @Test fun `defaults match spec`() = runTest {
        val snap = prefs.flow.first()
        assertEquals(true, snap.masterEnabled)
        assertEquals(true, snap.dailyReminderEnabled)
        assertEquals(9 * 60, snap.dailyReminderMinutes)
        assertEquals(true, snap.streakRiskEnabled)
        assertEquals(21 * 60, snap.streakRiskMinutes)
        assertEquals(true, snap.streakFrozenEnabled)
        assertEquals(true, snap.streakResetEnabled)
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.NotificationPreferencesTest"`
Expected: PASS, all green.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferences.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/NotificationPreferencesTest.kt
rtk git commit -m "feat(notifications): per-type Map + master switch in NotificationPreferences"
```

---

### Task 6: Extend NotificationFiringDateStore (new event keys)

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt`

- [ ] **Step 1: Add new event constants**

In `NotificationFiringDateStore.kt`, replace the `companion object` block with:

```kotlin
    companion object {
        const val EVENT_FROZEN = "day_boundary_frozen"
        const val EVENT_RESET = "day_boundary_reset"
        const val EVENT_TIER_ADVANCED = "tier_advanced"
        const val EVENT_MILESTONE_7 = "milestone_streak_7"
        const val EVENT_MILESTONE_30 = "milestone_streak_30"
        const val EVENT_MILESTONE_100 = "milestone_streak_100"
        const val EVENT_MILESTONE_365 = "milestone_streak_365"
        const val EVENT_CLOUD_RESTORE = "cloud_restore_complete"

        /** Per-identity event key for daily_reminder_per_identity. */
        fun perIdentityKey(identityId: String) = "per_identity_$identityId"
    }
```

- [ ] **Step 2: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationFiringDateStore.kt
rtk git commit -m "feat(notifications): new firing-date keys for milestone/tier/per-identity"
```

---

### Task 7: SyncFailureCounter (3-strike rule)

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounter.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounterTest.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class SyncFailureCounterTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `incrementAndShouldFire returns false until 3 strikes`() = runTest {
        val counter = SyncFailureCounter(context)
        counter.reset()
        assertFalse(counter.incrementAndShouldFire())
        assertFalse(counter.incrementAndShouldFire())
        assertTrue(counter.incrementAndShouldFire())
    }

    @Test fun `reset clears strikes`() = runTest {
        val counter = SyncFailureCounter(context)
        counter.incrementAndShouldFire()
        counter.incrementAndShouldFire()
        counter.reset()
        assertEquals(0, counter.current())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.SyncFailureCounterTest"`
Expected: FAIL with "unresolved reference: SyncFailureCounter".

- [ ] **Step 3: Create the counter**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounter.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncFailDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_failure_counter",
)

class SyncFailureCounter(private val context: Context) {
    private val key = intPreferencesKey("consecutive_failures")
    private val threshold = 3

    suspend fun current(): Int =
        context.syncFailDataStore.data.first()[key] ?: 0

    /** Increments the counter; returns true exactly when the threshold is crossed (3rd strike). */
    suspend fun incrementAndShouldFire(): Boolean {
        var fired = false
        context.syncFailDataStore.edit { prefs ->
            val next = (prefs[key] ?: 0) + 1
            prefs[key] = next
            if (next == threshold) fired = true
        }
        return fired
    }

    suspend fun reset() {
        context.syncFailDataStore.edit { it[key] = 0 }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.SyncFailureCounterTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounter.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/SyncFailureCounterTest.kt
rtk git commit -m "feat(notifications): SyncFailureCounter for 3-strike sync-failed-persistent notif"
```

---

### Task 8: MilestoneWorker (7/30/100/365 streak)

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorker.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorkerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorkerTest.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class MilestoneWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `doWork returns success or retry without crashing`() = runTest {
        val worker = TestListenableWorkerBuilder<MilestoneWorker>(context).build()
        val result = worker.doWork()
        assert(result == ListenableWorker.Result.success() || result == ListenableWorker.Result.retry())
    }

    @Test fun `milestoneFor returns matching threshold or null`() {
        assertEquals(7, MilestoneWorker.milestoneFor(7)?.days)
        assertEquals(30, MilestoneWorker.milestoneFor(30)?.days)
        assertEquals(100, MilestoneWorker.milestoneFor(100)?.days)
        assertEquals(365, MilestoneWorker.milestoneFor(365)?.days)
        assertNull(MilestoneWorker.milestoneFor(8))
        assertNull(MilestoneWorker.milestoneFor(0))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.MilestoneWorkerTest"`
Expected: FAIL with "unresolved reference: MilestoneWorker".

- [ ] **Step 3: Create the worker**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorker.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MilestoneWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.masterEnabled) return@runCatching Result.success()
        if (!prefs.isEnabled(NotificationTypeId.MILESTONE_STREAK)) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val summary = container.computeStreakUseCase.computeSummaryNow(userId)
        val milestone = milestoneFor(summary.currentStreak) ?: return@runCatching Result.success()

        val store = container.notificationFiringDateStore
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        if (store.getLastFired(milestone.firingKey) == today) return@runCatching Result.success()

        val body = "${milestone.days}-day streak — keep going."
        val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(applicationContext).notify(milestone.notifId, builder.build())
        store.setLastFired(milestone.firingKey, today)
        Result.success()
    }.getOrElse { Result.retry() }

    data class Milestone(val days: Int, val firingKey: String, val notifId: Int)

    companion object {
        val MILESTONES = listOf(
            Milestone(7,   NotificationFiringDateStore.EVENT_MILESTONE_7,   4011),
            Milestone(30,  NotificationFiringDateStore.EVENT_MILESTONE_30,  4012),
            Milestone(100, NotificationFiringDateStore.EVENT_MILESTONE_100, 4013),
            Milestone(365, NotificationFiringDateStore.EVENT_MILESTONE_365, 4014),
        )

        fun milestoneFor(streak: Int): Milestone? = MILESTONES.firstOrNull { it.days == streak }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.MilestoneWorkerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorker.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/MilestoneWorkerTest.kt
rtk git commit -m "feat(notifications): MilestoneWorker (7/30/100/365 streak)"
```

---

### Task 9: PerIdentityReminderScheduler + Worker

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderWorker.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderScheduler.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderSchedulerTest.kt`

- [ ] **Step 1: Write the failing scheduler test**

Create `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderSchedulerTest.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class PerIdentityReminderSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before fun setup() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
    }

    @Test fun `reconcile enqueues one work per identity id and cancels removed`() = runTest {
        val scheduler = PerIdentityReminderScheduler(context)
        scheduler.reconcile(setOf("id-a", "id-b"), minutesOfDay = 17 * 60 + 30)
        val wm = WorkManager.getInstance(context)
        val aInfos = wm.getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-a")).get()
        val bInfos = wm.getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-b")).get()
        assertEquals(1, aInfos.size)
        assertEquals(1, bInfos.size)

        scheduler.reconcile(setOf("id-b"), minutesOfDay = 17 * 60 + 30, previousIdentityIds = setOf("id-a", "id-b"))
        val aAfter = wm.getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-a")).get()
        assertTrue(aAfter.all { it.state.isFinished })
    }

    @Test fun `cancelAll cancels all tagged work`() = runTest {
        val scheduler = PerIdentityReminderScheduler(context)
        scheduler.reconcile(setOf("id-a"), minutesOfDay = 600)
        scheduler.cancelAll()
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(PerIdentityReminderScheduler.workName("id-a")).get()
        assertTrue(infos.all { it.state.isFinished })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.PerIdentityReminderSchedulerTest"`
Expected: FAIL with "unresolved reference: PerIdentityReminderScheduler".

- [ ] **Step 3: Create the worker**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderWorker.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.R
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Fires a per-identity nudge if the user logged no habits linked to the identity today.
 * Identity id is passed via input data under [KEY_IDENTITY_ID].
 */
class PerIdentityReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val identityId = inputData.getString(KEY_IDENTITY_ID) ?: return@runCatching Result.success()
        val app = applicationContext.applicationContext as HabitTrackerApplication
        val container = app.container
        val prefs = container.notificationPreferences.current()
        if (!prefs.masterEnabled) return@runCatching Result.success()
        if (!prefs.isEnabled(NotificationTypeId.DAILY_REMINDER_PER_IDENTITY)) return@runCatching Result.success()
        if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

        val userId = container.currentUserId()
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val start = today.atStartOfDayIn(tz)
        val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

        val identity = container.identityRepository.getAllIdentities()
            .firstOrNull { it.id == identityId } ?: return@runCatching Result.success()

        val habits = container.identityRepository
            .observeHabitsForIdentity(userId, identityId)
            .first()
        if (habits.isEmpty()) return@runCatching Result.success()

        val any = habits.any { h ->
            container.habitLogRepository
                .getActiveLogsForHabitOnDay(userId, h.id, start, end)
                .isNotEmpty()
        }
        if (any) return@runCatching Result.success()

        val store = container.notificationFiringDateStore
        val key = NotificationFiringDateStore.perIdentityKey(identityId)
        if (store.getLastFired(key) == today) return@runCatching Result.success()

        val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText("${identity.name} hasn't shown up today.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(applicationContext)
            .notify(NOTIF_BASE_ID + (identityId.hashCode() and 0xffff), builder.build())
        store.setLastFired(key, today)
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        const val KEY_IDENTITY_ID = "identity_id"
        const val NOTIF_BASE_ID = 5000
    }
}
```

- [ ] **Step 4: Create the scheduler**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderScheduler.kt`:

```kotlin
package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

class PerIdentityReminderScheduler(
    private val context: Context,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /**
     * Cancels any work for identities no longer in [activeIdentityIds],
     * then (re)enqueues a daily work for each active identity at [minutesOfDay].
     */
    fun reconcile(activeIdentityIds: Set<String>, minutesOfDay: Int, previousIdentityIds: Set<String> = emptySet()) {
        val wm = WorkManager.getInstance(context)
        val toCancel = previousIdentityIds - activeIdentityIds
        for (id in toCancel) wm.cancelUniqueWork(workName(id))
        for (id in activeIdentityIds) {
            wm.enqueueUniquePeriodicWork(
                workName(id),
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicAt(minutesOfDay, id),
            )
        }
    }

    fun cancel(identityId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(identityId))
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    private fun periodicAt(minutesOfDay: Int, identityId: String): PeriodicWorkRequest {
        val now = clock.now()
        val today = now.toLocalDateTime(timeZone).date
        val candidate = today.atStartOfDayIn(timeZone) + minutesOfDay.minutes
        val target = if (candidate > now) candidate
            else today.atStartOfDayIn(timeZone) + (minutesOfDay + 24 * 60).minutes
        val initialDelayMs = (target - now).inWholeMilliseconds.coerceAtLeast(0)
        return PeriodicWorkRequestBuilder<PerIdentityReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .setInputData(Data.Builder().putString(PerIdentityReminderWorker.KEY_IDENTITY_ID, identityId).build())
            .build()
    }

    companion object {
        const val TAG = "phase9-per-identity-reminder"
        fun workName(identityId: String) = "phase9-per-identity-$identityId"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.PerIdentityReminderSchedulerTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderWorker.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderScheduler.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/notifications/PerIdentityReminderSchedulerTest.kt
rtk git commit -m "feat(notifications): per-identity reminder worker + scheduler"
```

---

### Task 10: Rebind DailyReminderWorker + StreakRiskWorker

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt`

- [ ] **Step 1: Update DailyReminderWorker**

In `DailyReminderWorker.kt`, replace `doWork` body + `companion object`:

```kotlin
override suspend fun doWork(): Result = runCatching {
    val app = applicationContext.applicationContext as HabitTrackerApplication
    val container = app.container
    val prefs = container.notificationPreferences.current()
    if (!prefs.masterEnabled) return@runCatching Result.success()
    if (!prefs.isEnabled(NotificationTypeId.DAILY_REMINDER)) return@runCatching Result.success()
    if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

    val userId = container.currentUserId()
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val start = today.atStartOfDayIn(tz)
    val end = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)

    val count = container.habitLogRepository.countActiveLogsBetween(userId, start, end)
    if (count == 0) fireDailyReminder(applicationContext)
    Result.success()
}.getOrElse { Result.retry() }

companion object {
    const val NOTIF_ID = 4001
    fun fireDailyReminder(context: Context) {
        val builder = NotificationCompat.Builder(context, NotificationChannels.REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText("Time to log today's habits.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(context).notify(NOTIF_ID, builder.build())
    }
}
```

- [ ] **Step 2: Update StreakRiskWorker**

In `StreakRiskWorker.kt`, add the master-switch guard at the top of `doWork` immediately after `val prefs = ...`:

```kotlin
if (!prefs.masterEnabled) return@runCatching Result.success()
```

Replace the `NotificationCompat.Builder` call site with:

```kotlin
val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.ALERT)
    .setSmallIcon(R.drawable.ic_notification)
    .setContentTitle("Habitto")
    .setContentText("${summary.currentStreak}-day streak at risk — log before midnight.")
    .setAutoCancel(true)
    .setPriority(NotificationCompat.PRIORITY_HIGH)
```

- [ ] **Step 3: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run worker tests**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.DailyReminderWorkerTest" --tests "com.jktdeveloper.habitto.notifications.StreakRiskWorkerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DailyReminderWorker.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/StreakRiskWorker.kt
rtk git commit -m "refactor(notifications): rebind Daily + StreakRisk workers to grouped channels"
```

---

### Task 11: Extend DayBoundaryWorker with tier_advanced

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt`

- [ ] **Step 1: Add tier-advanced fire path**

Replace `doWork`, the existing `fire` helper, and `companion object` in `DayBoundaryWorker.kt`:

```kotlin
override suspend fun doWork(): Result = runCatching {
    val app = applicationContext.applicationContext as HabitTrackerApplication
    val container = app.container
    val prefs = container.notificationPreferences.current()
    if (!prefs.masterEnabled) return@runCatching Result.success()
    if (!PermissionUtils.hasNotificationPermission(applicationContext)) return@runCatching Result.success()

    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    val yesterday = today.minus(1, DateTimeUnit.DAY)

    val range = DateRange(start = yesterday, endExclusive = today.plus(1, DateTimeUnit.DAY))
    val userId = container.currentUserId()
    val result = container.computeStreakUseCase.computeNow(userId, range)
    val yesterdayState = result.days.firstOrNull { it.date == yesterday }?.state

    val firingStore = container.notificationFiringDateStore

    if (prefs.isEnabled(NotificationTypeId.STREAK_FROZEN) && yesterdayState == StreakDayState.FROZEN) {
        if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_FROZEN) != yesterday) {
            fire(applicationContext, NOTIF_FROZEN, NotificationChannels.STATUS,
                "Missed yesterday. Don't miss today, or your streak resets.",
                NotificationCompat.PRIORITY_LOW)
            firingStore.setLastFired(NotificationFiringDateStore.EVENT_FROZEN, yesterday)
        }
    }
    if (prefs.isEnabled(NotificationTypeId.STREAK_RESET) && yesterdayState == StreakDayState.BROKEN) {
        if (firingStore.getLastFired(NotificationFiringDateStore.EVENT_RESET) != yesterday) {
            fire(applicationContext, NOTIF_RESET, NotificationChannels.STATUS,
                "Streak reset. Start fresh today.",
                NotificationCompat.PRIORITY_LOW)
            firingStore.setLastFired(NotificationFiringDateStore.EVENT_RESET, yesterday)
        }
    }

    if (prefs.isEnabled(NotificationTypeId.TIER_ADVANCED)) {
        val summary = container.computeStreakUseCase.computeSummaryNow(userId)
        val currentTier = ExchangeRateCalculator.tierFor(summary.currentStreak)
        val yesterdayStreak = (summary.currentStreak - 1).coerceAtLeast(0)
        val previousTier = ExchangeRateCalculator.tierFor(yesterdayStreak)
        if (currentTier.level > previousTier.level &&
            firingStore.getLastFired(NotificationFiringDateStore.EVENT_TIER_ADVANCED) != today
        ) {
            fire(applicationContext, NOTIF_TIER, NotificationChannels.STATUS,
                "Tier ${currentTier.level} unlocked — ${currentTier.rate}× spending.",
                NotificationCompat.PRIORITY_DEFAULT)
            firingStore.setLastFired(NotificationFiringDateStore.EVENT_TIER_ADVANCED, today)
        }
    }

    Result.success()
}.getOrElse { Result.retry() }

private fun fire(context: Context, id: Int, channel: String, body: String, priority: Int) {
    val builder = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Habitto")
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(priority)
    NotificationManagerCompat.from(context).notify(id, builder.build())
}

companion object {
    const val NOTIF_FROZEN = 4003
    const val NOTIF_RESET = 4004
    const val NOTIF_TIER = 4005
}
```

Add the new import at the top of the file:

```kotlin
import com.habittracker.domain.usecase.ExchangeRateCalculator
```

- [ ] **Step 2: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run worker test**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.DayBoundaryWorkerTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/DayBoundaryWorker.kt
rtk git commit -m "feat(notifications): DayBoundaryWorker emits tier_advanced notif"
```

---

### Task 12: Update NotificationScheduler for new workers + master switch

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt`

- [ ] **Step 1: Replace cancelAll + reschedule + companion**

In `NotificationScheduler.kt`, replace `cancelAll`, `reschedule`, and the companion object with:

```kotlin
fun cancelAll() {
    val wm = WorkManager.getInstance(context)
    wm.cancelUniqueWork(WORK_DAILY)
    wm.cancelUniqueWork(WORK_RISK)
    wm.cancelUniqueWork(WORK_DAY_BOUNDARY)
    wm.cancelUniqueWork(WORK_MILESTONE)
    wm.cancelAllWorkByTag(PerIdentityReminderScheduler.TAG)
}

suspend fun reschedule() {
    val snap = prefs.current()
    val wm = WorkManager.getInstance(context)

    if (!snap.masterEnabled) {
        cancelAll()
        return
    }

    if (snap.isEnabled(NotificationTypeId.DAILY_REMINDER))
        wm.enqueueUniquePeriodicWork(
            WORK_DAILY,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicAt<DailyReminderWorker>(snap.minutesOfDay(NotificationTypeId.DAILY_REMINDER) ?: (9 * 60)),
        )
    else wm.cancelUniqueWork(WORK_DAILY)

    if (snap.isEnabled(NotificationTypeId.STREAK_RISK))
        wm.enqueueUniquePeriodicWork(
            WORK_RISK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicAt<StreakRiskWorker>(snap.minutesOfDay(NotificationTypeId.STREAK_RISK) ?: (21 * 60)),
        )
    else wm.cancelUniqueWork(WORK_RISK)

    val anyDayBoundary = snap.isEnabled(NotificationTypeId.STREAK_FROZEN)
        || snap.isEnabled(NotificationTypeId.STREAK_RESET)
        || snap.isEnabled(NotificationTypeId.TIER_ADVANCED)
    if (anyDayBoundary)
        wm.enqueueUniquePeriodicWork(
            WORK_DAY_BOUNDARY,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicAt<DayBoundaryWorker>(30),  // 00:30 local
        )
    else wm.cancelUniqueWork(WORK_DAY_BOUNDARY)

    if (snap.isEnabled(NotificationTypeId.MILESTONE_STREAK))
        wm.enqueueUniquePeriodicWork(
            WORK_MILESTONE,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicAt<MilestoneWorker>(35),  // 00:35 local
        )
    else wm.cancelUniqueWork(WORK_MILESTONE)
}

companion object {
    const val WORK_DAILY = "phase4-daily-reminder"
    const val WORK_RISK = "phase4-streak-risk"
    const val WORK_DAY_BOUNDARY = "phase4-day-boundary"
    const val WORK_MILESTONE = "phase9-milestone"
}
```

- [ ] **Step 2: Run scheduler tests**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.notifications.NotificationSchedulerTest"`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/notifications/NotificationScheduler.kt
rtk git commit -m "feat(notifications): scheduler honors master + wires MilestoneWorker"
```

---

### Task 13: AppContainer wires WantTimerRepository + sync observers

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`

- [ ] **Step 1: Add imports**

At the top of `AppContainer.kt`, add:

```kotlin
import com.habittracker.data.repository.LocalWantTimerRepository
import com.habittracker.data.repository.WantTimerRepository
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PerIdentityReminderScheduler
import com.jktdeveloper.habitto.notifications.SyncFailureCounter
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.notifications.NotificationFiringDateStore
import com.jktdeveloper.habitto.notifications.PermissionUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.datetime.toLocalDateTime
```

- [ ] **Step 2: Add new properties after `wantLogRepository`**

```kotlin
val wantTimerRepository: WantTimerRepository = LocalWantTimerRepository(db)
val perIdentityReminderScheduler = PerIdentityReminderScheduler(appContext)
val syncFailureCounter = SyncFailureCounter(appContext)
```

- [ ] **Step 3: Add sync-state notifier and call from init**

Inside `AppContainer`, add:

```kotlin
private fun startSyncNotifier() {
    applicationScope.launch {
        var sawAnyPullSinceLogin = false
        syncEngine.syncState.collect { state ->
            val prefs = notificationPreferences.current()
            if (!prefs.masterEnabled) return@collect
            if (!PermissionUtils.hasNotificationPermission(appContext)) return@collect
            when (state) {
                is SyncState.Synced -> {
                    syncFailureCounter.reset()
                    if (!sawAnyPullSinceLogin && state.pulled > 0
                        && prefs.isEnabled(NotificationTypeId.CLOUD_RESTORE_COMPLETE)
                    ) {
                        val today = kotlinx.datetime.Clock.System.now()
                            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                        val key = NotificationFiringDateStore.EVENT_CLOUD_RESTORE
                        if (notificationFiringDateStore.getLastFired(key) != today) {
                            fireSystemNotif(NOTIF_CLOUD_RESTORE,
                                "Cloud restore complete — your data is back.")
                            notificationFiringDateStore.setLastFired(key, today)
                        }
                    }
                    sawAnyPullSinceLogin = true
                }
                is SyncState.Error -> {
                    if (state.message == "Session expired"
                        && prefs.isEnabled(NotificationTypeId.SESSION_EXPIRED)
                    ) {
                        fireSystemNotif(NOTIF_SESSION_EXPIRED,
                            "Signed out — please sign in again to keep syncing.")
                    } else if (state.message != "Session expired"
                        && prefs.isEnabled(NotificationTypeId.SYNC_FAILED_PERSISTENT)
                    ) {
                        if (syncFailureCounter.incrementAndShouldFire()) {
                            fireSystemNotif(NOTIF_SYNC_FAILED,
                                "Sync has been failing — check your connection.")
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

private fun fireSystemNotif(id: Int, body: String) {
    val builder = NotificationCompat.Builder(appContext, NotificationChannels.SYSTEM)
        .setSmallIcon(com.jktdeveloper.habitto.R.drawable.ic_notification)
        .setContentTitle("Habitto")
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
    NotificationManagerCompat.from(appContext).notify(id, builder.build())
}

private companion object {
    const val NOTIF_SESSION_EXPIRED = 4101
    const val NOTIF_CLOUD_RESTORE = 4102
    const val NOTIF_SYNC_FAILED = 4103
}
```

Replace the existing `init { ... }` block with:

```kotlin
init {
    if (driverFactory.lastCreateWasWipe) {
        watermarks.reset()
    }
    startSessionGuard()
    startSyncNotifier()
}
```

- [ ] **Step 4: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt
rtk git commit -m "feat(notifications): AppContainer wires WantTimer + sync state notif observer"
```

---

### Task 14: AndroidManifest — register WantTimerService + foreground perms

**Files:**
- Modify: `mobile/androidApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Add permissions + service declaration**

In `AndroidManifest.xml`, after the existing `<uses-permission android:name="android.permission.WAKE_LOCK" />` line add:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

Inside `<application>` after the closing `</activity>` add:

```xml
<service
    android:name=".timer.WantTimerService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="want_timer_countdown" />
</service>
```

- [ ] **Step 2: Build to verify manifest merges**

Run: `rtk ./gradlew :androidApp:processDebugMainManifest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/AndroidManifest.xml
rtk git commit -m "feat(timer): register WantTimerService + foreground-service permissions"
```

---

### Task 15: WantTimerService (foreground) + WantTimerController

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerController.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerService.kt`
- Create: `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/timer/WantTimerControllerTest.kt`

- [ ] **Step 1: Write the failing controller test**

Create `mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/timer/WantTimerControllerTest.kt`:

```kotlin
package com.jktdeveloper.habitto.timer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.repository.LocalWantTimerRepository
import com.habittracker.domain.model.WantTimerState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class WantTimerControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, "test-want-timer.db")
    private val db = HabitTrackerDatabase(driver)
    private val repo = LocalWantTimerRepository(db)
    private val controller = WantTimerController(context, repo)

    @Test fun `start creates a RUNNING timer row`() = runTest {
        controller.start(userId = "u1", activityId = "a1", durationSec = 300)
        val active = repo.getActive("u1")
        assertEquals("a1", active?.activityId)
        assertEquals(WantTimerState.RUNNING, active?.state)
        assertEquals(300, active?.durationSec)
    }

    @Test fun `cancel marks the active timer CANCELLED`() = runTest {
        controller.start(userId = "u1", activityId = "a1", durationSec = 300)
        controller.cancel(userId = "u1")
        assertNull(repo.getActive("u1"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.timer.WantTimerControllerTest"`
Expected: FAIL with "unresolved reference: WantTimerController".

- [ ] **Step 3: Create the controller**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerController.kt`:

```kotlin
package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * UI-facing facade for starting/cancelling want timers.
 * Persists state via [WantTimerRepository], then nudges [WantTimerService] which owns
 * the foreground countdown notification.
 */
class WantTimerController(
    private val context: Context,
    private val repository: WantTimerRepository,
) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun start(userId: String, activityId: String, durationSec: Int) {
        require(durationSec in 1..(24 * 60 * 60))
        val now = Clock.System.now()
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

    suspend fun cancel(userId: String) {
        val active = repository.getActive(userId) ?: return
        repository.setState(active.id, WantTimerState.CANCELLED)
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

- [ ] **Step 4: Create the service**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerService.kt`:

```kotlin
package com.jktdeveloper.habitto.timer

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.jktdeveloper.habitto.MainActivity
import com.jktdeveloper.habitto.R
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class WantTimerService : LifecycleService() {

    private var tickJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
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
        }
        return START_NOT_STICKY
    }

    private fun startForegroundForTimer() {
        val n = buildRunningNotification(remaining = "starting…", activityName = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_RUNNING_ID, n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_RUNNING_ID, n)
        }
    }

    private suspend fun runUntilEnd(timerId: String) {
        val container = (applicationContext as HabitTrackerApplication).container
        val repo = container.wantTimerRepository
        val initial = repo.getById(timerId) ?: run {
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        val activity = container.wantActivityRepository
            .getAllWantActivitiesForUser(initial.userId)
            .firstOrNull { it.id == initial.activityId }
        val activityName = activity?.name ?: "Timer"

        while (true) {
            val current = repo.getById(timerId) ?: break
            if (current.state != WantTimerState.RUNNING) break
            val now = Clock.System.now()
            val remainingSec = (current.endsAt - now).inWholeSeconds.coerceAtLeast(0)
            if (remainingSec <= 0) {
                onTimerFinished(current, activityName)
                break
            }
            NotificationManagerCompat.from(applicationContext)
                .notify(NOTIF_RUNNING_ID, buildRunningNotification(formatMmSs(remainingSec.toInt()), activityName))
            val tickDelay = ((remainingSec % 60).coerceAtLeast(1) * 1000L).coerceAtMost(60_000L)
            delay(tickDelay)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun onTimerFinished(timer: WantTimer, activityName: String) {
        val container = (applicationContext as HabitTrackerApplication).container
        val repo = container.wantTimerRepository
        repo.setState(timer.id, WantTimerState.FINISHED)

        val activity = container.wantActivityRepository
            .getAllWantActivitiesForUser(timer.userId)
            .firstOrNull { it.id == timer.activityId }
        val pointsSegment: String = if (activity != null && activity.unit == "min") {
            val taps = (timer.durationSec / 60).coerceAtLeast(1)
            val result = container.logWantUseCase.execute(
                userId = timer.userId,
                activityId = timer.activityId,
                taps = taps,
                deviceMode = DeviceMode.THIS_DEVICE,
            )
            result.fold(
                onSuccess = { " · $taps min logged · −${it.pointsSpent} pt" },
                onFailure = { "" },
            )
        } else ""

        val prefs = container.notificationPreferences.current()
        val canFire = prefs.masterEnabled
            && prefs.isEnabled(NotificationTypeId.WANT_TIMER_END)
            && PermissionUtils.hasNotificationPermission(applicationContext)
        if (canFire) {
            val builder = NotificationCompat.Builder(applicationContext, NotificationChannels.WANT_TIMER_END)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Habitto")
                .setContentText("$activityName timer finished$pointsSegment")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(openAppPendingIntent())
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_END_ID, builder.build())
        }
    }

    private fun buildRunningNotification(remaining: String, activityName: String?): Notification {
        val cancelIntent = Intent(this, WantTimerService::class.java).apply { action = ACTION_STOP }
        val cancelPi = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = activityName?.let { "$it timer" } ?: "Want timer"
        return NotificationCompat.Builder(this, NotificationChannels.WANT_TIMER_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$remaining remaining")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPendingIntent())
            .addAction(0, "Cancel", cancelPi)
            .build()
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_START = "com.jktdeveloper.habitto.timer.START"
        const val ACTION_STOP = "com.jktdeveloper.habitto.timer.STOP"
        const val EXTRA_TIMER_ID = "timer_id"
        const val NOTIF_RUNNING_ID = 4201
        const val NOTIF_END_ID = 4202

        fun formatMmSs(totalSec: Int): String {
            val m = totalSec / 60
            val s = totalSec % 60
            return "%02d:%02d".format(m, s)
        }
    }
}
```

- [ ] **Step 5: Run controller test**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest --tests "com.jktdeveloper.habitto.timer.WantTimerControllerTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerService.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerController.kt mobile/androidApp/src/test/kotlin/com/jktdeveloper/habitto/timer/WantTimerControllerTest.kt
rtk git commit -m "feat(timer): WantTimerService (foreground) + WantTimerController"
```

---

### Task 16: WantTimerRecovery on app start

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerRecovery.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HabitTrackerApplication.kt`

- [ ] **Step 1: Create the recovery utility**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerRecovery.kt`:

```kotlin
package com.jktdeveloper.habitto.timer

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.usecase.LogWantUseCase
import com.jktdeveloper.habitto.R
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils
import kotlinx.datetime.Clock

/**
 * On app start, finalize any RUNNING timers whose endsAt has passed (OS killed the
 * service during low memory or the device rebooted). Resumes still-running ones
 * by re-launching the service.
 */
class WantTimerRecovery(
    private val context: Context,
    private val timerRepo: WantTimerRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val logWantUseCase: LogWantUseCase,
    private val notificationPreferences: NotificationPreferences,
) {
    suspend fun scanOnStart() {
        val now = Clock.System.now()
        val running = timerRepo.getAllRunning()
        for (t in running) {
            if (t.endsAt <= now) {
                timerRepo.setState(t.id, WantTimerState.FINISHED)
                val activity = wantActivityRepo
                    .getAllWantActivitiesForUser(t.userId)
                    .firstOrNull { it.id == t.activityId }
                val pointsSegment: String = if (activity != null && activity.unit == "min") {
                    val taps = (t.durationSec / 60).coerceAtLeast(1)
                    val result = logWantUseCase.execute(
                        userId = t.userId,
                        activityId = t.activityId,
                        taps = taps,
                        deviceMode = DeviceMode.THIS_DEVICE,
                    )
                    result.fold(
                        onSuccess = { " · $taps min logged · −${it.pointsSpent} pt" },
                        onFailure = { "" },
                    )
                } else ""
                postFinishedNotif(activity?.name ?: "Timer", pointsSegment)
            } else {
                val intent = Intent(context, WantTimerService::class.java).apply {
                    action = WantTimerService.ACTION_START
                    putExtra(WantTimerService.EXTRA_TIMER_ID, t.id)
                }
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            }
        }
    }

    private suspend fun postFinishedNotif(activityName: String, pointsSegment: String) {
        val prefs = notificationPreferences.current()
        if (!prefs.masterEnabled) return
        if (!prefs.isEnabled(NotificationTypeId.WANT_TIMER_END)) return
        if (!PermissionUtils.hasNotificationPermission(context)) return
        val builder = NotificationCompat.Builder(context, NotificationChannels.WANT_TIMER_END)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText("$activityName timer finished$pointsSegment")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        NotificationManagerCompat.from(context).notify(WantTimerService.NOTIF_END_ID, builder.build())
    }
}
```

- [ ] **Step 2: Wire it into HabitTrackerApplication**

Replace `HabitTrackerApplication.kt` body with:

```kotlin
package com.jktdeveloper.habitto

import android.app.Application
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.timer.WantTimerRecovery
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class HabitTrackerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureChannels(this)
        val recovery = WantTimerRecovery(
            context = this,
            timerRepo = container.wantTimerRepository,
            wantActivityRepo = container.wantActivityRepository,
            logWantUseCase = container.logWantUseCase,
            notificationPreferences = container.notificationPreferences,
        )
        GlobalScope.launch(Dispatchers.Default) {
            runCatching { recovery.scanOnStart() }
        }
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/timer/WantTimerRecovery.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/HabitTrackerApplication.kt
rtk git commit -m "feat(timer): app-start recovery of orphaned RUNNING timers"
```

---

### Task 17: WantDetailViewModel — observe active timer + start/cancel

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt`

- [ ] **Step 1: Add imports + extend UI state + add start/cancel**

In `WantDetailViewModel.kt`, add imports:

```kotlin
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.WantTimer
import com.jktdeveloper.habitto.timer.WantTimerController
import com.jktdeveloper.habitto.timer.WantTimerService
import kotlinx.coroutines.delay
```

Replace `WantDetailUi` with:

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
    val showDurationSheet: Boolean = false,
)
```

Replace the class header + constructors + init + remove `onTimerStub`. New class shell:

```kotlin
class WantDetailViewModel private constructor(
    private val activityId: String,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val timerController: WantTimerController,
    private val timerRepo: WantTimerRepository,
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
        timerController = WantTimerController(container.appContext, container.wantTimerRepository),
        timerRepo = container.wantTimerRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { reload(); observeTimer() }

    fun showDurationSheet() { _state.update { it.copy(showDurationSheet = true) } }
    fun dismissDurationSheet() { _state.update { it.copy(showDurationSheet = false) } }

    fun startTimer(durationSec: Int) {
        viewModelScope.launch {
            timerController.start(userIdProvider(), activityId, durationSec)
            _state.update { it.copy(showDurationSheet = false) }
        }
    }

    fun cancelTimer() {
        viewModelScope.launch { timerController.cancel(userIdProvider()) }
    }

    private fun observeTimer() {
        viewModelScope.launch {
            while (true) {
                val active = timerRepo.getActive(userIdProvider())
                val remainingMmSs = active?.let {
                    val remainSec = (it.endsAt - clock.now()).inWholeSeconds.coerceAtLeast(0).toInt()
                    WantTimerService.formatMmSs(remainSec)
                }
                _state.update { it.copy(activeTimer = active, timerRemainingMmSs = remainingMmSs) }
                delay(1000L)
            }
        }
    }

    // Existing methods preserved verbatim:
    // - reload()
    // - consumeToast()
    // - hide()
    // - delete()
    // Delete the old onTimerStub() method entirely.
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailViewModel.kt
rtk git commit -m "feat(timer): WantDetailViewModel observes active timer + start/cancel"
```

---

### Task 18: WantDetailScreen — running banner + duration sheet

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt`

- [ ] **Step 1: Add running banner + Start-timer entry**

Inside `WantDetailScreen`, between the `HeroCard(...)` block and the `Spacer(Modifier.height(8.dp))` line that precedes the `FilledTonalButton(onClick = onEdit, ...)`, insert:

```kotlin
state.activeTimer?.let { _ ->
    Spacer(Modifier.height(8.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Timer running · ${state.timerRemainingMmSs ?: "--:--"}",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = viewModel::cancelTimer) {
                Text("Cancel", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

if (state.activeTimer == null) {
    Spacer(Modifier.height(8.dp))
    FilledTonalButton(
        onClick = viewModel::showDurationSheet,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(48.dp),
    ) {
        Text("Start timer", fontWeight = FontWeight.SemiBold)
    }
}
```

- [ ] **Step 2: Add duration bottom sheet at the end of the Scaffold lambda**

Below the existing `if (pendingDelete) { AlertDialog(...) }` block, add:

```kotlin
if (state.showDurationSheet) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = viewModel::dismissDurationSheet) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("How long?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            val durations = listOf(5, 10, 15, 20, 30, 60)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                durations.forEach { mins ->
                    androidx.compose.material3.AssistChip(
                        onClick = { viewModel.startTimer(mins * 60) },
                        label = { Text("$mins min") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/want/WantDetailScreen.kt
rtk git commit -m "feat(timer): WantDetail running banner + duration bottom sheet"
```

---

### Task 19: NotificationsSettings screen + VM

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsViewModel.kt`
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsScreen.kt`

- [ ] **Step 1: Create the VM**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsViewModel.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.notifications.NotificationCategory
import com.jktdeveloper.habitto.notifications.NotificationPrefs
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsSettingsViewModel(private val container: AppContainer) : ViewModel() {

    val prefs: StateFlow<NotificationPrefs> = container.notificationPreferences.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationPrefs.DEFAULT)

    fun setMaster(enabled: Boolean) {
        viewModelScope.launch {
            container.notificationPreferences.setMasterEnabled(enabled)
            container.notificationScheduler.reschedule()
        }
    }

    fun setTypeEnabled(t: NotificationTypeId, enabled: Boolean) {
        viewModelScope.launch {
            container.notificationPreferences.setTypeEnabled(t, enabled)
            container.notificationScheduler.reschedule()
        }
    }

    fun setTypeMinutes(t: NotificationTypeId, minutes: Int) {
        viewModelScope.launch {
            container.notificationPreferences.setTypeMinutesOfDay(t, minutes)
            container.notificationScheduler.reschedule()
        }
    }

    val types: List<NotificationTypeId> = NotificationTypeId.values().toList()
    val categories: List<NotificationCategory> = NotificationCategory.values().toList()
}
```

- [ ] **Step 2: Create the screen**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsScreen.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(viewModel: NotificationsSettingsViewModel, onBack: () -> Unit) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current
    val hasPermission = remember(prefs) { PermissionUtils.hasNotificationPermission(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!hasPermission) {
                BannerCard(
                    text = "Notifications blocked by Android.",
                    actionLabel = "Open system Settings",
                    onAction = { PermissionUtils.openAppNotificationSettings(context) },
                )
            }

            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Text("All notifications", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(checked = prefs.masterEnabled, onCheckedChange = { viewModel.setMaster(it) })
                }
            }

            if (!prefs.masterEnabled) {
                Text(
                    "Notifications muted",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (cat in viewModel.categories) {
                Spacer(Modifier.height(12.dp))
                Text(
                    cat.displayName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                )
                for (t in viewModel.types.filter { it.category == cat }) {
                    TypeRow(
                        type = t,
                        enabled = prefs.isEnabled(t),
                        minutesOfDay = prefs.minutesOfDay(t),
                        masterEnabled = prefs.masterEnabled,
                        onToggle = { viewModel.setTypeEnabled(t, it) },
                        onMinutes = { viewModel.setTypeMinutes(t, it) },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BannerCard(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Text(text, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeRow(
    type: NotificationTypeId,
    enabled: Boolean,
    minutesOfDay: Int?,
    masterEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val pickerState = remember(minutesOfDay) {
        TimePickerState(
            initialHour = (minutesOfDay ?: 0) / 60,
            initialMinute = (minutesOfDay ?: 0) % 60,
            is24Hour = true,
        )
    }
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(type.key.replace('_', ' ').replaceFirstChar { it.uppercase() })
                if (type.hasTime && minutesOfDay != null) {
                    TextButton(onClick = { showTimePicker = true }, enabled = enabled && masterEnabled) {
                        Text("at ${"%02d".format(minutesOfDay / 60)}:${"%02d".format(minutesOfDay % 60)}")
                    }
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = masterEnabled)
        }
    }
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onMinutes(pickerState.hour * 60 + pickerState.minute)
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = pickerState) },
        )
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsScreen.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/NotificationsSettingsViewModel.kt
rtk git commit -m "feat(notifications): NotificationsSettings screen + VM"
```

---

### Task 20: NotificationPermissionPrompt (first launch)

**Files:**
- Create: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/NotificationPermissionPrompt.kt`
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/MainActivity.kt`

- [ ] **Step 1: Create the composable**

Create `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/NotificationPermissionPrompt.kt`:

```kotlin
package com.jktdeveloper.habitto.ui.onboarding

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.jktdeveloper.habitto.notifications.PermissionUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.permissionPromptStore: DataStore<Preferences> by preferencesDataStore("notif_prompt")
private val KEY_SKIPPED = booleanPreferencesKey("permission_prompt_skipped")

@Composable
fun NotificationPermissionPromptHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (PermissionUtils.hasNotificationPermission(context)) return@LaunchedEffect
        val skipped = context.permissionPromptStore.data.first()[KEY_SKIPPED] ?: false
        if (!skipped) visible = true
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { visible = false },
    )
    if (visible) {
        AlertDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = { Text("Stay on top of your habits") },
            text = {
                Text("Enable notifications for daily reminders, streak alerts, and timer completions.")
            },
            confirmButton = {
                TextButton(onClick = { launcher.launch(PermissionUtils.PERMISSION_NAME) }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        context.permissionPromptStore.edit { it[KEY_SKIPPED] = true }
                    }
                    visible = false
                }) { Text("Skip") }
            },
        )
    }
}
```

- [ ] **Step 2: Mount the prompt host from MainActivity**

In `MainActivity.kt`, replace the `setContent { ... }` block with:

```kotlin
setContent {
    HabitTrackerTheme {
        AppNavigation(container = container)
        com.jktdeveloper.habitto.ui.onboarding.NotificationPermissionPromptHost()
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/onboarding/NotificationPermissionPrompt.kt mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/MainActivity.kt
rtk git commit -m "feat(notifications): first-launch permission prompt"
```

---

### Task 21: Wire NotificationsSettings into navigation + Settings entry

**Files:**
- Modify: existing navigation graph file (find via `rtk grep -rn 'NavHost\|composable(' mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/`)
- Modify: existing Settings or You hub screen file.

- [ ] **Step 1: Locate the navigation graph + Settings entry**

Run: `rtk grep -rn "NavHost\\|composable(" mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/`
Expected: list of routes; identify the file containing the `NavHost` block.

Run: `rtk grep -rn "Sign out\\|Notifications" mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/ mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/ 2>/dev/null`
Expected: file containing the Settings list.

- [ ] **Step 2: Add the route to the NavHost block**

Inside the `NavHost` block (matching the existing `composable("settings") { ... }` style), add:

```kotlin
composable("notifications-settings") {
    val vm = remember { NotificationsSettingsViewModel(container) }
    NotificationsSettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
}
```

Add imports at the top of the navigation file:

```kotlin
import com.jktdeveloper.habitto.ui.settings.NotificationsSettingsScreen
import com.jktdeveloper.habitto.ui.settings.NotificationsSettingsViewModel
```

- [ ] **Step 3: Add the entry row in the Settings screen**

Inside the Settings screen's main Column, above the "Sign out" row (use the project's existing row pattern — read the file first to match):

```kotlin
ListItem(
    headlineContent = { Text("Notifications") },
    supportingContent = { Text("Daily reminders, alerts, status, system") },
    modifier = Modifier.clickable { navController.navigate("notifications-settings") },
)
```

- [ ] **Step 4: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/ mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/settings/ mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/you/
rtk git commit -m "feat(notifications): NotificationsSettings route + Settings entry"
```

---

### Task 22: Per-identity reminder reconcile on identity change

**Files:**
- Modify: `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`

- [ ] **Step 1: Add observer + reconciler**

In `AppContainer.kt`, add import:

```kotlin
import kotlinx.coroutines.flow.combine
```

Append inside the existing `init { ... }` block (after `startSyncNotifier()`):

```kotlin
startPerIdentityReconciler()
```

Add the new method to the class:

```kotlin
private fun startPerIdentityReconciler() {
    applicationScope.launch {
        var previous: Set<String> = emptySet()
        combine(
            identityRepository.observeUserIdentities(currentUserId()),
            notificationPreferences.flow,
        ) { ids, prefs -> ids to prefs }
            .collect { (identities, prefs) ->
                val active = if (
                    prefs.masterEnabled
                    && prefs.isEnabled(NotificationTypeId.DAILY_REMINDER_PER_IDENTITY)
                ) identities.map { it.id }.toSet() else emptySet()
                val minutes = prefs.minutesOfDay(NotificationTypeId.DAILY_REMINDER_PER_IDENTITY) ?: (17 * 60 + 30)
                perIdentityReminderScheduler.reconcile(active, minutes, previous)
                previous = active
            }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `rtk ./gradlew :androidApp:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
rtk git add mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt
rtk git commit -m "feat(notifications): reconcile per-identity reminders on identity changes"
```

---

### Task 23: Full build + Robolectric test sweep + manual smoke

**Files:** (none — verification only)

- [ ] **Step 1: Full Android build**

Run: `rtk ./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Full unit-test sweep**

Run: `rtk ./gradlew :androidApp:testDebugUnitTest :shared:jvmTest`
Expected: PASS, all green.

- [ ] **Step 3: Manual smoke on device/emulator**

Install on emulator (API 33+):
1. Clear app data.
2. Launch app → permission prompt appears → tap **Allow** → system dialog appears → grant.
3. Complete onboarding with at least one identity + one habit + one want activity with unit "min".
4. Open want detail → **Start timer** → pick 5 min.
5. Pull notification shade → confirm "Want timer · 05:00 remaining" on LOW-importance channel.
6. Wait until completion (or use a shorter duration to speed up).
7. Confirm completion notification on WANT_TIMER_END channel + WantLog row was inserted (visit `WantDetail` → Recent activity shows the auto-logged row).
8. Go to **Settings → Notifications**. Toggle master off — confirm all type rows dim. Toggle on. Toggle one individual type. Pick a time for `daily_reminder`.
9. Open Android system app-info → Notifications: confirm 6 channels visible (Reminders, Alerts, Status updates, System, Want timer (running), Want timer (end)).

- [ ] **Step 4: Confirm tree is clean**

Run: `rtk git status`
Expected: working tree clean.

- [ ] **Step 5: Push branch**

```bash
rtk git push -u origin feature/phase9-notifications-timer
```

---

## Self-Review notes

**Spec coverage:**
- Want timer domain + schema migration 9 → Tasks 1, 2.
- WantTimerService with mm:ss countdown + auto-log on unit=="min" + recovery → Tasks 15, 16.
- 11 notification types in 4 grouped channels → Tasks 3, 4.
- NotificationPreferences master + per-type Map + time picker → Tasks 5, 19.
- DailyReminderWorker + StreakRiskWorker rebind → Task 10.
- DayBoundaryWorker streak_frozen + streak_reset (existing) + tier_advanced (new) → Task 11.
- MilestoneWorker (7/30/100/365) → Task 8.
- PerIdentityReminderScheduler + Worker + identity-change reconcile → Tasks 9, 22.
- SyncEngine hooks for session_expired, cloud_restore_complete, sync_failed_persistent → Tasks 7, 13.
- NotificationsSettings (grouped sections, permission-blocked banner, master-off dimmed) → Task 19.
- NotificationPermissionPrompt (first launch + skip-remember) → Task 20.
- Settings entry in You hub → Task 21.
- AndroidManifest service + permissions → Task 14.
- Tests covering WantTimer repo, MilestoneWorker, PerIdentityReminderScheduler, NotificationPreferences, SyncFailureCounter, WantTimerController → Tasks 2, 5, 7, 8, 9, 15.

**Out of scope (per spec):** bar_raised/bar_dropped (Phase 10), iOS notifications, widgets, snooze actions, custom sound config, cross-device timer sync — no tasks.

**Type consistency:** Names defined in early tasks reused in later tasks:
- `NotificationTypeId.{WANT_TIMER_END, MILESTONE_STREAK, DAILY_REMINDER_PER_IDENTITY, TIER_ADVANCED, SESSION_EXPIRED, CLOUD_RESTORE_COMPLETE, SYNC_FAILED_PERSISTENT}` → defined Task 3, used Tasks 8, 9, 10, 11, 13, 15, 16, 19, 22.
- `WantTimer`, `WantTimerState.{RUNNING,FINISHED,CANCELLED}` → defined Task 2, used Tasks 15, 16, 17.
- `NotificationChannels.{REMINDER,ALERT,STATUS,SYSTEM,WANT_TIMER_RUNNING,WANT_TIMER_END}` → defined Task 4, used Tasks 8, 9, 10, 11, 13, 15, 16.
- `WantTimerService.{ACTION_START,ACTION_STOP,EXTRA_TIMER_ID,NOTIF_RUNNING_ID,NOTIF_END_ID,formatMmSs}` → defined Task 15, used Tasks 14, 16, 17.
- `PerIdentityReminderScheduler.{TAG,workName,reconcile,cancel,cancelAll}` → defined Task 9, used Tasks 12, 22.
- `WantTimerRepository.{insert,startReplacing,getActive,getById,setState,getAllRunning}` → defined Task 2, used Tasks 13, 15, 16, 17.
- `NotificationFiringDateStore.{EVENT_TIER_ADVANCED, EVENT_MILESTONE_*, EVENT_CLOUD_RESTORE, perIdentityKey}` → defined Task 6, used Tasks 8, 9, 11, 13.
