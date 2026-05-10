package com.jktdeveloper.habitto.ui.want

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class WantListUi(
    val seeded: List<WantActivity>,
    val custom: List<WantActivity>,
    val hidden: List<WantActivity>,
    val showHidden: Boolean = false,
    val toast: String? = null,
)

class WantListViewModel private constructor(
    private val repo: WantActivityRepository,
    private val userIdProvider: () -> String,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WantListUi(seeded = emptyList(), custom = emptyList(), hidden = emptyList())
    )
    val state: StateFlow<WantListUi> = _state.asStateFlow()

    constructor(container: AppContainer) : this(
        repo = container.wantActivityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            val userId = userIdProvider()
            val all = repo.getAllWantActivitiesForUser(userId).sortedBy { it.name.lowercase() }
            _state.value = _state.value.copy(
                seeded = all.filter { !it.isCustom && it.hiddenAt == null },
                custom = all.filter { it.isCustom && it.hiddenAt == null },
                hidden = all.filter { !it.isCustom && it.hiddenAt != null },
            )
        }
    }

    fun toggleShowHidden() {
        _state.value = _state.value.copy(showHidden = !_state.value.showHidden)
    }

    fun hide(activityId: String, name: String) {
        viewModelScope.launch {
            repo.hideWantActivity(activityId, userIdProvider(), clock.now())
            reload()
            _state.value = _state.value.copy(toast = "$name hidden")
        }
    }

    fun unhide(activityId: String) {
        viewModelScope.launch {
            repo.unhideWantActivity(activityId, userIdProvider())
            reload()
        }
    }

    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    companion object {
        fun forTest(
            repo: WantActivityRepository,
            userIdProvider: () -> String,
            clock: Clock = Clock.System,
        ) = WantListViewModel(repo, userIdProvider, clock)
    }
}
