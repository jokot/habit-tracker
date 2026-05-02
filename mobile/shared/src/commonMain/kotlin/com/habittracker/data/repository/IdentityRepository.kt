package com.habittracker.data.repository

import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface IdentityRepository {
    // Seed reference (existing)
    suspend fun getAllIdentities(): List<Identity>
    suspend fun upsertIdentities(identities: List<Identity>)

    // User → identities (new)
    fun observeUserIdentities(userId: String): Flow<List<Identity>>
    suspend fun setUserIdentities(userId: String, identityIds: Set<String>)
    suspend fun clearUserIdentitiesForUser(userId: String)
    suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow>
    suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant)
    suspend fun mergePulledUserIdentity(row: UserIdentityRow)
    suspend fun setPinForIdentity(userId: String, identityId: String, isPinned: Boolean)
    suspend fun clearPinForUser(userId: String)
    suspend fun updateWhyText(userId: String, identityId: String, whyText: String?)
    suspend fun markUserIdentityRemoved(userId: String, identityId: String, removedAt: Instant)
    suspend fun setPinAtomically(userId: String, identityId: String)
    suspend fun getPinnedIdentityIdForUser(userId: String): String?

    // Habit → identities (new)
    /**
     * Adds links from a habit to the given identities. Additive: existing links are preserved.
     * To replace a habit's identity set, the caller must explicitly clear or delete first.
     */
    suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>)
    suspend fun clearHabitIdentitiesForUser(userId: String)
    suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow>
    suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant)
    suspend fun mergePulledHabitIdentity(row: HabitIdentityRow)
    fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>>
}

data class UserIdentityRow(
    val userId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
    val isPinned: Boolean = false,
    val whyText: String? = null,
    val removedAt: Instant? = null,
)

data class HabitIdentityRow(
    val habitId: String,
    val identityId: String,
    val addedAt: Instant,
    val syncedAt: Instant?,
)
