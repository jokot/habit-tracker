package com.habittracker.data.local

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

private const val DB_NAME = "habit_tracker.db"
private const val PREFS_NAME = "habittracker_db_prefs"
private const val KEY_SCHEMA_VERSION = "schema_version"

actual class DatabaseDriverFactory(private val context: Context) {
    var lastCreateWasWipe: Boolean = false
        private set

    actual fun createDriver(): SqlDriver {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVersion = prefs.getLong(KEY_SCHEMA_VERSION, 0L)
        val currentVersion = HabitTrackerDatabase.Schema.version
        lastCreateWasWipe = false
        if (storedVersion in 1L until currentVersion) {
            // Dev-only wipe: schema changed since last install; drop the DB file so
            // SQLDelight recreates it from the new schema. No production users yet.
            val dbFile = context.getDatabasePath(DB_NAME)
            if (dbFile.exists()) {
                context.deleteDatabase(DB_NAME)
                lastCreateWasWipe = true
            }
        }
        prefs.edit().putLong(KEY_SCHEMA_VERSION, currentVersion).apply()
        return AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, DB_NAME)
    }
}
