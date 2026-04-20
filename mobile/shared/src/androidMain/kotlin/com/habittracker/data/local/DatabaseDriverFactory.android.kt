package com.habittracker.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, "habit_tracker.db")
}
