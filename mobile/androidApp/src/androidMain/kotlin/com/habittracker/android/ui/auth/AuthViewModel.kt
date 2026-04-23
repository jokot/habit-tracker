package com.habittracker.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.data.repository.SignUpResult
import com.habittracker.data.repository.UserSession
import com.habittracker.data.sync.SyncReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthEvent {
    object Success : AuthEvent
    data class ConfirmationEmailSent(val email: String) : AuthEvent
}

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSignUp: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value, error = null)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible)
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.value = _uiState.value.copy(
            isConfirmPasswordVisible = !_uiState.value.isConfirmPasswordVisible,
        )
    }

    fun toggleSignUp() {
        val current = _uiState.value
        _uiState.value = current.copy(
            isSignUp = !current.isSignUp,
            confirmPassword = "",
            isConfirmPasswordVisible = false,
            error = null,
        )
    }

    fun submit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "Email and password required")
            return
        }
        if (state.isSignUp) {
            if (state.confirmPassword.isBlank()) {
                _uiState.value = state.copy(error = "Please confirm your password")
                return
            }
            if (state.password != state.confirmPassword) {
                _uiState.value = state.copy(error = "Passwords don't match")
                return
            }
        }
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            if (state.isSignUp) {
                container.authRepository.signUp(state.email.trim(), state.password)
                    .onSuccess { outcome ->
                        when (outcome) {
                            is SignUpResult.SignedIn -> completeSignIn(outcome.session)
                            is SignUpResult.ConfirmationRequired -> {
                                _uiState.value = _uiState.value.copy(isLoading = false)
                                _events.emit(AuthEvent.ConfirmationEmailSent(outcome.email))
                            }
                        }
                    }.onFailure { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Auth failed")
                    }
            } else {
                container.authRepository.signIn(state.email.trim(), state.password)
                    .onSuccess { session -> completeSignIn(session) }
                    .onFailure { e ->
                        _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Auth failed")
                    }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            container.authRepository.signInWithGoogle(idToken)
                .onSuccess { session -> completeSignIn(session) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Google sign-in failed",
                    )
                }
        }
    }

    private suspend fun completeSignIn(session: UserSession) {
        container.migrateLocalToAuthenticated(session.userId)
        container.refreshAuthState()
        container.seedLocalDataIfEmpty()
        container.syncEngine.sync(SyncReason.POST_SIGN_IN)
        _events.emit(AuthEvent.Success)
    }
}
