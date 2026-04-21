package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity

interface WantActivityRepository {
    suspend fun getWantActivities(userId: String): List<WantActivity>
    suspend fun saveWantActivity(activity: WantActivity, userId: String)
}
