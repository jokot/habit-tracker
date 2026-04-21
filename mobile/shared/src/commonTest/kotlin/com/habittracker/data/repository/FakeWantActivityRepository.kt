package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity

class FakeWantActivityRepository : WantActivityRepository {
    val activities = mutableListOf<WantActivity>()

    override suspend fun getWantActivities(userId: String): List<WantActivity> = activities

    override suspend fun saveWantActivity(activity: WantActivity, userId: String) {
        activities.removeAll { it.id == activity.id }
        activities.add(activity)
    }
}
