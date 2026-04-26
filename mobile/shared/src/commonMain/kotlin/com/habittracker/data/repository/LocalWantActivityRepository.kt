package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalWantActivity
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class LocalWantActivityRepository(
    private val db: HabitTrackerDatabase,
) : WantActivityRepository {

    override fun observeWantActivities(userId: String): Flow<List<WantActivity>> =
        db.habitTrackerDatabaseQueries
            .getWantActivitiesForUser(userId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getWantActivities(userId: String): List<WantActivity> =
        db.habitTrackerDatabaseQueries
            .getWantActivitiesForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun saveWantActivity(activity: WantActivity, userId: String) {
        val updatedAt = activity.updatedAt.takeIf { it.toEpochMilliseconds() > 0L }
            ?: Clock.System.now()
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = activity.id,
            userId = userId,
            name = activity.name,
            unit = activity.unit,
            costPerUnit = activity.costPerUnit,
            isCustom = if (activity.isCustom) 1L else 0L,
            updatedAt = updatedAt.toEpochMilliseconds(),
        )
    }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        db.habitTrackerDatabaseQueries.migrateWantActivitiesUserId(newUserId, oldUserId)
    }

    override suspend fun clearForUser(userId: String) {
        db.habitTrackerDatabaseQueries.clearCustomWantActivitiesForUser(userId)
    }

    override suspend fun getUnsyncedFor(userId: String): List<WantActivity> =
        db.habitTrackerDatabaseQueries
            .getUnsyncedWantActivitiesForUser(userId)
            .executeAsList()
            .map { it.toDomain() }

    override suspend fun markSynced(id: String, syncedAt: Instant) {
        db.habitTrackerDatabaseQueries.markWantActivitySynced(syncedAt.toEpochMilliseconds(), id)
    }

    override suspend fun getByIdsForUser(userId: String, ids: List<String>): List<WantActivity> {
        if (ids.isEmpty()) return emptyList()
        return db.habitTrackerDatabaseQueries
            .getWantActivitiesByIdForUser(userId, ids)
            .executeAsList()
            .map { it.toDomain() }
    }

    override suspend fun mergePulled(row: WantActivity) {
        db.habitTrackerDatabaseQueries.mergePulledWantActivity(
            id = row.id,
            userId = row.createdByUserId,
            name = row.name,
            unit = row.unit,
            costPerUnit = row.costPerUnit,
            isCustom = if (row.isCustom) 1L else 0L,
            updatedAt = row.updatedAt.toEpochMilliseconds(),
            syncedAt = row.syncedAt?.toEpochMilliseconds(),
        )
    }
}

private fun LocalWantActivity.toDomain(): WantActivity = WantActivity(
    id = id,
    name = name,
    unit = unit,
    costPerUnit = costPerUnit,
    isCustom = isCustom == 1L,
    createdByUserId = userId,
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    syncedAt = syncedAt?.let { Instant.fromEpochMilliseconds(it) },
)
