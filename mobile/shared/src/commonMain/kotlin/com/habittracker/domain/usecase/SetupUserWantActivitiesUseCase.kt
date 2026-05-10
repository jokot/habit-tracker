package com.habittracker.domain.usecase

import com.habittracker.data.local.SeedData
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import kotlinx.datetime.Clock

class SetupUserWantActivitiesUseCase(
    private val wantActivityRepository: WantActivityRepository,
    private val clock: Clock = Clock.System,
) {
    /** Insert a known list (used by onboarding seed for new users). */
    suspend fun execute(userId: String, activities: List<WantActivity>): Result<Unit> =
        runCatching {
            val now = clock.now()
            activities.forEach { activity ->
                wantActivityRepository.saveWantActivity(
                    activity.copy(updatedAt = now),
                    userId,
                )
            }
        }

    /**
     * Idempotent reconciliation. For each canonical seeded id, if the user has no
     * row with that id, insert it. Existing rows untouched.
     */
    suspend fun reconcile(userId: String): Result<Unit> = runCatching {
        val existing = wantActivityRepository.getAllWantActivitiesForUser(userId)
        val existingIds = existing.map { it.id }.toSet()
        val now = clock.now()
        SeedData.wantActivities
            .filter { it.id !in existingIds }
            .forEach { seed ->
                wantActivityRepository.saveWantActivity(
                    seed.copy(updatedAt = now),
                    userId,
                )
            }
    }
}
