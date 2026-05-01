# Phase 5c-1-fix — Stale State Bug Fixes

> **Mini bug-fix phase off main.** Branch: `feature/phase5c-1-fix`. Worktree: `.worktrees/phase5c-1-fix`.

## Goal

Fix two stale-state bugs surfaced during Phase 5c-1 smoke testing:

1. **Day rollover (Home):** When the user keeps the app foreground across midnight, yesterday's habit checkmarks and progress remain on screen until manual reload.
2. **JWT expiry (sync):** When the Supabase session expires, the app shows a generic "Session expired" toast but leaves the user on Home with no path to re-authenticate.

## Non-Goals

- Background midnight rollover (already handled by `DayBoundaryWorker` for notifications).
- Mid-foreground rollover for IdentityList / IdentityDetail / StreakHistory. These already refresh on resume; users keeping them foreground past midnight see stale data until next resume — acceptable for v1.
- Soft re-login banner / read-only browse after expiry.
- Refresh-token rotation logic (delegated to supabase-kt SDK).
- Sign-in conflict 3-way merge dialog (deferred separately).

## Architecture

Two independent fixes sharing one mini-phase. They do not share code paths.

**Bug 1** introduces a reactive day-boundary `Flow<LocalDate>` consumed by `HomeViewModel`. The flow re-emits when calendar day flips. `flatMapLatest` cancels the prior `combine` block and rebuilds it with fresh `dayStart`/`dayEnd` bounds.

**Bug 2** routes JWT failures through a single guard inside `AppContainer`. The guard observes `SyncEngine.syncState`. On `Error("Session expired")` it attempts one explicit `client.auth.refreshCurrentSession()`. If refresh succeeds, retry sync once. If refresh fails, perform a hard sign-out (clear local session, flip authState) and emit a one-shot `sessionExpiredEvents` signal. `AppNavigation` collects that signal, navigates to `Screen.Auth`, and surfaces a toast.

## Components

### Bug 1 — Reactive Day Boundary

**New file:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/util/DayBoundaryFlow.kt`

```kotlin
package com.jktdeveloper.habitto.util

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Emits today's [LocalDate] now and re-emits each time the local calendar day flips.
 * Foreground-only by design: the delay pauses with the host coroutine scope.
 */
fun dayBoundaryFlow(
    tz: TimeZone = TimeZone.currentSystemDefault(),
    clock: Clock = Clock.System,
): Flow<LocalDate> = flow {
    while (currentCoroutineContext().isActive) {
        val now = clock.now()
        val today = now.toLocalDateTime(tz).date
        emit(today)
        val nextMidnight = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
        delay(nextMidnight - now)
    }
}
```

**Modified:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/home/HomeViewModel.kt`

In `observeHomeUiState()`, replace the captured `now/todayDate/dayStart/dayEnd` with the day flow as the outer Flow:

```kotlin
container.authState
    .flatMapLatest { auth ->
        dayBoundaryFlow().flatMapLatest { todayDate ->
            val dayStart = todayDate.atStartOfDayIn(TimeZone.UTC)
            val dayEnd = dayStart + 1.days
            val weekStart = weekStartUtcFor(todayDate)

            combine(
                container.habitRepository.observeHabitsForUser(auth.userId),
                container.habitLogRepository.observeAllActiveLogsForUser(auth.userId),
                container.wantActivityRepository.observeWantActivities(auth.userId),
                container.wantLogRepository.observeAllActiveLogsForUser(auth.userId),
            ) { habits, habitLogs, wants, wantLogs ->
                /* existing body, now using fresh dayStart/dayEnd/weekStart */
            }
        }
    }
    .collect { _uiState.value = it }
```

Same pattern in `observeStreaks()`: wrap the `range` computation inside `dayBoundaryFlow().flatMapLatest { today -> ... }`.

`weekStartUtc()` is refactored to take `today` as a parameter (`weekStartUtcFor(today)`) so it stays pure.

### Bug 2 — JWT Expiry Guard

**Modified:** `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/AuthRepository.kt`

```kotlin
interface AuthRepository {
    // ... existing methods ...
    suspend fun tryRefreshSession(): Result<Unit>
}
```

**Modified:** `mobile/shared/src/commonMain/kotlin/com/habittracker/data/repository/SupabaseAuthRepository.kt`

```kotlin
override suspend fun tryRefreshSession(): Result<Unit> = runCatching {
    client.auth.refreshCurrentSession()
}
```

**Modified:** `mobile/shared/src/commonTest/kotlin/com/habittracker/data/repository/FakeAuthRepository.kt`

Add a configurable `refreshSucceeds` flag (default `true`); `tryRefreshSession()` returns `Result.success(Unit)` or `Result.failure(...)` based on it.

