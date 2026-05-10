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

enum class WantSortMode { Name, Recent }

data class WantListUi(
    val seeded: List<WantActivity>,
    val custom: List<WantActivity>,
    val hidden: List<WantActivity>,
    val showHidden: Boolean = false,
    val sortMode: WantSortMode = WantSortMode.Name,
    val helpOpen: Boolean = false,
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
            val sortMode = _state.value.sortMode
            val all = repo.getAllWantActivitiesForUser(userId).let { applySort(it, sortMode) }
            _state.update {
                it.copy(
                    seeded = all.filter { a -> !a.isCustom && a.hiddenAt == null },
                    custom = all.filter { a -> a.isCustom && a.hiddenAt == null },
                    hidden = all.filter { a -> !a.isCustom && a.hiddenAt != null },
                )
            }
        }
    }

    private fun applySort(list: List<WantActivity>, mode: WantSortMode): List<WantActivity> =
        when (mode) {
            WantSortMode.Name -> list.sortedBy { it.name.lowercase() }
            WantSortMode.Recent -> list.sortedByDescending { it.updatedAt }
        }

    fun toggleShowHidden() {
        _state.update { it.copy(showHidden = !it.showHidden) }
    }

    fun cycleSortMode() {
        val next = when (_state.value.sortMode) {
            WantSortMode.Name -> WantSortMode.Recent
            WantSortMode.Recent -> WantSortMode.Name
        }
        _state.update { it.copy(sortMode = next) }
        reload()
    }

    fun setHelpOpen(open: Boolean) {
        _state.update { it.copy(helpOpen = open) }
    }

    fun hide(activityId: String, name: String) {
        viewModelScope.launch {
            repo.hideWantActivity(activityId, userIdProvider(), clock.now())
            reload()
            _state.update { it.copy(toast = "$name hidden") }
        }
    }

    fun unhide(activityId: String) {
        viewModelScope.launch {
            repo.unhideWantActivity(activityId, userIdProvider())
            reload()
        }
    }

    fun consumeToast() { _state.update { it.copy(toast = null) } }

    companion object {
        fun forTest(
            repo: WantActivityRepository,
            userIdProvider: () -> String,
            clock: Clock = Clock.System,
        ) = WantListViewModel(repo, userIdProvider, clock)
    }
}
