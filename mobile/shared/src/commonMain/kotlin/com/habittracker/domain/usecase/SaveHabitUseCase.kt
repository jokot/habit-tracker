package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class SaveHabitUseCase(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val clock: Clock = Clock.System,
) {
    /** Create a new habit. Returns the new habit's id. */
    suspend fun create(
        userId: String,
        name: String,
        unit: String,
        threshold: Double,
        target: Int,
        identityIds: Set<String>,
        templateId: String?,
    ): String {
        validate(name = name, unit = unit, threshold = threshold, target = target, identityIds = identityIds)
        val now = clock.now()
        val id = Uuid.random().toString()
        habitRepo.saveHabit(
            Habit(
                id = id,
                userId = userId,
                templateId = templateId,
                name = name.trim(),
                unit = unit.trim(),
                thresholdPerPoint = threshold,
                dailyTarget = target,
                createdAt = now,
                updatedAt = now,
                syncedAt = null,
                effectiveFrom = now,
                effectiveTo = null,
            )
        )
        identityIds.forEach { identityRepo.linkHabitToIdentities(id, setOf(it)) }
        return id
    }

    /** Update an existing habit. Diffs identity links: add new, soft-remove dropped, resume previously-removed. */
    suspend fun update(
        userId: String,
        habitId: String,
        name: String,
        unit: String,
        threshold: Double,
        target: Int,
        newIdentityIds: Set<String>,
    ) {
        validate(name = name, unit = unit, threshold = threshold, target = target, identityIds = newIdentityIds)
        val now = clock.now()
        val existing = habitRepo.getHabitsForUser(userId).firstOrNull { it.id == habitId }
            ?: error("habit $habitId not found for user $userId")
        habitRepo.saveHabit(
            existing.copy(
                name = name.trim(),
                unit = unit.trim(),
                thresholdPerPoint = threshold,
                dailyTarget = target,
                updatedAt = now,
                syncedAt = null,
            )
        )

        val allLinks = identityRepo.getHabitIdentityLinksForUser(userId).filter { it.habitId == habitId }
        val activeLinkIds = allLinks.filter { it.effectiveTo == null }.map { it.identityId }.toSet()

        val toAdd = newIdentityIds - activeLinkIds
        val toRemove = activeLinkIds - newIdentityIds

        // linkHabitToIdentities is additive + idempotent; for resume cases, the underlying
        // upsert clears effectiveTo by re-inserting the row with effectiveTo = null.
        toAdd.forEach { identityRepo.linkHabitToIdentities(habitId, setOf(it)) }
        toRemove.forEach { identityRepo.markHabitIdentityRemoved(habitId, it, now) }
    }

    private fun validate(
        name: String,
        unit: String,
        threshold: Double,
        target: Int,
        identityIds: Set<String>,
    ) {
        require(name.trim().isNotEmpty()) { "name must not be blank" }
        require(unit.trim().isNotEmpty()) { "unit must not be blank" }
        require(threshold > 0.0) { "threshold must be > 0" }
        require(target >= 1) { "target must be >= 1" }
        require(identityIds.isNotEmpty()) { "at least one identity required" }
    }
}
