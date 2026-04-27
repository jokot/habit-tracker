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
import com.habittracker.domain.model.PointBalance
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.usecase.InsufficientPointsException
import com.habittracker.domain.usecase.LogHabitStatus
import com.habittracker.domain.usecase.PointCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
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
}

private const val PENDING_WINDOW_SECONDS = 3

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val syncState: StateFlow<SyncState> = container.syncEngine.syncState

    private val _showLogoutDialog = MutableStateFlow(false)
    val showLogoutDialog: StateFlow<Boolean> = _showLogoutDialog.asStateFlow()

    private val _logoutUnsyncedCount = MutableStateFlow(0)
    val logoutUnsyncedCount: StateFlow<Int> = _logoutUnsyncedCount.asStateFlow()

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

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeHomeUiState() {
        viewModelScope.launch {
            container.authState
                .flatMapLatest { auth ->
                    val userId = auth.userId
                    val now = Clock.System.now()
                    val todayDate = now.toLocalDateTime(TimeZone.UTC).date
                    val dayStart = todayDate.atStartOfDayIn(TimeZone.UTC)
                    val dayEnd = dayStart + 1.days
                    val weekStart = weekStartUtc()

                    combine(
                        container.habitRepository.observeHabitsForUser(userId),
                        container.habitLogRepository.observeAllActiveLogsForUser(userId),
                        container.wantActivityRepository.observeWantActivities(userId),
                        container.wantLogRepository.observeAllActiveLogsForUser(userId),
                    ) { habits, habitLogs, wants, wantLogs ->
                        val habitsById = habits.associateBy { it.id }
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

                        val earned = habitLogs
                            .filter { it.loggedAt >= weekStart }
                            .groupBy { log ->
                                log.habitId to log.loggedAt.toLocalDateTime(TimeZone.UTC).date
                            }
                            .entries.sumOf { (key, dayLogs) ->
                                val habit = habitsById[key.first] ?: return@sumOf 0
                                dayLogs.sumOf {
                                    PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
                                }.coerceAtMost(habit.dailyTarget)
                            }
                        val spent = wantLogs
                            .filter { it.loggedAt >= weekStart }
                            .sumOf { log ->
                                wants.firstOrNull { it.id == log.activityId }?.let {
                                    PointCalculator.pointsSpent(log.quantity, it.costPerUnit)
                                } ?: 0
                            }

                        HomeUiState(
                            habitsWithProgress = habitsWithProgress,
                            pointBalance = PointBalance(earned, spent, balance = maxOf(0, earned - spent)),
                            wantActivities = wants,
                            isAuthenticated = auth.isAuthenticated,
                            isLoading = false,
                        )
                    }
                }
                .collect { _uiState.value = it }
        }
    }

    init { observeHomeUiState() }

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

    /** Tap handler: bump pending count for this want activity and (re)start its 3s countdown. */
    fun tapWant(activity: WantActivity) {
        val newCount = (_pendingWants.value[activity.id]?.count ?: 0) + 1
        val perTap = PointCalculator.pointsSpent(1.0, activity.costPerUnit)
        val projectedCost = newCount * perTap
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
        var totalSpent = 0
        var succeeded = 0
        for (i in 1..batch.count) {
            val result = container.logWantUseCase.execute(userId, activity.id, 1.0, DeviceMode.OTHER)
            if (result.isSuccess) {
                totalSpent += result.getOrThrow().pointsSpent
                succeeded++
                continue
            }
            val e = result.exceptionOrNull()
            val msg = when (e) {
                is InsufficientPointsException -> if (succeeded > 0) {
                    "-$totalSpent pts — $succeeded/${batch.count} logged, balance empty — ${activity.name}"
                } else {
                    "Not enough points: need ${e.required}, have ${e.available} — ${activity.name}"
                }
                else -> "Failed: ${e?.message}"
            }
            _events.tryEmit(HomeEvent.Message(msg))
            if (succeeded > 0) {
                SyncTriggers.enqueue(container.appContext, SyncReason.POST_LOG)
            }
            return
        }
        _events.tryEmit(HomeEvent.Message("-$totalSpent pts — ${activity.name}"))
        if (succeeded > 0) {
            SyncTriggers.enqueue(container.appContext, SyncReason.POST_LOG)
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
    }
}

private fun weekStartUtc(): Instant {
    val now = Clock.System.now()
    val localDate = now.toLocalDateTime(TimeZone.UTC).date
    val daysFromMonday = localDate.dayOfWeek.ordinal
    val monday = localDate.minus(daysFromMonday, DateTimeUnit.DAY)
    return monday.atStartOfDayIn(TimeZone.UTC)
}
