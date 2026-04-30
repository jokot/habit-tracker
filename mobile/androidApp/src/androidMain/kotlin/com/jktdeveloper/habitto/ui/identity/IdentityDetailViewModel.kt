package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.IdentityStats
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class IdentityDetailState {
    object Loading : IdentityDetailState()
    data class Loaded(
        val identity: Identity,
        val stats: IdentityStats,
        val habits: List<Habit>,
    ) : IdentityDetailState()
    object NotFound : IdentityDetailState()
}

class IdentityDetailViewModel private constructor(
    private val identityRepo: IdentityRepository,
    private val statsUseCase: ComputeIdentityStatsUseCase,
    private val userIdProvider: () -> String,
    private val identityId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<IdentityDetailState>(IdentityDetailState.Loading)
    val state: StateFlow<IdentityDetailState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer, identityId: String) : this(
        identityRepo = container.identityRepository,
        statsUseCase = container.computeIdentityStatsUseCase,
        userIdProvider = { container.currentUserId() },
        identityId = identityId,
    )

    init { refresh() }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            val userIdentities = identityRepo.observeUserIdentities(userId).first()
            val identity = userIdentities.firstOrNull { it.id == identityId }
            if (identity == null) {
                _state.value = IdentityDetailState.NotFound
                return@launch
            }
            val stats = statsUseCase.computeNow(userId, identityId)
            val habits = identityRepo.observeHabitsForIdentity(userId, identityId).first()
            _state.value = IdentityDetailState.Loaded(identity, stats, habits)
        }
    }

    companion object {
        fun forTest(
            identityRepo: IdentityRepository,
            statsUseCase: ComputeIdentityStatsUseCase,
            userIdProvider: () -> String,
            identityId: String,
        ) = IdentityDetailViewModel(identityRepo, statsUseCase, userIdProvider, identityId)
    }
}
