package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AddIdentityWithHabitsUseCase(
    private val habitRepo: HabitRepository,
    private val identityRepo: IdentityRepository,
    private val templates: GetHabitTemplatesForIdentitiesUseCase,
    private val clock: Clock = Clock.System,
) {
    /**
     * Inserts a new user_identity row and links/creates habits for each selectedTemplateId.
     * - If the user already has a habit with the given templateId, only a habit_identities link is added.
     * - Otherwise, a new habit row is created from the template (defaults applied) and linked.
     */
    suspend fun execute(
        userId: String,
        identityId: String,
        selectedTemplateIds: Set<String>,
    ) {
        // 1. Insert user_identity additively: read current active set, add the new identity.
        val currentlyActive = identityRepo.observeUserIdentities(userId).first().map { it.id }.toSet()
        identityRepo.setUserIdentities(userId, currentlyActive + identityId)

        // 2. For each selected template, link existing habit OR create + link.
        val ownedByTemplate: Map<String, Habit> =
            habitRepo.getHabitsForUser(userId).associateBy { it.templateId }

        // GetHabitTemplatesForIdentitiesUseCase returns List<TemplateWithIdentities>.
        // Build a map of templateId -> HabitTemplate for the given identity.
        val tplsById = templates.execute(setOf(identityId))
            .filter { twi -> twi.recommendedBy.any { it.id == identityId } }
            .associate { twi -> twi.template.id to twi.template }

        for (templateId in selectedTemplateIds) {
            val existing = ownedByTemplate[templateId]
            if (existing != null) {
                identityRepo.linkHabitToIdentities(existing.id, setOf(identityId))
                continue
            }
            val tpl = tplsById[templateId] ?: continue
            val now = clock.now()
            val habit = Habit(
                id = Uuid.random().toString(),
                userId = userId,
                templateId = templateId,
                name = tpl.name,
                unit = tpl.unit,
                thresholdPerPoint = tpl.defaultThreshold,
                dailyTarget = tpl.defaultDailyTarget,
                createdAt = now,
                updatedAt = now,
                syncedAt = null,
                effectiveFrom = now,
            )
            habitRepo.saveHabit(habit)
            identityRepo.linkHabitToIdentities(habit.id, setOf(identityId))
        }
    }
}
