package com.jktdeveloper.habitto.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HabitListState {
    data object Loading : HabitListState
    data class Loaded(val habits: List<HabitRowItem>) : HabitListState
}

data class HabitRowItem(
    val habit: Habit,
    val identityNames: List<String>,
    val firstIdentity: Identity? = null,
)

class HabitListViewModel private constructor(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val userIdProvider: () -> String,
) : ViewModel() {

    private val _state = MutableStateFlow<HabitListState>(HabitListState.Loading)
    val state: StateFlow<HabitListState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer) : this(
        habitRepo = container.habitRepository,
        identityRepo = container.identityRepository,
        userIdProvider = { container.currentUserId() },
    )

    init { observe() }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            habitRepo.observeHabitsForUser(userId).collect { habits ->
                val activeHabits = habits.filter { it.effectiveTo == null }
                val identities = identityRepo.observeUserIdentities(userId).first()
                val identityById = identities.associateBy { it.id }
                val links = identityRepo.getHabitIdentityLinksForUser(userId)
                    .filter { it.effectiveTo == null }
                    .groupBy { it.habitId }

                val rows = activeHabits
                    .sortedBy { it.name.lowercase() }
                    .map { habit ->
                        val identityIds = links[habit.id]?.map { it.identityId }.orEmpty()
                        val linkedIdentities = identityIds.mapNotNull { identityById[it] }
                        HabitRowItem(
                            habit = habit,
                            identityNames = linkedIdentities.map { it.name },
                            firstIdentity = linkedIdentities.firstOrNull(),
                        )
                    }

                _state.value = HabitListState.Loaded(rows)
            }
        }
    }

    companion object {
        fun forTest(
            habitRepo: HabitRepository,
            identityRepo: IdentityRepository,
            userIdProvider: () -> String,
        ) = HabitListViewModel(habitRepo, identityRepo, userIdProvider)
    }
}
