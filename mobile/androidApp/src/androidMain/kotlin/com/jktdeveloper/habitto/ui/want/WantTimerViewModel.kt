package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantTimer
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.timer.CancelResult
import com.jktdeveloper.habitto.timer.WantTimerController
import com.jktdeveloper.habitto.timer.WantTimerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class WantTimerUi(
    val isLoading: Boolean = true,
    val state: ScreenState = ScreenState.Orphan,
    val want: WantActivity? = null,
    val remainingMmSs: String = "--:--",
    val totalMin: Int = 0,
    val elapsedMin: Int = 0,
    val pointsSpentSoFar: Int = 0,
    val elapsedFraction: Float = 0f,
    val toast: String? = null,
) {
    enum class ScreenState { Running, Orphan }
}

class WantTimerViewModel(
    private val timerController: WantTimerController,
    private val timerRepo: WantTimerRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(WantTimerUi())
    val state: StateFlow<WantTimerUi> = _state.asStateFlow()

    constructor(container: AppContainer) : this(
        timerController = container.wantTimerController,
        timerRepo = container.wantTimerRepository,
        wantActivityRepo = container.wantActivityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { observe() }

    private fun observe() {
        viewModelScope.launch {
            while (true) {
                val userId = userIdProvider()
                val active: WantTimer? = timerRepo.getActive(userId)
                if (active == null) {
                    _state.update { it.copy(isLoading = false, state = WantTimerUi.ScreenState.Orphan, want = null) }
                } else {
                    val want = wantActivityRepo
                        .getAllWantActivitiesForUser(userId)
                        .firstOrNull { it.id == active.activityId }
                    val now = clock.now()
                    val remainingSec = (active.endsAt - now).inWholeSeconds.coerceAtLeast(0).toInt()
                    val totalMin = (active.durationSec / 60).coerceAtLeast(1)
                    val elapsedMin = (totalMin - ((remainingSec + 59) / 60)).coerceAtLeast(0)
                    val unitsPerPoint = (want?.unitsPerPoint ?: 1).coerceAtLeast(1)
                    val pointsSpent = elapsedMin / unitsPerPoint
                    _state.update {
                        it.copy(
                            isLoading = false,
                            state = WantTimerUi.ScreenState.Running,
                            want = want,
                            remainingMmSs = WantTimerService.formatMmSs(remainingSec),
                            totalMin = totalMin,
                            elapsedMin = elapsedMin,
                            pointsSpentSoFar = pointsSpent,
                            elapsedFraction = (elapsedMin.toFloat() / totalMin.toFloat()).coerceIn(0f, 1f),
                        )
                    }
                }
                delay(1000L)
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            val result = timerController.cancelWithPartialLog(userIdProvider())
            timerController.signalServiceStop()
            val toast = when (result) {
                is CancelResult.Logged -> "Logged ${result.minutes} min · −${result.pointsSpent} pt"
                CancelResult.Discarded -> "Timer cancelled"
                CancelResult.NoActiveTimer -> null
            }
            _state.update { it.copy(toast = toast) }
        }
    }

    fun consumeToast() { _state.update { it.copy(toast = null) } }
}
