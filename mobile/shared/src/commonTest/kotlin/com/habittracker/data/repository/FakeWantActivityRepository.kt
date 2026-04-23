package com.habittracker.data.repository

import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeWantActivityRepository : WantActivityRepository {
    private val _activities = MutableStateFlow<List<WantActivity>>(emptyList())

    /**
     * Mutable-list view over the backing state flow. Mutations (e.g. `activities.add(...)`)
     * update the flow so existing tests that populate via `.add(...)` still work.
     */
    val activities: MutableList<WantActivity> = object : AbstractMutableList<WantActivity>() {
        override val size: Int get() = _activities.value.size
        override fun get(index: Int): WantActivity = _activities.value[index]
        override fun add(index: Int, element: WantActivity) {
            _activities.value = _activities.value.toMutableList().also { it.add(index, element) }
        }
        override fun removeAt(index: Int): WantActivity {
            val updated = _activities.value.toMutableList()
            val removed = updated.removeAt(index)
            _activities.value = updated
            return removed
        }
        override fun set(index: Int, element: WantActivity): WantActivity {
            val updated = _activities.value.toMutableList()
            val prev = updated.set(index, element)
            _activities.value = updated
            return prev
        }
    }

    override fun observeWantActivities(userId: String): Flow<List<WantActivity>> =
        _activities.map { it }

    override suspend fun getWantActivities(userId: String): List<WantActivity> =
        _activities.value

    override suspend fun saveWantActivity(activity: WantActivity, userId: String) {
        _activities.value = _activities.value.filterNot { it.id == activity.id } + activity
    }

    override suspend fun migrateUserId(oldUserId: String, newUserId: String) {
        _activities.value = _activities.value.map {
            if (it.createdByUserId == oldUserId) it.copy(createdByUserId = newUserId) else it
        }
    }

    override suspend fun clearForUser(userId: String) {
        _activities.value = _activities.value.filterNot { it.createdByUserId == userId }
    }
}
