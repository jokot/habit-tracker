package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.IdentityWithStats
import com.habittracker.domain.usecase.ObserveUserIdentitiesWithStatsUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class IdentityListState {
    object Loading : IdentityListState()
    data class Loaded(val items: List<IdentityWithStats>) : IdentityListState()
}

class IdentityListViewModel private constructor(
    private val aggregate: ObserveUserIdentitiesWithStatsUseCase,
    private val userIdProvider: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow<IdentityListState>(IdentityListState.Loading)
    val state: StateFlow<IdentityListState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer) : this(
        aggregate = container.observeUserIdentitiesWithStatsUseCase,
        userIdProvider = { container.currentUserId() },
    )

    init { refresh() }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            aggregate.execute(userIdProvider()).collect { items ->
                _state.value = IdentityListState.Loaded(items)
            }
        }
    }

    companion object {
        fun forTest(
            aggregate: ObserveUserIdentitiesWithStatsUseCase,
            userIdProvider: () -> String,
        ) = IdentityListViewModel(aggregate, userIdProvider)
    }
}
