package com.jktdeveloper.habitto.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.PerHabitStreakResult
import com.habittracker.domain.usecase.ComputePerHabitStreakUseCase
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HabitDetailState {
    data object Loading : HabitDetailState
    data object NotFound : HabitDetailState
    data class Loaded(
        val habit: Habit,
        val identityNames: List<String>,
        val streak: PerHabitStreakResult,
    ) : HabitDetailState
}

class HabitDetailViewModel private constructor(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val streakUseCase: ComputePerHabitStreakUseCase,
    private val userIdProvider: () -> String,
    private val habitId: String,
) : ViewModel() {

    private val _state = MutableStateFlow<HabitDetailState>(HabitDetailState.Loading)
    val state: StateFlow<HabitDetailState> = _state.asStateFlow()

    private var job: Job? = null

    constructor(container: AppContainer, habitId: String) : this(
        habitRepo = container.habitRepository,
        identityRepo = container.identityRepository,
        streakUseCase = container.computePerHabitStreakUseCase,
        userIdProvider = { container.currentUserId() },
        habitId = habitId,
    )

    init { observe() }

    private fun observe() {
        job?.cancel()
        job = viewModelScope.launch {
            val userId = userIdProvider()
            streakUseCase.observe(userId, habitId).collect { streak ->
                val habit = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
                if (habit == null) {
                    _state.value = HabitDetailState.NotFound
                    return@collect
                }
                val identities = identityRepo.observeUserIdentities(userId).first()
                val identityById = identities.associateBy { it.id }
                val links = identityRepo.getHabitIdentityLinksForUser(userId)
                    .filter { it.habitId == habitId && it.effectiveTo == null }
                val identityNames = links.mapNotNull { identityById[it.identityId]?.name }
                _state.value = HabitDetailState.Loaded(habit, identityNames, streak)
            }
        }
    }

    companion object {
        fun forTest(
            habitRepo: HabitRepository,
            identityRepo: IdentityRepository,
            streakUseCase: ComputePerHabitStreakUseCase,
            userIdProvider: () -> String,
            habitId: String,
        ) = HabitDetailViewModel(habitRepo, identityRepo, streakUseCase, userIdProvider, habitId)
    }
}
