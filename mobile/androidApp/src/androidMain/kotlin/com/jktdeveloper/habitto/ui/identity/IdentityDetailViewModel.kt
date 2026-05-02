package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.IdentityStats
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.habittracker.domain.usecase.PinIdentityUseCase
import com.habittracker.domain.usecase.UnpinIdentityUseCase
import com.habittracker.domain.usecase.RemoveIdentityUseCase
import com.habittracker.domain.usecase.UpdateIdentityWhyUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class IdentityDetailState {
    object Loading : IdentityDetailState()
    data class Loaded(
        val identity: Identity,
        val stats: IdentityStats,
        val habits: List<Habit>,
        val isPinned: Boolean = false,
        val whyText: String? = null,
        val isEditingWhy: Boolean = false,
        val pendingWhyDraft: String? = null,
    ) : IdentityDetailState()
    object NotFound : IdentityDetailState()
}

class IdentityDetailViewModel private constructor(
    private val identityRepo: IdentityRepository,
    private val statsUseCase: ComputeIdentityStatsUseCase,
    private val pinUseCase: PinIdentityUseCase,
    private val unpinUseCase: UnpinIdentityUseCase,
    private val removeUseCase: RemoveIdentityUseCase,
    private val updateWhyUseCase: UpdateIdentityWhyUseCase,
    private val userIdProvider: () -> String,
    private val identityId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<IdentityDetailState>(IdentityDetailState.Loading)
    val state: StateFlow<IdentityDetailState> = _state.asStateFlow()

    private val _removeSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val removeSuccess: SharedFlow<Unit> = _removeSuccess.asSharedFlow()

    private var job: Job? = null

    constructor(container: AppContainer, identityId: String) : this(
        identityRepo = container.identityRepository,
        statsUseCase = container.computeIdentityStatsUseCase,
        pinUseCase = container.pinIdentityUseCase,
        unpinUseCase = container.unpinIdentityUseCase,
        removeUseCase = container.removeIdentityUseCase,
        updateWhyUseCase = container.updateIdentityWhyUseCase,
        userIdProvider = { container.currentUserId() },
        identityId = identityId,
    )

    init { observe() }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            identityRepo.observeUserIdentities(userId).collect { userIdentities ->
                val identity = userIdentities.firstOrNull { it.id == identityId }
                if (identity == null) {
                    _state.value = IdentityDetailState.NotFound
                    return@collect
                }
                val row = identityRepo.getUserIdentityRow(userId, identityId)
                val stats = statsUseCase.computeNow(userId, identityId)
                val habits = identityRepo.observeHabitsForIdentity(userId, identityId).first()
                // Preserve any in-progress edit state
                val current = _state.value
                val isEditing = (current as? IdentityDetailState.Loaded)?.isEditingWhy ?: false
                val pendingDraft = (current as? IdentityDetailState.Loaded)?.pendingWhyDraft
                _state.value = IdentityDetailState.Loaded(
                    identity = identity,
                    stats = stats,
                    habits = habits,
                    isPinned = row?.isPinned ?: false,
                    whyText = row?.whyText,
                    isEditingWhy = isEditing,
                    pendingWhyDraft = pendingDraft,
                )
            }
        }
    }

    fun togglePin() {
        val loaded = _state.value as? IdentityDetailState.Loaded ?: return
        viewModelScope.launch {
            val userId = userIdProvider()
            if (loaded.isPinned) {
                unpinUseCase.execute(userId, identityId)
            } else {
                pinUseCase.execute(userId, identityId)
            }
            // No explicit refresh needed — setPinForIdentity triggers observeUserIdentities re-emit
        }
    }

    fun removeIdentity() {
        viewModelScope.launch {
            val userId = userIdProvider()
            runCatching { removeUseCase.execute(userId, identityId) }
                .onSuccess { _removeSuccess.tryEmit(Unit) }
        }
    }

    fun startEditingWhy() {
        val loaded = _state.value as? IdentityDetailState.Loaded ?: return
        _state.value = loaded.copy(isEditingWhy = true, pendingWhyDraft = loaded.whyText.orEmpty())
    }

    fun updateWhyDraft(text: String) {
        val loaded = _state.value as? IdentityDetailState.Loaded ?: return
        _state.value = loaded.copy(pendingWhyDraft = text)
    }

    fun saveWhyText() {
        val loaded = _state.value as? IdentityDetailState.Loaded ?: return
        // Flip out of edit mode immediately so the UI returns to display state.
        // The Flow re-emit from updateWhyText will refresh whyText shortly after.
        _state.value = loaded.copy(isEditingWhy = false, pendingWhyDraft = null)
        viewModelScope.launch {
            val userId = userIdProvider()
            updateWhyUseCase.execute(userId, identityId, loaded.pendingWhyDraft)
        }
    }

    fun cancelEditingWhy() {
        val loaded = _state.value as? IdentityDetailState.Loaded ?: return
        _state.value = loaded.copy(isEditingWhy = false, pendingWhyDraft = null)
    }

    companion object {
        fun forTest(
            identityRepo: IdentityRepository,
            statsUseCase: ComputeIdentityStatsUseCase,
            pinUseCase: PinIdentityUseCase,
            unpinUseCase: UnpinIdentityUseCase,
            removeUseCase: RemoveIdentityUseCase,
            updateWhyUseCase: UpdateIdentityWhyUseCase,
            userIdProvider: () -> String,
            identityId: String,
        ) = IdentityDetailViewModel(
            identityRepo, statsUseCase, pinUseCase, unpinUseCase,
            removeUseCase, updateWhyUseCase, userIdProvider, identityId,
        )
    }
}
