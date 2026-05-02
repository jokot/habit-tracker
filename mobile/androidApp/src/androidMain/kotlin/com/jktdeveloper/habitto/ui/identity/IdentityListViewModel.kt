package com.jktdeveloper.habitto.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.IdentityRepository
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
    data class Loaded(
        val items: List<IdentityWithStats>,
        val pinnedIdentityId: String? = null,
    ) : IdentityListState()
}

class IdentityListViewModel private constructor(
    private val aggregate: ObserveUserIdentitiesWithStatsUseCase,
    private val identityRepo: IdentityRepository,
    private val userIdProvider: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow<IdentityListState>(IdentityListState.Loading)
    val state: StateFlow<IdentityListState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer) : this(
        aggregate = container.observeUserIdentitiesWithStatsUseCase,
        identityRepo = container.identityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { refresh() }

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            aggregate.execute(userId).collect { items ->
                val pinnedId = identityRepo.getPinnedIdentityIdForUser(userId)
                _state.value = IdentityListState.Loaded(items = items, pinnedIdentityId = pinnedId)
            }
        }
    }

    companion object {
        fun forTest(
            aggregate: ObserveUserIdentitiesWithStatsUseCase,
            identityRepo: IdentityRepository,
            userIdProvider: () -> String,
        ) = IdentityListViewModel(aggregate, identityRepo, userIdProvider)
    }
}
