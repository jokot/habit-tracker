package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

private val Context.firingDateStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_firing_dates",
)

class NotificationFiringDateStore(private val context: Context) {

    suspend fun getLastFired(eventKey: String): LocalDate? {
        val key = stringPreferencesKey(eventKey)
        val raw = context.firingDateStore.data.first()[key] ?: return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    suspend fun setLastFired(eventKey: String, date: LocalDate) {
        val key = stringPreferencesKey(eventKey)
        context.firingDateStore.edit { it[key] = date.toString() }
    }

    companion object {
        const val EVENT_FROZEN = "day_boundary_frozen"
        const val EVENT_RESET = "day_boundary_reset"
    }
}
