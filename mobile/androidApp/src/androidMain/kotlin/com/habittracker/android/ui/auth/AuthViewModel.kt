package com.habittracker.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthNavigation {
    object ToOnboarding : AuthNavigation
    object ToHome : AuthNavigation
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _navigation = MutableSharedFlow<AuthNavigation>()
    val navigation: SharedFlow<AuthNavigation> = _navigation.asSharedFlow()

    init {
        viewModelScope.launch {
            if (container.authRepository.isLoggedIn()) {
                val userId = container.authRepository.currentUserId() ?: return@launch
                container.seedLocalDataIfEmpty()
                if (container.isOnboardedUseCase.execute(userId)) {
                    _navigation.emit(AuthNavigation.ToHome)
                } else {
                    _navigation.emit(AuthNavigation.ToOnboarding)
                }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun toggleSignUp() {
        _uiState.value = _uiState.value.copy(isSignUp = !_uiState.value.isSignUp, error = null)
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password required")
            return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            val result = if (state.isSignUp) {
                container.authRepository.signUp(state.email.trim(), state.password)
            } else {
                container.authRepository.signIn(state.email.trim(), state.password)
            }
            result.onSuccess { session ->
                container.seedLocalDataIfEmpty()
                if (container.isOnboardedUseCase.execute(session.userId)) {
                    _navigation.emit(AuthNavigation.ToHome)
                } else {
                    _navigation.emit(AuthNavigation.ToOnboarding)
                }
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Auth failed")
            }
        }
    }
}
