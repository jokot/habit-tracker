package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository

class IsOnboardedUseCase(private val habitRepository: HabitRepository) {
    suspend fun execute(userId: String): Boolean =
        habitRepository.getHabitsForUser(userId).isNotEmpty()
}
