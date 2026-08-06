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
        const val EVENT_TIER_ADVANCED = "tier_advanced"
        const val EVENT_MILESTONE_7 = "milestone_streak_7"
        const val EVENT_MILESTONE_30 = "milestone_streak_30"
        const val EVENT_MILESTONE_100 = "milestone_streak_100"
        const val EVENT_MILESTONE_365 = "milestone_streak_365"
        const val EVENT_CLOUD_RESTORE = "cloud_restore_complete"

        /** Per-identity event key for daily_reminder_per_identity. */
        fun perIdentityKey(identityId: String) = "per_identity_$identityId"
    }
}
