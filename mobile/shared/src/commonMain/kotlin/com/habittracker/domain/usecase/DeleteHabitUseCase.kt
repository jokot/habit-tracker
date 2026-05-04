package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import kotlinx.datetime.Clock

class DeleteHabitUseCase(
    private val habitRepo: HabitRepository,
    private val clock: Clock = Clock.System,
) {
    suspend fun execute(userId: String, habitId: String) {
        habitRepo.markHabitDeleted(habitId, userId, clock.now())
    }
}
