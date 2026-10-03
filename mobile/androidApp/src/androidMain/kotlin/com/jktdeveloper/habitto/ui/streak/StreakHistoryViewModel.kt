package com.jktdeveloper.habitto.ui.streak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.sync.SyncState
import com.habittracker.domain.model.DateRange
import com.habittracker.domain.model.DayPoints
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakSummary
import com.habittracker.domain.model.TodaySection
import com.habittracker.domain.usecase.ComputeStreakUseCase
import com.habittracker.domain.usecase.GetDayPointsUseCase
import com.jktdeveloper.habitto.util.dayBoundaryFlow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
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

/**
 * Streak History. The summary and each loaded month watch the logs and the habits,
 * so the screen updates by itself and never reloads on entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreakHistoryViewModel(
    private val useCase: ComputeStreakUseCase,
    private val getDayPointsUseCase: GetDayPointsUseCase,
    private val userIdProvider: () -> String,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    readySections: StateFlow<Set<TodaySection>> = MutableStateFlow(TodaySection.entries.toSet()),
    syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.Idle),
    /** Emits the local date, then again each time the day changes. */
    days: Flow<LocalDate> = dayBoundaryFlow(timeZone, clock),
    /** Runs the streak computation, which walks the whole log history. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val userId = userIdProvider()
    private val today = days.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    /** False until the whole habit-log history is local. Until then the screen shows skeletons. */
    val historyReady: StateFlow<Boolean> = readySections
        .map { TodaySection.STREAK in it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TodaySection.STREAK in readySections.value)

    /** The last sync failed while the history was still loading. */
    val loadFailed: StateFlow<Boolean> = combine(syncState, historyReady) { state, ready ->
        state is SyncState.Error && !ready
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null until the first computation ends. */
    private val _summary = MutableStateFlow<StreakSummary?>(null)
    val summary: StateFlow<StreakSummary?> = _summary.asStateFlow()

    private val _months = MutableStateFlow<List<MonthData>>(emptyList())
    val months: StateFlow<List<MonthData>> = _months.asStateFlow()

    private val _selectedDayPoints = MutableStateFlow<DayPoints?>(null)
    val selectedDayPoints: StateFlow<DayPoints?> = _selectedDayPoints.asStateFlow()

    fun onDaySelected(day: LocalDate?) {
        if (day == null) {
            _selectedDayPoints.value = null
            return
        }
        viewModelScope.launch {
            getDayPointsUseCase.execute(userId, day)
                .onSuccess { _selectedDayPoints.value = it }
                .onFailure { _selectedDayPoints.value = DayPoints(earned = 0, spent = 0) }
        }
    }

    init {
        viewModelScope.launch {
            today
                .flatMapLatest { useCase.observeCurrent(userId) }
                .flowOn(computeDispatcher)
                .collect { _summary.value = it }
        }
        val now = clock.now().toLocalDateTime(timeZone).date
        loadMonth(now.year, now.monthNumber)
    }

    /** Loads the month before the oldest one shown. Stops at the month of the first log. */
    fun loadOlderMonth() {
        val oldest = _months.value.lastOrNull() ?: return
        val firstLog = _summary.value?.firstLogDate ?: return
        val older = LocalDate(oldest.year, oldest.month, 1).minus(1, DateTimeUnit.MONTH)
        if (older < LocalDate(firstLog.year, firstLog.monthNumber, 1)) return
        loadMonth(older.year, older.monthNumber)
    }

    private fun loadMonth(year: Int, monthNumber: Int) {
        if (_months.value.any { it.year == year && it.month == monthNumber }) return
        _months.update { it + MonthData(year, monthNumber, emptyList(), isLoading = true) }

        val first = LocalDate(year, monthNumber, 1)
        val range = DateRange(start = first, endExclusive = first.plus(1, DateTimeUnit.MONTH))
        viewModelScope.launch {
            today
                .flatMapLatest { useCase.observeRange(userId, range) }
                .flowOn(computeDispatcher)
                .catch { e ->
                    updateMonth(year, monthNumber) {
                        it.copy(isLoading = false, error = e.message ?: "Failed to load")
                    }
                }
                .collect { result ->
                    updateMonth(year, monthNumber) {
                        it.copy(days = result.days, isLoading = false, error = null)
                    }
                }
        }
    }

    private fun updateMonth(year: Int, monthNumber: Int, change: (MonthData) -> MonthData) {
        _months.update { existing ->
            existing.map { m -> if (m.year == year && m.month == monthNumber) change(m) else m }
        }
    }
}
