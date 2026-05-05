package com.jktdeveloper.habitto.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.RateTier
import com.habittracker.domain.model.StreakSummary
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComparisonRow(
    val activityId: String,
    val name: String,
    val unit: String,
    val baseCostPerUnit: Double,
    val currentCostPerUnit: Double,
)

data class ExchangeRateState(
    val isLoading: Boolean = true,
    val currentStreak: Int = 0,
    val currentRate: Double = 1.0,
    val currentTier: RateTier = ExchangeRateCalculator.tiers.first(),
    val daysToNext: Int? = 7,
    val comparison: List<ComparisonRow> = emptyList(),
)

class ExchangeRateViewModel(
    private val userIdProvider: () -> String,
    private val streakFlow: () -> Flow<StreakSummary>,
    private val wantActivitiesProvider: suspend (String) -> List<WantActivity>,
) : ViewModel() {

    constructor(container: AppContainer) : this(
        userIdProvider = { container.currentUserId() },
        streakFlow = { container.computeStreakUseCase.observeCurrent(container.currentUserId()) },
        wantActivitiesProvider = { userId -> container.wantActivityRepository.getWantActivities(userId) },
    )

    private val _state = MutableStateFlow(ExchangeRateState())
    val state: StateFlow<ExchangeRateState> = _state.asStateFlow()

    init {
        viewModelScope.launch { observe() }
    }

    private suspend fun observe() {
        val userId = userIdProvider()
        streakFlow().collect { summary ->
            val activities = wantActivitiesProvider(userId)
            val rate = ExchangeRateCalculator.rateFor(summary.currentStreak)
            val tier = ExchangeRateCalculator.tierFor(summary.currentStreak)
            val daysToNext = ExchangeRateCalculator.daysToNextTier(summary.currentStreak)
            val comparison = activities.map { activity ->
                ComparisonRow(
                    activityId = activity.id,
                    name = activity.name,
                    unit = activity.unit,
                    baseCostPerUnit = activity.costPerUnit,
                    currentCostPerUnit = activity.costPerUnit * rate,
                )
            }
            _state.value = ExchangeRateState(
                isLoading = false,
                currentStreak = summary.currentStreak,
                currentRate = rate,
                currentTier = tier,
                daysToNext = daysToNext,
                comparison = comparison,
            )
        }
    }
}
