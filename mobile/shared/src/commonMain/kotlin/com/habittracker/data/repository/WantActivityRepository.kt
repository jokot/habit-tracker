package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.Flow

interface WantActivityRepository {
    fun observeWantActivities(userId: String): Flow<List<WantActivity>>
    suspend fun getWantActivities(userId: String): List<WantActivity>
    suspend fun saveWantActivity(activity: WantActivity, userId: String)
    suspend fun migrateUserId(oldUserId: String, newUserId: String)
    suspend fun clearForUser(userId: String)
}
