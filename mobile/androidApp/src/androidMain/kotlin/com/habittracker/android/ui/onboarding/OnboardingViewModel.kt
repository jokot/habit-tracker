package com.habittracker.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.android.AppContainer
import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.HabitTemplate
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { IDENTITY, HABITS, WANTS }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.IDENTITY,
    val identities: List<Identity> = SeedData.identities,
    val selectedIdentityId: String? = null,
    val habitTemplates: List<HabitTemplate> = emptyList(),
    val selectedTemplateIds: Set<String> = emptySet(),
    val wantActivities: List<WantActivity> = SeedData.wantActivities,
    val selectedActivityIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>()
    val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    fun selectIdentity(identityId: String) {
        val templates = container.getHabitTemplatesForIdentityUseCase.execute(identityId)
        _uiState.value = _uiState.value.copy(
            selectedIdentityId = identityId,
            habitTemplates = templates,
            selectedTemplateIds = emptySet(),
        )
    }

    fun continueFromIdentity() {
        if (_uiState.value.selectedIdentityId == null) return
        _uiState.value = _uiState.value.copy(step = OnboardingStep.HABITS)
    }

    fun toggleHabit(templateId: String) {
        val current = _uiState.value.selectedTemplateIds.toMutableSet()
        if (current.contains(templateId)) current.remove(templateId) else current.add(templateId)
        _uiState.value = _uiState.value.copy(selectedTemplateIds = current)
    }

    fun continueFromHabits() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.WANTS)
    }

    fun toggleWantActivity(activityId: String) {
        val current = _uiState.value.selectedActivityIds.toMutableSet()
        if (current.contains(activityId)) current.remove(activityId) else current.add(activityId)
        _uiState.value = _uiState.value.copy(selectedActivityIds = current)
    }

    fun finish() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = container.currentUserId()
            val state = _uiState.value
            val selectedTemplates = state.habitTemplates.filter { it.id in state.selectedTemplateIds }
            val selectedActivities = state.wantActivities.filter { it.id in state.selectedActivityIds }

            container.setupUserHabitsUseCase.execute(userId, selectedTemplates)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            container.setupUserWantActivitiesUseCase.execute(userId, selectedActivities)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            _finished.emit(Unit)
        }
    }
}
