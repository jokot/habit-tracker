package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class FakeIdentityRepository(
    private val seed: List<Identity> = emptyList(),
) : IdentityRepository {

    private val seedFlow = MutableStateFlow(seed)
    private val userIdentities = MutableStateFlow<List<UserIdentityRow>>(emptyList())
    private val habitIdentities = MutableStateFlow<List<HabitIdentityRow>>(emptyList())
    private val habits = MutableStateFlow<List<Habit>>(emptyList())

    fun seedHabit(habit: Habit) {
        habits.value = habits.value.filterNot { it.id == habit.id } + habit
    }

    val userIdentitiesSnapshot: List<UserIdentityRow> get() = userIdentities.value
    val habitIdentitiesSnapshot: List<HabitIdentityRow> get() = habitIdentities.value

    override suspend fun getAllIdentities(): List<Identity> = seedFlow.value

    override suspend fun upsertIdentities(identities: List<Identity>) {
        seedFlow.value = (seedFlow.value.associateBy { it.id } + identities.associateBy { it.id }).values.toList()
    }

    override fun observeUserIdentities(userId: String): Flow<List<Identity>> =
        combine(userIdentities, seedFlow) { rows, seeds ->
            val map = seeds.associateBy { it.id }
            rows.filter { it.userId == userId && it.removedAt == null }
                .sortedBy { it.addedAt }
                .mapNotNull { map[it.identityId] }
        }

    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {
        val now = Clock.System.now()
        val existing = userIdentities.value.filter { it.userId == userId }
        val keep = existing.filter { it.identityId in identityIds }
        val add = (identityIds - keep.map { it.identityId }.toSet()).map {
            UserIdentityRow(userId = userId, identityId = it, addedAt = now, syncedAt = null)
        }
        val others = userIdentities.value.filter { it.userId != userId }
        userIdentities.value = others + keep + add
    }

    override suspend fun clearUserIdentitiesForUser(userId: String) {
        userIdentities.value = userIdentities.value.filter { it.userId != userId }
    }

    override suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow> =
        userIdentities.value.filter { it.userId == userId && it.syncedAt == null }

    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.identityId == identityId) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {
        userIdentities.value = userIdentities.value.filterNot { it.userId == row.userId && it.identityId == row.identityId } + row
    }

    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.identityId == identityId) {
                it.copy(isPinned = isPinned, syncedAt = null)
            } else it
        }
    }

    override suspend fun clearPinForUser(userId: String) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.isPinned) it.copy(isPinned = false, syncedAt = null) else it
        }
    }

    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.identityId == identityId) {
                it.copy(whyText = whyText, syncedAt = null)
            } else it
        }
    }

    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.identityId == identityId) {
                it.copy(removedAt = removedAt, isPinned = false, syncedAt = null)
            } else it
        }
    }

    override suspend fun setPinAtomically(userId: String, identityId: String) {
        clearPinForUser(userId)
        setPinForIdentity(userId, identityId, isPinned = true)
    }

    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
        val now = Clock.System.now()
        val keep = habitIdentities.value.filterNot { it.habitId == habitId && it.identityId !in identityIds }
        val existingIds = keep.filter { it.habitId == habitId }.map { it.identityId }.toSet()
        val add = (identityIds - existingIds).map {
            HabitIdentityRow(habitId = habitId, identityId = it, addedAt = now, syncedAt = null)
        }
        habitIdentities.value = keep + add
    }

    override suspend fun clearHabitIdentitiesForUser(userId: String) {
        val userHabitIds = habits.value.filter { it.userId == userId }.map { it.id }.toSet()
        habitIdentities.value = habitIdentities.value.filterNot { it.habitId in userHabitIds }
    }

    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow> {
        val userHabitIds = habits.value.filter { it.userId == userId }.map { it.id }.toSet()
        return habitIdentities.value.filter { it.habitId in userHabitIds && it.syncedAt == null }
    }

    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) {
        habitIdentities.value = habitIdentities.value.map {
            if (it.habitId == habitId && it.identityId == identityId) it.copy(syncedAt = syncedAt) else it
        }
    }

    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {
        habitIdentities.value = habitIdentities.value.filterNot { it.habitId == row.habitId && it.identityId == row.identityId } + row
    }

    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> =
        combine(habits, habitIdentities) { hs, his ->
            val habitIds = his.filter { it.identityId == identityId }.map { it.habitId }.toSet()
            hs.filter { it.userId == userId && it.id in habitIds }
        }

    fun seedUserIdentity(
        userId: String,
        identityId: String,
        isPinned: Boolean = false,
        whyText: String? = null,
        removedAt: Instant? = null,
    ) {
        val row = UserIdentityRow(
            userId = userId,
            identityId = identityId,
            addedAt = Clock.System.now(),
            syncedAt = null,
            isPinned = isPinned,
            whyText = whyText,
            removedAt = removedAt,
        )
        userIdentities.value = userIdentities.value
            .filterNot { it.userId == userId && it.identityId == identityId } + row
    }

    fun isPinned(userId: String, identityId: String): Boolean =
        userIdentities.value.any { it.userId == userId && it.identityId == identityId && it.isPinned }

    fun getWhyText(userId: String, identityId: String): String? =
        userIdentities.value.firstOrNull { it.userId == userId && it.identityId == identityId }?.whyText
}
