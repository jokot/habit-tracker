package com.jktdeveloper.habitto.ui.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.ExchangeRateCalculator
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.AuthState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
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

    /** Active habits — same `effectiveTo == null` rule the habit list screen applies. */
    val habitCount: StateFlow<Int> = container.habitRepository
        .observeHabitsForUser(container.currentUserId())
        .map { habits -> habits.count { it.effectiveTo == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val wantCount: StateFlow<Int> = container.wantActivityRepository
        .observeWantActivities(container.currentUserId())
        .map { wants -> wants.count { it.hiddenAt == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _pinnedIdentityName = MutableStateFlow<String?>(null)
    val pinnedIdentityName: StateFlow<String?> = _pinnedIdentityName.asStateFlow()

    init { observePinnedIdentity() }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observePinnedIdentity() {
        viewModelScope.launch {
            container.authState.flatMapLatest { auth ->
                container.identityRepository.observeUserIdentities(auth.userId).map { identities ->
                    val pinnedId = container.identityRepository.getPinnedIdentityIdForUser(auth.userId)
                    identities.firstOrNull { it.id == pinnedId }?.name
                }
            }.collect { _pinnedIdentityName.value = it }
        }
    }
}
