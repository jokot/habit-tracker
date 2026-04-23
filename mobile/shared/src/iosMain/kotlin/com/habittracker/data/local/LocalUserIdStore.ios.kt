package com.habittracker.data.local

import platform.Foundation.NSUserDefaults
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class LocalUserIdStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    @OptIn(ExperimentalUuidApi::class)
    actual fun getOrCreate(): String {
        val existing = defaults.stringForKey(KEY)
        if (existing != null) return existing
        val fresh = Uuid.random().toString()
        defaults.setObject(fresh, KEY)
        return fresh
    }

    private companion object {
        const val KEY = "habit_tracker.local_user_id"
    }
}
