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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

data class HomeUiState(
    val habitsWithProgress: List<HabitWithProgress> = emptyList(),
    val pointBalance: PointBalance = PointBalance(0, 0),
    val wantActivities: List<WantActivity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            val userId = container.authRepository.currentUserId() ?: return@launch
            val habits = container.habitRepository.getHabitsForUser(userId)

            val now = Clock.System.now()
            val todayDate = now.toLocalDateTime(TimeZone.UTC).date
            val dayStart = todayDate.atStartOfDayIn(TimeZone.UTC)
            val dayEnd = dayStart + 1.days

            val habitsWithProgress = habits.map { habit ->
                val logsToday = container.habitLogRepository
                    .getActiveLogsForHabitOnDay(userId, habit.id, dayStart, dayEnd)
                val pointsToday = logsToday.sumOf {
                    PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint)
                }
                HabitWithProgress(habit, pointsToday)
            }

            val balance = container.getPointBalanceUseCase.execute(userId)
                .getOrDefault(PointBalance(0, 0))

            val activities = container.wantActivityRepository.getWantActivities(userId)

            _uiState.value = HomeUiState(
                habitsWithProgress = habitsWithProgress,
                pointBalance = balance,
                wantActivities = activities,
                isLoading = false,
            )
        }
    }
}
