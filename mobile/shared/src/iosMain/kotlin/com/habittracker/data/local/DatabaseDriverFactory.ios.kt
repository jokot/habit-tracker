package com.habittracker.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(HabitTrackerDatabase.Schema, "habit_tracker.db")
}
