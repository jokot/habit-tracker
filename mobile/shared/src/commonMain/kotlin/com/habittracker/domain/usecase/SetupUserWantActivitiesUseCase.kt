package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.domain.model.WantActivity
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.datetime.Clock

class SetupUserWantActivitiesUseCase(
    private val wantActivityRepository: WantActivityRepository,
    private val seedActivities: List<WantActivity>,
    private val clock: Clock = Clock.System,
) {
    /**
     * Inserts the supplied list of activities for new-user onboarding. Caller-supplied
     * IDs are persisted as-is (no fresh UUID assignment). For idempotent updates use
     * [reconcile] instead.
     */
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
     * Idempotent reconciliation. Matches existing seeded rows by name (case-insensitive)
     * to support cross-device sync where the same canonical seed may have a different UUID
     * per user. Missing seeds are inserted with a fresh per-user UUID — sharing seed UUIDs
     * across users causes Postgres `ON CONFLICT(id) DO UPDATE` to hit RLS USING violations
     * when another user already claimed the row.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun reconcile(userId: String): Result<Unit> = runCatching {
        val existing = wantActivityRepository.getAllWantActivitiesForUser(userId)
        val existingSeedNames = existing
            .filter { !it.isCustom }
            .map { it.name.lowercase() }
            .toSet()
        val now by lazy { clock.now() }
        seedActivities
            .filter { it.name.lowercase() !in existingSeedNames }
            .forEach { seed ->
                wantActivityRepository.saveWantActivity(
                    seed.copy(id = Uuid.random().toString(), updatedAt = now),
                    userId,
                )
            }
    }
}
