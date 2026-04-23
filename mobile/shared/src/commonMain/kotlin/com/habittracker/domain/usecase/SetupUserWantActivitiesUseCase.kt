package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity

class SetupUserWantActivitiesUseCase(private val wantActivityRepository: WantActivityRepository) {
    suspend fun execute(userId: String, activities: List<WantActivity>): Result<Unit> =
        runCatching {
            activities.forEach { activity ->
                wantActivityRepository.saveWantActivity(activity, userId)
            }
        }
}
