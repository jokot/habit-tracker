package com.jktdeveloper.habitto.ui.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.DayPoints
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakSummary
import com.habittracker.domain.usecase.ComputeStreakUseCase
import com.habittracker.domain.usecase.GetDayPointsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class MonthData(
    val year: Int,
    val month: Int,
    val days: List<StreakDay>,
    val isLoading: Boolean,
    val error: String? = null,
) {
    fun firstDay(): LocalDate = LocalDate(year, month, 1)
}

class StreakHistoryViewModel(
    private val useCase: ComputeStreakUseCase,
    private val getDayPointsUseCase: GetDayPointsUseCase,
    private val userIdProvider: () -> String,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _summary = MutableStateFlow(StreakSummary(0, 0, 0, null))
    val summary: StateFlow<StreakSummary> = _summary.asStateFlow()

    private val _months = MutableStateFlow<List<MonthData>>(emptyList())
    val months: StateFlow<List<MonthData>> = _months.asStateFlow()

    private val _firstLogDate = MutableStateFlow<LocalDate?>(null)
    val firstLogDate: StateFlow<LocalDate?> = _firstLogDate.asStateFlow()

    private val _selectedDayPoints = MutableStateFlow<DayPoints?>(null)
    val selectedDayPoints: StateFlow<DayPoints?> = _selectedDayPoints.asStateFlow()

    fun onDaySelected(day: LocalDate?) {
        if (day == null) {
            _selectedDayPoints.value = null
            return
        }
        viewModelScope.launch {
            getDayPointsUseCase.execute(userIdProvider(), day)
                .onSuccess { _selectedDayPoints.value = it }
                .onFailure { _selectedDayPoints.value = DayPoints(earned = 0, spent = 0) }
        }
    }

    init {
        viewModelScope.launch {
            val userId = userIdProvider()
            val s = useCase.computeSummaryNow(userId)
            _summary.value = s
            _firstLogDate.value = s.firstLogDate
            val today = clock.now().toLocalDateTime(timeZone).date
            loadMonth(today.year, today.monthNumber)
        }
    }

    fun loadOlderMonth() {
        val oldest = _months.value.lastOrNull() ?: return
        val first = LocalDate(oldest.year, oldest.month, 1)
        val older = first.minus(1, DateTimeUnit.MONTH)
        val firstLog = _firstLogDate.value
        if (firstLog != null && older.toEpochDays() < LocalDate(firstLog.year, firstLog.monthNumber, 1).toEpochDays()) return
        loadMonth(older.year, older.monthNumber)
    }

    private fun loadMonth(year: Int, monthNumber: Int) {
        if (_months.value.any { it.year == year && it.month == monthNumber }) return
        _months.update { it + MonthData(year, monthNumber, emptyList(), isLoading = true) }

        viewModelScope.launch {
            runCatching {
                val first = LocalDate(year, monthNumber, 1)
                val nextMonth = first.plus(1, DateTimeUnit.MONTH)
                val range = DateRange(start = first, endExclusive = nextMonth)
                val result = useCase.computeNow(userIdProvider(), range)
                _months.update { existing ->
                    existing.map { m ->
                        if (m.year == year && m.month == monthNumber)
                            m.copy(days = result.days, isLoading = false)
                        else m
                    }
                }
            }.onFailure { e ->
                _months.update { existing ->
                    existing.map { m ->
                        if (m.year == year && m.month == monthNumber)
                            m.copy(isLoading = false, error = e.message ?: "Failed to load")
                        else m
                    }
                }
            }
        }
    }
}
