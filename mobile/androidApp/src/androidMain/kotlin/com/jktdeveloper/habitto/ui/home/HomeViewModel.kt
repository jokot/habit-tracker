package com.jktdeveloper.habitto.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.sync.SyncTriggers
import com.habittracker.data.sync.SyncReason
import com.habittracker.data.sync.SyncState
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.PointBalance
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.isTimed
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.habittracker.domain.usecase.InsufficientPointsException
import com.habittracker.domain.usecase.LogHabitStatus
import com.habittracker.domain.usecase.PointCalculator
import com.jktdeveloper.habitto.timer.CancelResult
import com.jktdeveloper.habitto.timer.StartTimerOutcome
import com.jktdeveloper.habitto.timer.WantTimerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.jktdeveloper.habitto.util.dayBoundaryFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

data class HomeUiState(
    val habitsWithProgress: List<HabitWithProgress> = emptyList(),
    val pointBalance: PointBalance = PointBalance(0, 0, 0),
    val wantActivities: List<WantActivity> = emptyList(),
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

/** One pending habit log: N taps accumulated, seconds remaining before commit. */
data class PendingHabitLog(
    val count: Int,
    val secondsRemaining: Int,
)

/** One pending want log: N taps accumulated, seconds remaining before commit. */
data class PendingWantLog(
    val count: Int,
    val secondsRemaining: Int,
)

sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
    /** A timer just started from Home; the screen navigates to it. */
    data class OpenTimer(val activityId: String) : HomeEvent
}

/** A want whose timer Home offered to start, and the other timer standing in the way. */
data class HomeOverlap(
    val activity: WantActivity,
    val otherWantName: String,
    val elapsedMin: Int,
    val minutesLeft: Int,
    val desiredDurationSec: Int,
)

/** Active Want timer, surfaced on Home regardless of which want it belongs to. */
data class HomeTimerUi(
    val activityId: String,
    val wantName: String,
    val remainingMmSs: String,
)

