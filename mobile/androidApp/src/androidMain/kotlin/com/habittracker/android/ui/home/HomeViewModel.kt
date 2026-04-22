package com.habittracker.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.android.ui.common.UndoState
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

sealed interface HomeEvent {
    data class Message(val text: String) : HomeEvent
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    private var undoJob: Job? = null

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

    fun quickLogHabit(habit: Habit) {
        val userId = container.currentUserId()
        viewModelScope.launch {
            container.logHabitUseCase.execute(userId, habit.id, habit.thresholdPerPoint)
                .onSuccess { result ->
                    when (result.status) {
                        LogHabitStatus.EARNED -> {
                            _events.tryEmit(
                                HomeEvent.Message("+${result.pointsEarned} pts — ${habit.name}")
                            )
                            startUndoTimer(result.log.id)
                        }
                        LogHabitStatus.DAILY_TARGET_MET -> {
                            _events.tryEmit(
                                HomeEvent.Message("Goal already met today — ${habit.name}")
                            )
                        }
                        LogHabitStatus.BELOW_THRESHOLD -> {
                            _events.tryEmit(HomeEvent.Message("Logged — 0 pts"))
                        }
                    }
                }
                .onFailure { e ->
                    _events.tryEmit(HomeEvent.Message("Failed: ${e.message}"))
                }
        }
    }

    fun undoLastHabit() {
        val current = _undoState.value ?: return
        val userId = container.currentUserId()
        viewModelScope.launch {
            container.undoHabitLogUseCase.execute(current.logId, userId)
            undoJob?.cancel()
            _undoState.value = null
            _events.tryEmit(HomeEvent.Message("Undone"))
        }
    }

    private fun startUndoTimer(logId: String) {
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            for (seconds in 300 downTo 0) {
                _undoState.value = UndoState(logId, seconds)
                delay(1000L)
            }
            _undoState.value = null
        }
    }
}

private fun weekStartUtc(): Instant {
    val now = Clock.System.now()
    val localDate = now.toLocalDateTime(TimeZone.UTC).date
    val daysFromMonday = localDate.dayOfWeek.ordinal
    val monday = localDate.minus(daysFromMonday, DateTimeUnit.DAY)
    return monday.atStartOfDayIn(TimeZone.UTC)
}
