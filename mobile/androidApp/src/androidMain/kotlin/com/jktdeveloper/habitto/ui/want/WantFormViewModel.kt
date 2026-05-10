package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface FormMode {
    data object New : FormMode
    data class Edit(val activityId: String) : FormMode
}

data class WantFormUi(
    val mode: FormMode,
    val name: String = "",
    val unit: String = "min",
    val unitsInput: String = "1",
    val iconKey: String = "more_horiz",
    val isSaving: Boolean = false,
    val validationError: String? = null,
    val saved: Boolean = false,
)

class WantFormViewModel private constructor(
    private val mode: FormMode,
    private val wantActivityRepo: WantActivityRepository,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(WantFormUi(mode = mode))
    val state: StateFlow<WantFormUi> = _state.asStateFlow()

    constructor(mode: FormMode, container: AppContainer) : this(
        mode = mode,
        wantActivityRepo = container.wantActivityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { load() }

    private fun load() {
        viewModelScope.launch {
            when (val m = mode) {
                FormMode.New -> Unit
                is FormMode.Edit -> {
                    val userId = userIdProvider()
                    val w = wantActivityRepo.getAllWantActivitiesForUser(userId)
                        .firstOrNull { it.id == m.activityId } ?: return@launch
                    _state.update { _ ->
                        WantFormUi(
                            mode = m,
                            name = w.name,
                            unit = w.unit,
                            unitsInput = w.unitsPerPoint.toString(),
                            iconKey = w.iconKey ?: "more_horiz",
                        )
                    }
                }
            }
        }
    }

    fun onName(v: String) { _state.update { it.copy(name = v, validationError = null) } }
    fun onUnit(v: String) { _state.update { it.copy(unit = v, validationError = null) } }
    fun onIconKey(v: String) { _state.update { it.copy(iconKey = v) } }

    fun onUnitsInput(v: String) {
        _state.update { it.copy(unitsInput = v, validationError = null) }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(validationError = "Name required") }
            return
        }
        val units = s.unitsInput.toIntOrNull()
        if (units == null || units < 1) {
            _state.update { it.copy(validationError = "Units per point must be ≥ 1") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val userId = userIdProvider()
            val activity = when (val m = s.mode) {
                FormMode.New -> WantActivity(
                    id = Uuid.random().toString(),
                    name = s.name,
                    unit = s.unit,
                    unitsPerPoint = units,
                    isCustom = true,
                    createdByUserId = userId,
                    iconKey = s.iconKey,
                    updatedAt = clock.now(),
                )
                is FormMode.Edit -> {
                    val existing = wantActivityRepo.getAllWantActivitiesForUser(userId)
                        .firstOrNull { it.id == m.activityId } ?: return@launch
                    existing.copy(
                        name = s.name,
                        unit = s.unit,
                        unitsPerPoint = units,
                        iconKey = s.iconKey,
                        updatedAt = clock.now(),
                    )
                }
            }
            wantActivityRepo.saveWantActivity(activity, userId)
            _state.update { it.copy(isSaving = false, saved = true) }
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val m = mode as? FormMode.Edit ?: return
        viewModelScope.launch {
            wantActivityRepo.hideWantActivity(m.activityId, userIdProvider(), clock.now())
            onDone()
        }
    }

    companion object {
        fun forTest(
            mode: FormMode,
            wantActivityRepo: WantActivityRepository,
            userIdProvider: () -> String,
            clock: Clock = Clock.System,
        ) = WantFormViewModel(mode, wantActivityRepo, userIdProvider, clock)
    }
}
