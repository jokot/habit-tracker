package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.habittracker.domain.usecase.GetUserStreakOnDayUseCase
import com.habittracker.domain.usecase.PointCalculator
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class TimedLog(val time: LocalTime, val qty: Double, val pointsAtLog: Int)
data class DayLogs(val date: LocalDate, val items: List<TimedLog>)

data class WantDetailUi(
    val isLoading: Boolean = true,
    val want: WantActivity? = null,
    val totalSpent7d: Int = 0,
    val timesLogged7d: Int = 0,
    val timeline: List<DayLogs> = emptyList(),
    val toast: String? = null,
)

class WantDetailViewModel private constructor(
    private val activityId: String,
    private val wantActivityRepo: WantActivityRepository,
    private val wantLogRepo: WantLogRepository,
    private val getUserStreakOnDay: GetUserStreakOnDayUseCase,
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
        getUserStreakOnDay = container.getUserStreakOnDayUseCase,
        userIdProvider = { container.currentUserId() },
    )

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val userId = userIdProvider()
            val want = wantActivityRepo.getAllWantActivitiesForUser(userId)
                .firstOrNull { it.id == activityId }
            if (want == null) {
                _state.update { it.copy(isLoading = false, want = null) }
                return@launch
            }
            val today = clock.now().toLocalDateTime(tz).date
            val sevenAgo = today.minus(6, DateTimeUnit.DAY)
            val windowStart = sevenAgo.atStartOfDayIn(tz)
            val windowEnd = today.plus(1, DateTimeUnit.DAY).atStartOfDayIn(tz)
            val logs = wantLogRepo.getAllActiveLogsForUser(userId)
                .filter {
                    it.activityId == activityId &&
                        it.loggedAt >= windowStart && it.loggedAt < windowEnd
                }

            val byDate = logs.groupBy { it.loggedAt.toLocalDateTime(tz).date }
            val days = (0..6).map { offset ->
                val d = today.minus(offset, DateTimeUnit.DAY)
                val items = (byDate[d] ?: emptyList()).map { log ->
                    val streak = getUserStreakOnDay.execute(userId, d)
                    val rate = ExchangeRateCalculator.rateFor(streak)
                    val points = PointCalculator.pointsSpentWithRate(log.quantity, want.costPerUnit, rate)
                    TimedLog(
                        time = log.loggedAt.toLocalDateTime(tz).time,
                        qty = log.quantity,
                        pointsAtLog = points,
                    )
                }
                DayLogs(date = d, items = items)
            }
            val totalSpent = days.sumOf { it.items.sumOf { item -> item.pointsAtLog } }
            _state.update {
                WantDetailUi(
                    isLoading = false,
                    want = want,
                    totalSpent7d = totalSpent,
                    timesLogged7d = days.sumOf { day -> day.items.size },
                    timeline = days,
                )
            }
        }
    }

    fun onTimerStub() {
        _state.update { it.copy(toast = "Timer coming soon.") }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    fun hide() {
        viewModelScope.launch {
            wantActivityRepo.hideWantActivity(activityId, userIdProvider(), clock.now())
            _state.update { it.copy(toast = "Hidden") }
        }
    }

    fun delete() {
        viewModelScope.launch {
            wantActivityRepo.hideWantActivity(activityId, userIdProvider(), clock.now())
            _state.update { it.copy(toast = "Deleted") }
        }
    }
}
