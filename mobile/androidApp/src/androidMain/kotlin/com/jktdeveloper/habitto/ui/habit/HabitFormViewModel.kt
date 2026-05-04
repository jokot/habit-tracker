package com.jktdeveloper.habitto.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.SaveHabitUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HabitFormMode { Create, Edit }

data class HabitFormState(
    val mode: HabitFormMode = HabitFormMode.Create,
    val name: String = "",
    val unit: String = "",
    val threshold: Double = 1.0,
    val target: Int = 1,
    val selectedIdentityIds: Set<String> = emptySet(),
    val availableIdentities: List<Identity> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = name.trim().isNotEmpty()
            && unit.trim().isNotEmpty()
            && threshold > 0.0
            && target >= 1
            && selectedIdentityIds.isNotEmpty()
            && !isSaving
}

class HabitFormViewModel(
    private val habitId: String?,
    private val prefillIdentityId: String?,
    private val userIdProvider: () -> String,
    private val saveUseCase: SaveHabitUseCase,
    private val deleteUseCase: DeleteHabitUseCase,
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val triggerSync: () -> Unit,
) : ViewModel() {

    constructor(container: AppContainer, habitId: String?, prefillIdentityId: String?) : this(
        habitId = habitId,
        prefillIdentityId = prefillIdentityId,
        userIdProvider = { container.currentUserId() },
        saveUseCase = container.saveHabitUseCase,
        deleteUseCase = container.deleteHabitUseCase,
        habitRepo = container.habitRepository,
        identityRepo = container.identityRepository,
        triggerSync = { com.jktdeveloper.habitto.sync.SyncTriggers.enqueue(container.appContext, com.habittracker.data.sync.SyncReason.POST_LOG) },
    )

    private val _state = MutableStateFlow(HabitFormState())
    val state: StateFlow<HabitFormState> = _state.asStateFlow()

    private val _saveSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSuccess: SharedFlow<Unit> = _saveSuccess.asSharedFlow()

    private val _deleteSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deleteSuccess: SharedFlow<Unit> = _deleteSuccess.asSharedFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val userId = userIdProvider()
        val identities = identityRepo.observeUserIdentities(userId).first()
        if (habitId == null) {
            _state.update {
                HabitFormState(
                    mode = HabitFormMode.Create,
                    selectedIdentityIds = prefillIdentityId?.let { setOf(it) } ?: emptySet(),
                    availableIdentities = identities,
                    isLoading = false,
                )
            }
            return
        }
        val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
        if (habit == null) {
            _state.update { it.copy(isLoading = false, error = "Habit not found") }
            return
        }
        val activeLinks = identityRepo.getHabitIdentityLinksForUser(userId)
            .filter { it.habitId == habitId && it.effectiveTo == null }
            .map { it.identityId }
            .toSet()
        _state.update {
            HabitFormState(
                mode = HabitFormMode.Edit,
                name = habit.name,
                unit = habit.unit,
                threshold = habit.thresholdPerPoint,
                target = habit.dailyTarget,
                selectedIdentityIds = activeLinks,
                availableIdentities = identities,
                isLoading = false,
            )
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onUnitChange(v: String) = _state.update { it.copy(unit = v) }
    fun onThresholdChange(v: Double) = _state.update { it.copy(threshold = v) }
    fun onTargetChange(v: Int) = _state.update { it.copy(target = v) }
    fun onIdentitiesChange(ids: Set<String>) = _state.update { it.copy(selectedIdentityIds = ids) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val userId = userIdProvider()
            runCatching {
                if (s.mode == HabitFormMode.Create) {
                    saveUseCase.create(
                        userId = userId,
                        name = s.name,
                        unit = s.unit,
                        threshold = s.threshold,
                        target = s.target,
                        identityIds = s.selectedIdentityIds,
                        templateId = null,
                    )
                } else {
                    saveUseCase.update(
                        userId = userId,
                        habitId = habitId!!,
                        name = s.name,
                        unit = s.unit,
                        threshold = s.threshold,
                        target = s.target,
                        newIdentityIds = s.selectedIdentityIds,
                    )
                }
            }.onSuccess {
                triggerSync()
                _saveSuccess.tryEmit(Unit)
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message ?: "Save failed") }
            }
        }
    }

    fun delete() {
        if (habitId == null) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val userId = userIdProvider()
            runCatching { deleteUseCase.execute(userId, habitId) }
                .onSuccess {
                    triggerSync()
                    _deleteSuccess.tryEmit(Unit)
                }
                .onFailure { e ->
                    _state.update { it.copy(isSaving = false, error = e.message ?: "Delete failed") }
                }
        }
    }
}
