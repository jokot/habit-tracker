package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface WantActivityRepository {
    fun observeWantActivities(userId: String): Flow<List<WantActivity>>
    suspend fun getWantActivities(userId: String): List<WantActivity>
    suspend fun getAllWantActivitiesForUser(userId: String): List<WantActivity>
    /**
     * Upsert a [WantActivity]. Callers MUST preserve `hiddenAt` from the existing
     * row when editing — passing `hiddenAt = null` on a previously-hidden row will
     * silently un-hide it without setting the sync-dirty flag. To toggle visibility
     * use [hideWantActivity] / [unhideWantActivity] instead.
     *
     * Sync semantics: `syncedAt` is always cleared on local save (handled by SQL).
     */
    suspend fun saveWantActivity(activity: WantActivity, userId: String)
    suspend fun hideWantActivity(id: String, userId: String, hiddenAt: Instant)
    suspend fun unhideWantActivity(id: String, userId: String)
    suspend fun migrateUserId(oldUserId: String, newUserId: String)
    suspend fun clearForUser(userId: String)
    suspend fun getUnsyncedFor(userId: String): List<WantActivity>
    suspend fun markSynced(id: String, syncedAt: Instant)
    suspend fun getByIdsForUser(userId: String, ids: List<String>): List<WantActivity>
    suspend fun mergePulled(row: WantActivity)
}
