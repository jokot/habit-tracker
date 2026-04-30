package com.jktdeveloper.habitto.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.AppContainer
import com.habittracker.data.local.SeedData
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.TemplateWithIdentities
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class OnboardingStep { IDENTITY, HABITS, WANTS, SYNC }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.IDENTITY,
    val identities: List<Identity> = SeedData.identities,
    val selectedIdentityIds: Set<String> = emptySet(),
    val habitTemplates: List<TemplateWithIdentities> = emptyList(),
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

    fun toggleIdentity(identityId: String) {
        val current = _uiState.value.selectedIdentityIds.toMutableSet()
        if (current.contains(identityId)) current.remove(identityId) else current.add(identityId)
        val newTemplates = container.getHabitTemplatesForIdentitiesUseCase.execute(current)
        val newTemplateIds = newTemplates.map { it.template.id }.toSet()
        val keptSelections = _uiState.value.selectedTemplateIds.intersect(newTemplateIds)
        _uiState.value = _uiState.value.copy(
            selectedIdentityIds = current,
            habitTemplates = newTemplates,
            selectedTemplateIds = keptSelections,
        )
    }

    fun continueFromIdentity() {
        if (_uiState.value.selectedIdentityIds.isEmpty()) return
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

    fun continueFromWants() {
        _uiState.value = _uiState.value.copy(step = OnboardingStep.SYNC)
    }

    fun back() {
        val current = _uiState.value.step
        val prev = when (current) {
            OnboardingStep.IDENTITY -> return
            OnboardingStep.HABITS -> OnboardingStep.IDENTITY
            OnboardingStep.WANTS -> OnboardingStep.HABITS
            OnboardingStep.SYNC -> OnboardingStep.WANTS
        }
        _uiState.value = _uiState.value.copy(step = prev)
    }

    fun finish() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = container.currentUserId()
            val state = _uiState.value
            val selectedTemplates = state.habitTemplates
                .filter { it.template.id in state.selectedTemplateIds }
                .map { it.template }
            val selectedActivities = state.wantActivities.filter { it.id in state.selectedActivityIds }

            container.setupUserIdentitiesUseCase
                .execute(userId, state.selectedIdentityIds)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            val templateIdToHabitId = container.setupUserHabitsUseCase
                .execute(userId, selectedTemplates)
                .getOrElse { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            container.linkOnboardingHabitsToIdentitiesUseCase
                .execute(userId, templateIdToHabitId, state.selectedIdentityIds)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            container.setupUserWantActivitiesUseCase
                .execute(userId, selectedActivities)
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                    return@launch
                }
            _finished.emit(Unit)
        }
    }
}
