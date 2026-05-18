package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncFailDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_failure_counter",
)

class SyncFailureCounter(private val context: Context) {
    private val key = intPreferencesKey("consecutive_failures")
    private val threshold = 3

    suspend fun current(): Int =
        context.syncFailDataStore.data.first()[key] ?: 0

    /** Increments the counter; returns true exactly when the threshold is crossed (3rd strike). */
    suspend fun incrementAndShouldFire(): Boolean {
        var fired = false
        context.syncFailDataStore.edit { prefs ->
            val next = (prefs[key] ?: 0) + 1
            prefs[key] = next
            if (next == threshold) fired = true
        }
        return fired
    }

    suspend fun reset() {
        context.syncFailDataStore.edit { it[key] = 0 }
    }
}