private const val PENDING_WINDOW_SECONDS = 3

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val syncState: StateFlow<SyncState> = container.syncEngine.syncState

    private val _streakStrip = MutableStateFlow(
        com.habittracker.domain.model.StreakRangeResult(emptyList(), null)
    )
    val streakStrip: StateFlow<com.habittracker.domain.model.StreakRangeResult> = _streakStrip.asStateFlow()

    private val _streakSummary = MutableStateFlow(
        com.habittracker.domain.model.StreakSummary(0, 0, 0, null)
    )
    val streakSummary: StateFlow<com.habittracker.domain.model.StreakSummary> = _streakSummary.asStateFlow()

    val currentRate: StateFlow<Double> = streakSummary
        .map { ExchangeRateCalculator.rateFor(it.currentStreak) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    val showRateLadderBanner: StateFlow<Boolean> = combine(
        container.appFlagsPreferences.seenRateLadderUpgradeBanner,
        container.wantLogRepository.observeAllActiveLogsForUser(container.currentUserId()),
    ) { seen, logs -> !seen && logs.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun markRateLadderBannerSeen() {
        viewModelScope.launch {
            container.appFlagsPreferences.setSeenRateLadderUpgradeBanner(true)
        }
    }

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    private val _logoutUnsyncedCount = MutableStateFlow(0)
    val logoutUnsyncedCount: StateFlow<Int> = _logoutUnsyncedCount.asStateFlow()

    val userIdentities: StateFlow<List<Identity>> =
        container.getUserIdentitiesUseCase.execute(container.currentUserId())
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _pinnedIdentityId = MutableStateFlow<String?>(null)
    val pinnedIdentityId: StateFlow<String?> = _pinnedIdentityId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePinnedIdentity() {
        viewModelScope.launch {
            container.authState.flatMapLatest { auth ->
                container.identityRepository.observeUserIdentities(auth.userId).map {
                    container.identityRepository.getPinnedIdentityIdForUser(auth.userId)
                }
            }.collect { _pinnedIdentityId.value = it }
        }
    }

    /** habitId → pending tap batch. Drops to empty on commit or cancel. */
    private val _pending = MutableStateFlow<Map<String, PendingHabitLog>>(emptyMap())
    val pending: StateFlow<Map<String, PendingHabitLog>> = _pending.asStateFlow()

    /** want activityId → pending tap batch. Drops to empty on commit or cancel. */
    private val _pendingWants = MutableStateFlow<Map<String, PendingWantLog>>(emptyMap())
    val pendingWants: StateFlow<Map<String, PendingWantLog>> = _pendingWants.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    /** One countdown coroutine per habit. */
    private val timers = mutableMapOf<String, Job>()

    /** One countdown coroutine per want activity. */
    private val wantTimers = mutableMapOf<String, Job>()

    private val _homeTimer = MutableStateFlow<HomeTimerUi?>(null)
    val homeTimer: StateFlow<HomeTimerUi?> = _homeTimer.asStateFlow()

    private var homeTimerJob: Job? = null

    private fun observeActiveWantTimer() {
        homeTimerJob = viewModelScope.launch {
            while (true) {
                val userId = container.currentUserId()
                val active = container.wantTimerRepository.getActive(userId)
                if (active == null) {
                    _homeTimer.value = null
                } else {
                    val want = container.wantActivityRepository
                        .getAllWantActivitiesForUser(userId)
                        .firstOrNull { it.id == active.activityId }
                    val remainingSec = (active.endsAt - kotlinx.datetime.Clock.System.now())
                        .inWholeSeconds.coerceAtLeast(0).toInt()
                    _homeTimer.value = HomeTimerUi(
                        activityId = active.activityId,
                        wantName = want?.name ?: "Want",
                        remainingMmSs = WantTimerService.formatMmSs(remainingSec),
                    )
                }
                delay(1000L)
            }
        }
    }

    /** The want whose "How long?" sheet is open, or null. */
    private val _durationSheetWant = MutableStateFlow<WantActivity?>(null)
    val durationSheetWant: StateFlow<WantActivity?> = _durationSheetWant.asStateFlow()

    private val _pendingOverlap = MutableStateFlow<HomeOverlap?>(null)
    val pendingOverlap: StateFlow<HomeOverlap?> = _pendingOverlap.asStateFlow()

    fun showDurationSheet(activity: WantActivity) { _durationSheetWant.value = activity }
    fun dismissDurationSheet() { _durationSheetWant.value = null }
    fun dismissOverlap() { _pendingOverlap.value = null }

    /** Duration picked on Home: start right here rather than sending the user to want detail. */
    fun requestStartTimer(durationSec: Int) {
        val activity = _durationSheetWant.value ?: return
        _durationSheetWant.value = null
        viewModelScope.launch {
            apply(
                container.wantTimerController.startUnlessOverlapping(
                    container.currentUserId(), activity.id, durationSec,
                ),
                activity,
                durationSec,
            )
        }
    }

    fun confirmReplace() {
        val pending = _pendingOverlap.value ?: return
        _pendingOverlap.value = null
        viewModelScope.launch {
            apply(
                container.wantTimerController.replaceAndStart(
                    container.currentUserId(), pending.activity.id, pending.desiredDurationSec,
                ),
                pending.activity,
                pending.desiredDurationSec,
            )
        }
    }

    private fun apply(outcome: StartTimerOutcome, activity: WantActivity, durationSec: Int) {
        when (outcome) {
            StartTimerOutcome.Started -> _events.tryEmit(HomeEvent.OpenTimer(activity.id))
            StartTimerOutcome.NoPoints -> _events.tryEmit(HomeEvent.Message("No points left to spend"))
            is StartTimerOutcome.NeedsReplace -> _pendingOverlap.value = HomeOverlap(
                activity = activity,
                otherWantName = outcome.otherWantName,
                elapsedMin = outcome.elapsedMin,
                minutesLeft = outcome.minutesLeft,
                desiredDurationSec = durationSec,
            )
        }
    }

    fun cancelActiveTimer() {
        viewModelScope.launch {
            val result = container.wantTimerController.cancelWithPartialLog(container.currentUserId())
            container.wantTimerController.signalServiceStop()
            val msg = when (result) {
                is CancelResult.Logged -> "Logged ${result.minutes} min · −${result.pointsSpent} pt"
                CancelResult.Discarded -> "Timer cancelled"
                CancelResult.NoActiveTimer -> null
            }
            msg?.let { _events.tryEmit(HomeEvent.Message(it)) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHomeUiState() {
        viewModelScope.launch {
            container.authState
                .flatMapLatest { auth ->
                    dayBoundaryFlow().flatMapLatest { todayDate ->
                        val userId = auth.userId
                        val tz = TimeZone.currentSystemDefault()
                        val dayStart = todayDate.atStartOfDayIn(tz)
                        val dayEnd = dayStart + 1.days
                        val weekStart = weekStartLocalFor(todayDate, tz)

                        combine(
                            container.habitRepository.observeHabitsForUser(userId),
                            container.habitLogRepository.observeAllActiveLogsForUser(userId),
                            container.wantActivityRepository.observeWantActivities(userId),
                            container.wantLogRepository.observeAllActiveLogsForUser(userId),
                        ) { habitsRaw, habitLogs, wants, wantLogs ->
                            // Exclude soft-deleted habits — only currently active ones surface in UI / progress.
                            val habits = habitsRaw.filter { it.effectiveTo == null }
                            val habitsById = habits.associateBy { it.id }
                            val habitsWithProgress = container.getTodayHabitsUseCase.execute(
                                habits, habitLogs, dayStart, dayEnd,
                            )

                            val earned = habitLogs
                                .filter { it.loggedAt >= weekStart }
                                .groupBy { log ->
                                    log.habitId to log.loggedAt.toLocalDateTime(tz).date
                                }
                                .entries.sumOf { (key, dayLogs) ->
                                    val habit = habitsById[key.first] ?: return@sumOf 0
                                    dayLogs.sumOf {
                                        PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
                                    }.coerceAtMost(habit.dailyTarget)
                                }
                            val spent = wantLogs
                                .filter { it.loggedAt >= weekStart }
                                .sumOf { log -> log.pointsSpent }
                            val earnedToday = habitLogs
                                .filter { it.loggedAt >= dayStart && it.loggedAt < dayEnd }
                                .groupBy { it.habitId }
                                .entries.sumOf { (habitId, dayLogs) ->
                                    val habit = habitsById[habitId] ?: return@sumOf 0
                                    dayLogs.sumOf {
                                        PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
                                    }.coerceAtMost(habit.dailyTarget)
                                }
                            val spentToday = wantLogs
                                .filter { it.loggedAt >= dayStart && it.loggedAt < dayEnd }
                                .sumOf { log -> log.pointsSpent }

                            HomeUiState(
                                habitsWithProgress = habitsWithProgress,
                                pointBalance = PointBalance(
                                    earned = earned,
                                    spent = spent,
                                    balance = maxOf(0, earned - spent),
                                    earnedToday = earnedToday,
                                    spentToday = spentToday,
                                ),
                                wantActivities = wants,
                                isAuthenticated = auth.isAuthenticated,
                                isLoading = false,
                            )
                        }
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeStreaks() {
        viewModelScope.launch {
            container.authState
                .flatMapLatest { auth ->
                    dayBoundaryFlow().flatMapLatest { today ->
                        val start = today.minus(6, DateTimeUnit.DAY)
                        val range = com.habittracker.domain.model.DateRange(
                            start = start,
                            endExclusive = today.plus(1, DateTimeUnit.DAY),
                        )
                        container.computeStreakUseCase.observeRange(auth.userId, range)
                    }
                }
                .collect { _streakStrip.value = it }
        }
        viewModelScope.launch {
            container.authState
                .flatMapLatest { auth -> container.computeStreakUseCase.observeCurrent(auth.userId) }
                .collect { _streakSummary.value = it }
        }
    }

    init {
        observeHomeUiState()
        observeStreaks()
        observePinnedIdentity()
        observeActiveWantTimer()
    }

    /** Tap handler: bump pending count for this habit and (re)start its 3s countdown. */
    fun tapHabit(habit: Habit) {
        val newCount = (_pending.value[habit.id]?.count ?: 0) + 1
        _pending.update { it + (habit.id to PendingHabitLog(newCount, PENDING_WINDOW_SECONDS)) }
        timers[habit.id]?.cancel()
        timers[habit.id] = viewModelScope.launch {
            for (seconds in PENDING_WINDOW_SECONDS - 1 downTo 0) {
                delay(1000L)
                _pending.update { current ->
                    val existing = current[habit.id] ?: return@update current
                    current + (habit.id to existing.copy(secondsRemaining = seconds))
                }
            }
            commitPending(habit)
        }
    }

    /** User hit Cancel before the countdown expired — drop state, no log written. */
    fun cancelPending(habitId: String) {
        timers[habitId]?.cancel()
        timers.remove(habitId)
        _pending.update { it - habitId }
    }

    private suspend fun commitPending(habit: Habit) {
        val batch = _pending.value[habit.id] ?: return
        _pending.update { it - habit.id }
        timers.remove(habit.id)
        val userId = container.currentUserId()
        val quantity = habit.thresholdPerPoint * batch.count
        container.logHabitUseCase.execute(userId, habit.id, quantity)
            .onSuccess { result ->
                val msg = when (result.status) {
                    LogHabitStatus.EARNED ->
                        "+${result.pointsEarned} pts — ${habit.name}"
                    LogHabitStatus.DAILY_TARGET_MET ->
                        "Goal already met — ${habit.name}"
                    LogHabitStatus.BELOW_THRESHOLD ->
                        "Logged — 0 pts"
                }
                _events.tryEmit(HomeEvent.Message(msg))
                SyncTriggers.enqueue(container.appContext, SyncReason.POST_LOG)
            }
            .onFailure { e ->
                _events.tryEmit(HomeEvent.Message("Failed: ${e.message}"))
            }
    }

    /**
     * Tap handler: bump pending count for this want activity and (re)start its 3s countdown.
     *
     * Timed wants never belong here — Home routes them to the duration sheet instead, and
     * spending their point outright would log a want that was never actually done.
     */
    fun tapWant(activity: WantActivity) {
        if (activity.isTimed) return
        val newCount = (_pendingWants.value[activity.id]?.count ?: 0) + 1
        val projectedCost = newCount  // 1 tap = 1 pt
        val balance = _uiState.value.pointBalance.balance
        if (projectedCost > balance) {
            _events.tryEmit(
                HomeEvent.Message(
                    "Not enough points: need $projectedCost, have $balance — ${activity.name}"
                )
            )
            return
        }
        _pendingWants.update {
            it + (activity.id to PendingWantLog(newCount, PENDING_WINDOW_SECONDS))
        }
        wantTimers[activity.id]?.cancel()
        wantTimers[activity.id] = viewModelScope.launch {
            for (seconds in PENDING_WINDOW_SECONDS - 1 downTo 0) {
                delay(1000L)
                _pendingWants.update { current ->
                    val existing = current[activity.id] ?: return@update current
                    current + (activity.id to existing.copy(secondsRemaining = seconds))
                }
            }
            commitPendingWant(activity)
        }
    }

    /** User hit Cancel before the countdown expired — drop state, no log written. */
    fun cancelPendingWant(activityId: String) {
        wantTimers[activityId]?.cancel()
        wantTimers.remove(activityId)
        _pendingWants.update { it - activityId }
    }

    private suspend fun commitPendingWant(activity: WantActivity) {
        val batch = _pendingWants.value[activity.id] ?: return
        _pendingWants.update { it - activity.id }
        wantTimers.remove(activity.id)
        val userId = container.currentUserId()
        val result = container.logWantUseCase.execute(
            userId = userId,
            activityId = activity.id,
            taps = batch.count,
            deviceMode = DeviceMode.OTHER,
        )
        result
            .onSuccess { r ->
                _events.tryEmit(HomeEvent.Message("-${r.pointsSpent} pts — ${activity.name}"))
                SyncTriggers.enqueue(container.appContext, SyncReason.POST_LOG)
            }
            .onFailure { e ->
                val msg = when (e) {
                    is InsufficientPointsException ->
                        "Not enough points: need ${e.required}, have ${e.available} — ${activity.name}"
                    else -> "Failed: ${e.message}"
                }
                _events.tryEmit(HomeEvent.Message(msg))
            }
    }

    fun manualRefresh() {
        viewModelScope.launch {
            container.syncEngine.sync(SyncReason.MANUAL)
        }
    }

    fun triggerManualSync() {
        SyncTriggers.enqueue(container.appContext, SyncReason.MANUAL)
    }

    fun beginSignOut() {
        viewModelScope.launch {
            val userId = container.currentUserId()
            val unsynced = container.habitLogRepository.getUnsyncedFor(userId).size +
                container.wantLogRepository.getUnsyncedFor(userId).size
            _logoutUnsyncedCount.value = unsynced
            _showLogoutDialog.value = true
        }
    }

    fun confirmSignOut(forceWhenUnsynced: Boolean) {
        val userId = container.currentUserId()
        viewModelScope.launch {
            val unsynced = _logoutUnsyncedCount.value
            if (unsynced > 0 && !forceWhenUnsynced) return@launch
            // Best-effort push; proceed regardless
            runCatching { container.syncEngine.sync(SyncReason.MANUAL) }
            container.clearAuthenticatedUserData(userId)
            container.authRepository.signOut()
            container.refreshAuthState()
            _showLogoutDialog.value = false
            _logoutUnsyncedCount.value = 0
            _events.tryEmit(HomeEvent.Message("Signed out"))
        }
    }

    fun dismissLogoutDialog() {
        _showLogoutDialog.value = false
        _logoutUnsyncedCount.value = 0
    }

    override fun onCleared() {
        timers.values.forEach { it.cancel() }
        timers.clear()
        wantTimers.values.forEach { it.cancel() }
        wantTimers.clear()
        homeTimerJob?.cancel()
    }
}

private fun weekStartLocalFor(today: LocalDate, tz: TimeZone): Instant {
    val daysFromMonday = today.dayOfWeek.ordinal
    val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
    return monday.atStartOfDayIn(tz)
}
