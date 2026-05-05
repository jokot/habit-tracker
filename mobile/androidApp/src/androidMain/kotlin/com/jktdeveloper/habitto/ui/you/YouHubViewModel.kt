package com.jktdeveloper.habitto.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class YouHubViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val authState: StateFlow<AuthState> = container.authState

    val userIdentities: StateFlow<List<Identity>> =
        container.getUserIdentitiesUseCase.execute(container.currentUserId())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val currentRate: StateFlow<Double> = container.computeStreakUseCase
        .observeCurrent(container.currentUserId())
        .map { ExchangeRateCalculator.rateFor(it.currentStreak) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0)

    val currentStreak: StateFlow<Int> = container.computeStreakUseCase
        .observeCurrent(container.currentUserId())
        .map { it.currentStreak }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun currentEmail(): String? = container.currentAccountEmail()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut: StateFlow<Boolean> = _isSigningOut.asStateFlow()

    fun signOut(onComplete: () -> Unit) {
        if (_isSigningOut.value) return
        viewModelScope.launch {
            _isSigningOut.value = true
            try {
                container.signOutFromSettings()
                onComplete()
            } finally {
                _isSigningOut.value = false
            }
        }
    }
}
