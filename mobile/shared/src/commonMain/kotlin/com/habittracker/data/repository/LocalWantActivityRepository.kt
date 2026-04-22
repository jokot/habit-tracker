package com.habittracker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalWantActivity
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        db.habitTrackerDatabaseQueries.upsertWantActivity(
            id = activity.id,
            userId = if (activity.isCustom) userId else null,
            name = activity.name,
            unit = activity.unit,
            costPerUnit = activity.costPerUnit,
            isCustom = if (activity.isCustom) 1L else 0L,
        )
    }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        db.habitTrackerDatabaseQueries.migrateWantActivitiesUserId(newUserId, oldUserId)
    }

    override suspend fun clearForUser(userId: String) {
        db.habitTrackerDatabaseQueries.clearCustomWantActivitiesForUser(userId)
    }
}

private fun LocalWantActivity.toDomain(): WantActivity = WantActivity(
    id = id,
    name = name,
    unit = unit,
    costPerUnit = costPerUnit,
    isCustom = isCustom == 1L,
    createdByUserId = userId,
)
