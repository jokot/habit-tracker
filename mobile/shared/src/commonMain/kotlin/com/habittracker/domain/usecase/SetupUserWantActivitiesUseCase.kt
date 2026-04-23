package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import kotlinx.datetime.Clock

class SetupUserWantActivitiesUseCase(private val wantActivityRepository: WantActivityRepository) {
    suspend fun execute(userId: String, activities: List<WantActivity>): Result<Unit> =
        runCatching {
            val now = Clock.System.now()
            activities.forEach { activity ->
                wantActivityRepository.saveWantActivity(activity.copy(updatedAt = now), userId)
            }
        }
}
