package com.habittracker.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.PointBalance
import com.habittracker.domain.model.WantActivity
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
import kotlinx.coroutines.flow.combine
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
    val pointBalance: PointBalance = PointBalance(0, 0),
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

sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
}

private const val PENDING_WINDOW_SECONDS = 3

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** habitId → pending tap batch. Drops to empty on commit or cancel. */
    private val _pending = MutableStateFlow<Map<String, PendingHabitLog>>(emptyMap())
    val pending: StateFlow<Map<String, PendingHabitLog>> = _pending.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    /** One countdown coroutine per habit. */
    private val timers = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            val userId = container.currentUserId()
            val now = Clock.System.now()
            val todayDate = now.toLocalDateTime(TimeZone.UTC).date
            val dayStart = todayDate.atStartOfDayIn(TimeZone.UTC)
            val dayEnd = dayStart + 1.days
            val weekStart = weekStartUtc()
            val isAuthed = container.isAuthenticated()

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
                    pointBalance = PointBalance(earned, spent),
                    wantActivities = wants,
                    isAuthenticated = isAuthed,
                    isLoading = false,
                )
            }.collect { _uiState.value = it }
        }
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
            }
            .onFailure { e ->
                _events.tryEmit(HomeEvent.Message("Failed: ${e.message}"))
            }
    }

    override fun onCleared() {
        timers.values.forEach { it.cancel() }
        timers.clear()
    }
}

private fun weekStartUtc(): Instant {
    val now = Clock.System.now()
    val localDate = now.toLocalDateTime(TimeZone.UTC).date
    val daysFromMonday = localDate.dayOfWeek.ordinal
    val monday = localDate.minus(daysFromMonday, DateTimeUnit.DAY)
    return monday.atStartOfDayIn(TimeZone.UTC)
}
