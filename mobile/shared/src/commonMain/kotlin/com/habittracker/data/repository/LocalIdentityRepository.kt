package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalHabit
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalIdentityRepository(
    private val db: HabitTrackerDatabase,
) : IdentityRepository {

    private val q get() = db.habitTrackerDatabaseQueries

    // ── seed ─────────────────────────────────────────────────────────────

    override suspend fun getAllIdentities(): List<Identity> =
        q.getAllIdentities().executeAsList().map {
            Identity(id = it.id, name = it.name, description = it.description, icon = it.icon)
        }

    override suspend fun upsertIdentities(identities: List<Identity>) {
        q.transaction {
            identities.forEach {
                q.upsertIdentity(id = it.id, name = it.name, description = it.description, icon = it.icon)
            }
        }
    }

    // ── user identities ──────────────────────────────────────────────────

    override fun observeUserIdentities(userId: String): Flow<List<Identity>> {
        val userIdsFlow = q.getUserIdentities(userId).asFlow().mapToList(Dispatchers.Default)
        val seedFlow = q.getAllIdentities().asFlow().mapToList(Dispatchers.Default)
        return combine(userIdsFlow, seedFlow) { userIds, seeds ->
            val seedMap = seeds.associate { it.id to Identity(it.id, it.name, it.description, it.icon) }
            userIds.mapNotNull { seedMap[it.identityId] }
        }
    }

    override suspend fun setUserIdentities(userId: String, identityIds: Set<String>) {
        val now = Clock.System.now().toEpochMilliseconds()
        q.transaction {
            val existing = q.getUserIdentities(userId).executeAsList().map { it.identityId }.toSet()
            val toAdd = identityIds - existing
            val toRemove = existing - identityIds
            toRemove.forEach { q.deleteUserIdentity(userId = userId, identityId = it) }
            toAdd.forEach { q.upsertUserIdentity(userId = userId, identityId = it, addedAt = now) }
        }
    }

    override suspend fun clearUserIdentitiesForUser(userId: String) {
        q.deleteAllUserIdentitiesForUser(userId)
    }

    override suspend fun getUnsyncedUserIdentitiesFor(userId: String): List<UserIdentityRow> =
        q.getUnsyncedUserIdentitiesForUser(userId).executeAsList().map {
            UserIdentityRow(
                userId = it.userId,
                identityId = it.identityId,
                addedAt = Instant.fromEpochMilliseconds(it.addedAt),
                syncedAt = it.syncedAt?.let(Instant::fromEpochMilliseconds),
            )
        }

    override suspend fun markUserIdentitySynced(userId: String, identityId: String, syncedAt: Instant) {
        q.markUserIdentitySynced(syncedAt.toEpochMilliseconds(), userId, identityId)
    }

    override suspend fun mergePulledUserIdentity(row: UserIdentityRow) {
        q.mergePulledUserIdentity(
            userId = row.userId,
            identityId = row.identityId,
            addedAt = row.addedAt.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }

    // ── habit identities ─────────────────────────────────────────────────

    override suspend fun linkHabitToIdentities(habitId: String, identityIds: Set<String>) {
        val now = Clock.System.now().toEpochMilliseconds()
        identityIds.forEach { q.upsertHabitIdentity(habitId = habitId, identityId = it, addedAt = now) }
    }

    override suspend fun clearHabitIdentitiesForUser(userId: String) {
        q.clearHabitIdentitiesForUser(userId)
    }

    override suspend fun getUnsyncedHabitIdentitiesFor(userId: String): List<HabitIdentityRow> =
        q.getUnsyncedHabitIdentitiesForUser(userId).executeAsList().map {
            HabitIdentityRow(
                habitId = it.habitId,
                identityId = it.identityId,
                addedAt = Instant.fromEpochMilliseconds(it.addedAt),
                syncedAt = it.syncedAt?.let(Instant::fromEpochMilliseconds),
            )
        }

    override suspend fun markHabitIdentitySynced(habitId: String, identityId: String, syncedAt: Instant) {
        q.markHabitIdentitySynced(syncedAt.toEpochMilliseconds(), habitId, identityId)
    }

    override suspend fun mergePulledHabitIdentity(row: HabitIdentityRow) {
        q.mergePulledHabitIdentity(
            habitId = row.habitId,
            identityId = row.identityId,
            addedAt = row.addedAt.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }

    override fun observeHabitsForIdentity(userId: String, identityId: String): Flow<List<Habit>> =
        q.getHabitsForIdentity(userId, identityId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }
}

private fun LocalHabit.toDomain(): Habit = Habit(
    id = id,
    userId = userId,
    templateId = templateId,
    name = name,
    unit = unit,
    thresholdPerPoint = thresholdPerPoint,
    dailyTarget = dailyTarget.toInt(),
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