**Modified:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/AppContainer.kt`

```kotlin
private val _sessionExpiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val sessionExpiredEvents: SharedFlow<Unit> = _sessionExpiredEvents.asSharedFlow()

private fun startSessionGuard(scope: CoroutineScope) {
    scope.launch {
        syncEngine.syncState
            .filterIsInstance<SyncState.Error>()
            .filter { it.message == "Session expired" }
            .distinctUntilChanged()
            .collect { handleSessionExpired() }
    }
}

private suspend fun handleSessionExpired() {
    if (!isAuthenticated()) return
    val refresh = authRepository.tryRefreshSession()
    if (refresh.isSuccess) {
        runCatching { syncEngine.sync(SyncReason.MANUAL) }
        return
    }
    val userId = currentUserId()
    runCatching { clearAuthenticatedUserData(userId) }
    runCatching { authRepository.signOut() }
    refreshAuthState()
    _sessionExpiredEvents.tryEmit(Unit)
}
```

`startSessionGuard(applicationScope)` is invoked from `AppContainer.init` (or wherever `applicationScope` is constructed; spec assumes one already exists for sync triggers).

**Modified:** `mobile/androidApp/src/androidMain/kotlin/com/jktdeveloper/habitto/ui/navigation/AppNavigation.kt`

```kotlin
val context = LocalContext.current
LaunchedEffect(Unit) {
    container.sessionExpiredEvents.collect {
        Toast.makeText(context, "Session expired — sign in again", Toast.LENGTH_LONG).show()
        navController.navigate(Screen.Auth.route) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
}
```

`SyncEngine` is **not** modified. Its existing `categorize()` already labels the failure correctly. Recovery is layered on top.

## Data Flow

### Bug 1
```
dayBoundaryFlow → today (LocalDate)
    ↓ flatMapLatest
combine(habits, habitLogs, wants, wantLogs)
    ↓
HomeUiState (with dayStart/dayEnd derived from current `today`)
```
At midnight: `dayBoundaryFlow` emits the new date. `flatMapLatest` cancels the inner `combine` and rebuilds with new bounds. Pending tap state in `_pending`/`_pendingWants` is unaffected (separate `MutableStateFlow`s, owned by `viewModelScope`, not the recomputed pipeline).

### Bug 2
```
SyncEngine.syncState.collect → Error("Session expired")
    ↓ AppContainer guard
authRepository.tryRefreshSession()
    ├─ success → syncEngine.sync(MANUAL); user sees nothing
    └─ failure → clearAuthenticatedUserData + signOut + refreshAuthState
                 → _sessionExpiredEvents.tryEmit(Unit)
                 ↓ AppNavigation collector
                 Toast + navigate(Auth) popUpTo(graph)
```

## Error Handling

- **Bug 1:** No new failure modes. `dayBoundaryFlow` is an infinite producer; cancellation propagates from `viewModelScope`.
- **Bug 2:**
  - `tryRefreshSession()` swallows network errors as `Result.failure` and falls through to sign-out — acceptable: if the user is offline AND token expired, they cannot proceed anyway. AuthScreen will let them retry once network returns.
  - Sign-out steps are wrapped in `runCatching` so a failure in one (e.g. clearing local data) does not block the others.
  - Guard de-dupe via `distinctUntilChanged` prevents re-entry while sync state remains in the same Error state.
  - Guard skips when `!isAuthenticated()` to avoid stomping a user who already initiated manual sign-out.

## Testing

**Unit tests (commonTest):**
- `DayBoundaryFlowTest` — using `kotlinx-coroutines-test` virtual time:
  - First emission equals expected date.
  - Advancing virtual time past next midnight produces second emission.
  - Cancelling scope stops emissions.
- `FakeAuthRepository` updated for `tryRefreshSession()`. Existing tests stay green.

**Manual smoke (pre-merge checklist):**
- [ ] Foreground app at 23:59, wait until 00:01 → habit checks reset, points/progress reflect new day, 7d strip slides.
- [ ] Cold-start at 00:05 → today's date correct.
- [ ] Pending tap countdown active when day flips → tap commits to old day OR cancels (document actual behavior; no crash).
- [ ] Trigger 401 (revoke session in Supabase dashboard, then attempt sync) → app navigates to AuthScreen with toast.
- [ ] 401 with healthy refresh token → silent recovery (no toast, sync succeeds).
- [ ] 401 while offline → still navigates to AuthScreen, user sees retry on AuthScreen.
- [ ] Manual sign-out from Settings still works (guard does not interfere).
- [ ] After re-auth from expiry route → Home loads cleanly.

## Migration / Rollout

- No schema changes.
- No data migration.
- Single APK deploy.

## Open Questions

None. Design locked.
