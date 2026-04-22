package com.habittracker.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.PointBalance
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.usecase.PointCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

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
                        .coerceAtMost(habit.dailyTarget)
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
}

private fun weekStartUtc(): Instant {
    val now = Clock.System.now()
    val localDate = now.toLocalDateTime(TimeZone.UTC).date
    val daysFromMonday = localDate.dayOfWeek.ordinal
    val monday = localDate.minus(daysFromMonday, DateTimeUnit.DAY)
    return monday.atStartOfDayIn(TimeZone.UTC)
}
