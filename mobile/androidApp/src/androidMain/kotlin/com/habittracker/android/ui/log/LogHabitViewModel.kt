package com.habittracker.android.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.domain.model.Habit
import com.habittracker.domain.usecase.LogHabitStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UndoState(val logId: String, val secondsRemaining: Int)

sealed interface LogHabitUiState {
    object Idle : LogHabitUiState
    object Loading : LogHabitUiState
    data class Success(
        val pointsEarned: Int,
        val logId: String,
        val status: LogHabitStatus,
    ) : LogHabitUiState
    data class Error(val message: String) : LogHabitUiState
}

class LogHabitViewModel(
    private val habitId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _habit = MutableStateFlow<Habit?>(null)
    val habit: StateFlow<Habit?> = _habit.asStateFlow()

    private val _quantityInput = MutableStateFlow("")
    val quantityInput: StateFlow<String> = _quantityInput.asStateFlow()

    private val _uiState = MutableStateFlow<LogHabitUiState>(LogHabitUiState.Idle)
    val uiState: StateFlow<LogHabitUiState> = _uiState.asStateFlow()

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            val userId = container.currentUserId()
            _habit.value = container.habitRepository.getHabitsForUser(userId)
                .firstOrNull { it.id == habitId }
        }
    }

    fun onQuantityChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _quantityInput.value = value
        }
    }

    fun log() {
        val userId = container.currentUserId()
        val quantity = _quantityInput.value.toDoubleOrNull() ?: run {
            _uiState.value = LogHabitUiState.Error("Enter a valid number")
            return
        }
        if (quantity <= 0) {
            _uiState.value = LogHabitUiState.Error("Quantity must be greater than 0")
            return
        }
        viewModelScope.launch {
            _uiState.value = LogHabitUiState.Loading
            container.logHabitUseCase.execute(userId, habitId, quantity)
                .onSuccess { result ->
                    _uiState.value = LogHabitUiState.Success(
                        pointsEarned = result.pointsEarned,
                        logId = result.log.id,
                        status = result.status,
                    )
                    if (result.status == LogHabitStatus.EARNED) startUndoTimer(result.log.id)
                }
                .onFailure { e ->
                    _uiState.value = LogHabitUiState.Error(e.message ?: "Failed to log")
                }
        }
    }

    fun undo(logId: String) {
        val userId = container.currentUserId()
        viewModelScope.launch {
            container.undoHabitLogUseCase.execute(logId, userId)
            undoJob?.cancel()
            _undoState.value = null
            _uiState.value = LogHabitUiState.Idle
            _quantityInput.value = ""
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
