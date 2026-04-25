package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class SetupUserWantActivitiesUseCase(private val wantActivityRepository: WantActivityRepository) {
    @OptIn(ExperimentalUuidApi::class)
    suspend fun execute(userId: String, activities: List<WantActivity>): Result<Unit> =
        runCatching {
            val now = Clock.System.now()
            activities.forEach { activity ->
                // Generate a fresh UUID per saved row so the user's local copy of a seeded
                // activity doesn't collide with the server's seed UUID (which is owned by
                // user_id = NULL and would fail the user-scoped RLS policy on upsert).
                wantActivityRepository.saveWantActivity(
                    activity.copy(
                        id = Uuid.random().toString(),
                        updatedAt = now,
                    ),
                    userId,
                )
            }
        }
}
