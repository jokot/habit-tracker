package com.habittracker.android.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.android.ui.common.UndoState
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LogWantUiState {
    object Idle : LogWantUiState
    object Loading : LogWantUiState
    data class Success(val pointsSpent: Int, val logId: String) : LogWantUiState
    data class Error(val message: String) : LogWantUiState
}

class LogWantViewModel(
    private val activityId: String,
    private val container: AppContainer,
) : ViewModel() {

    private val _activity = MutableStateFlow<WantActivity?>(null)
    val activity: StateFlow<WantActivity?> = _activity.asStateFlow()

    private val _quantityInput = MutableStateFlow("")
    val quantityInput: StateFlow<String> = _quantityInput.asStateFlow()

    private val _deviceMode = MutableStateFlow(DeviceMode.OTHER)
    val deviceMode: StateFlow<DeviceMode> = _deviceMode.asStateFlow()

    private val _uiState = MutableStateFlow<LogWantUiState>(LogWantUiState.Idle)
    val uiState: StateFlow<LogWantUiState> = _uiState.asStateFlow()

    private val _undoState = MutableStateFlow<UndoState?>(null)
    val undoState: StateFlow<UndoState?> = _undoState.asStateFlow()

    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            val userId = container.currentUserId()
            _activity.value = container.wantActivityRepository.getWantActivities(userId)
                .firstOrNull { it.id == activityId }
        }
    }

    fun onQuantityChange(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d*$"))) {
            _quantityInput.value = value
        }
    }

    fun onDeviceModeChange(mode: DeviceMode) {
        _deviceMode.value = mode
    }

    fun log() {
        val userId = container.currentUserId()
        val quantity = _quantityInput.value.toDoubleOrNull() ?: run {
            _uiState.value = LogWantUiState.Error("Enter a valid number")
            return
        }
        if (quantity <= 0) {
            _uiState.value = LogWantUiState.Error("Quantity must be greater than 0")
            return
        }
        viewModelScope.launch {
            _uiState.value = LogWantUiState.Loading
            container.logWantUseCase.execute(userId, activityId, quantity, _deviceMode.value)
                .onSuccess { result ->
                    _uiState.value = LogWantUiState.Success(result.pointsSpent, result.log.id)
                    startUndoTimer(result.log.id)
                }
                .onFailure { e ->
                    _uiState.value = LogWantUiState.Error(e.message ?: "Failed to log")
                }
        }
    }

    fun undo(logId: String) {
        val userId = container.currentUserId()
        viewModelScope.launch {
            container.undoWantLogUseCase.execute(logId, userId)
            undoJob?.cancel()
            _undoState.value = null
            _uiState.value = LogWantUiState.Idle
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
