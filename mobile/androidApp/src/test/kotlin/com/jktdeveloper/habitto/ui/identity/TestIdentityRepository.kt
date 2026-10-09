package com.jktdeveloper.habitto.ui.identity

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** A small in-memory [IdentityRepository] for the view model tests of this package. */
internal class TestIdentityRepository(
    private val seed: List<Identity> = emptyList(),
) : IdentityRepository {
    private val seedFlow = MutableStateFlow(seed)
    private val userIdentities = MutableStateFlow<List<UserIdentityRow>>(emptyList())
    private val habitIdentities = MutableStateFlow<List<HabitIdentityRow>>(emptyList())

    override suspend fun getAllIdentities(): List<Identity> = seedFlow.value
    override suspend fun upsertIdentities(identities: List<Identity>) {
        seedFlow.value = (seedFlow.value.associateBy { it.id } + identities.associateBy { it.id }).values.toList()
    }
    override fun observeUserIdentities(userId: String): Flow<List<Identity>> =
        combine(userIdentities, seedFlow) { rows, seeds ->
            val map = seeds.associateBy { it.id }
            rows.filter { it.userId == userId }.sortedBy { it.addedAt }.mapNotNull { map[it.identityId] }
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
    override suspend fun getUnsyncedUserIdentitiesFor(userId: String) =
        userIdentities.value.filter { it.userId == userId && it.syncedAt == null }
    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {
        userIdentities.value = userIdentities.value.map {
            if (it.userId == userId && it.identityId == identityId) it.copy(syncedAt = syncedAt) else it
        }
    }
    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {
        userIdentities.value = userIdentities.value.filterNot { it.userId == row.userId && it.identityId == row.identityId } + row
    }
    /** Each link that a test made: the habit id and the identity ids. */
    val links = mutableListOf<Pair<String, Set<String>>>()

    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
        links += habitId to identityIds
    }
    override suspend fun clearHabitIdentitiesForUser(userId: String) = error("unused")
    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String) = error("unused")
    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) = error("unused")
    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) = error("unused")
    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> = flowOf(emptyList())
    override suspend fun getHabitIdentityLinksForUser(userId: String): List<HabitIdentityRow> = emptyList()
    override suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean) = Unit
    override suspend fun clearPinForUser(userId: String) = Unit
    override suspend fun updateWhyText(userId: String, identityId: String, whyText: String?) = Unit
    override suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant) = Unit
    override suspend fun setPinAtomically(userId: String, identityId: String) = Unit
    override suspend fun getPinnedIdentityIdForUser(userId: String): String? = null
    override suspend fun getUserIdentityRow(userId: String, identityId: String): UserIdentityRow? = null
    override suspend fun markHabitIdentityRemoved(habitId: String, identityId: String, effectiveTo: Instant) = Unit
}
