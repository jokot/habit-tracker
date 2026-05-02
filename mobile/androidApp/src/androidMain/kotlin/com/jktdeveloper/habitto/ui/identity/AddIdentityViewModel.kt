package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.sync.SyncReason
import com.habittracker.domain.model.Identity
import com.habittracker.domain.usecase.AddIdentityWithHabitsUseCase
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentitiesUseCase
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.sync.SyncTriggers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitChoice(
    val templateId: String,
    val name: String,
    val threshold: Double,
    val target: Int,
    val unit: String,
    val checked: Boolean,
    val alreadyTracking: Boolean,
)

data class AddIdentityUiState(
    val step: Int = 1,
    val candidates: List<Identity> = emptyList(),
    val selectedIdentity: Identity? = null,
    val recommendedHabits: List<HabitChoice> = emptyList(),
    val isCommitting: Boolean = false,
    val error: String? = null,
)

class AddIdentityViewModel(
    private val identityRepo: IdentityRepository,
    private val habitRepo: HabitRepository,
    private val templates: GetHabitTemplatesForIdentitiesUseCase,
    private val addUseCase: AddIdentityWithHabitsUseCase,
    private val userIdProvider: () -> String,
    private val triggerSync: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(AddIdentityUiState())
    val state: StateFlow<AddIdentityUiState> = _state.asStateFlow()

    private val _commitSuccess = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val commitSuccess: SharedFlow<Unit> = _commitSuccess.asSharedFlow()

    constructor(container: AppContainer) : this(
        identityRepo = container.identityRepository,
        habitRepo = container.habitRepository,
        templates = container.getHabitTemplatesForIdentitiesUseCase,
        addUseCase = container.addIdentityWithHabitsUseCase,
        userIdProvider = { container.currentUserId() },
        triggerSync = { SyncTriggers.enqueue(container.appContext, SyncReason.POST_LOG) },
    )

    init { loadCandidates() }

    private fun loadCandidates() {
        viewModelScope.launch {
            val userId = userIdProvider()
            val all = identityRepo.getAllIdentities()
            val active = identityRepo.observeUserIdentities(userId).first().map { it.id }.toSet()
            val candidates = all.filter { it.id !in active }
            _state.update { it.copy(candidates = candidates, selectedIdentity = candidates.firstOrNull()) }
        }
    }

    fun selectIdentity(identity: Identity) {
        _state.update { it.copy(selectedIdentity = identity) }
    }

    fun advanceToStep2() {
        val selected = _state.value.selectedIdentity ?: return
        viewModelScope.launch {
            val userId = userIdProvider()
            val ownedTemplateIds = habitRepo.getHabitsForUser(userId).map { it.templateId }.toSet()
            val tplsForIdentity = templates.execute(setOf(selected.id))
                .filter { tw -> tw.recommendedBy.any { it.id == selected.id } }
                .map { it.template }
            val choices = tplsForIdentity.map { tpl ->
                HabitChoice(
                    templateId = tpl.id,
                    name = tpl.name,
                    threshold = tpl.defaultThreshold,
                    target = tpl.defaultDailyTarget,
                    unit = tpl.unit,
                    checked = true,
                    alreadyTracking = tpl.id in ownedTemplateIds,
                )
            }
            _state.update { it.copy(step = 2, recommendedHabits = choices) }
        }
    }

    fun goBackToStep1() {
        _state.update { it.copy(step = 1) }
    }

    fun toggleHabit(templateId: String) {
        _state.update { current ->
            current.copy(
                recommendedHabits = current.recommendedHabits.map {
                    if (it.templateId == templateId) it.copy(checked = !it.checked) else it
                }
            )
        }
    }

    fun commit() {
        val selected = _state.value.selectedIdentity ?: return
        val selectedTemplateIds = _state.value.recommendedHabits.filter { it.checked }.map { it.templateId }.toSet()
        _state.update { it.copy(isCommitting = true, error = null) }
        viewModelScope.launch {
            val userId = userIdProvider()
            runCatching { addUseCase.execute(userId, selected.id, selectedTemplateIds) }
                .onSuccess {
                    triggerSync()
                    _state.update { it.copy(isCommitting = false) }
                    _commitSuccess.tryEmit(Unit)
                }
                .onFailure { e ->
                    _state.update { it.copy(isCommitting = false, error = "Couldn't save — try again. (${e.message})") }
                }
        }
    }
}
