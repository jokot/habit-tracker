package com.jktdeveloper.habitto.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_prefs",
)

data class NotificationPrefs(
    val masterEnabled: Boolean,
    private val enabledByType: Map<NotificationTypeId, Boolean>,
    private val minutesByType: Map<NotificationTypeId, Int>,
) {
    fun isEnabled(t: NotificationTypeId): Boolean =
        enabledByType[t] ?: t.defaultEnabled

    fun minutesOfDay(t: NotificationTypeId): Int? =
        minutesByType[t] ?: t.defaultMinutesOfDay

    // Back-compat accessors used by existing Phase 4 workers + tests.
    val dailyReminderEnabled: Boolean get() = isEnabled(NotificationTypeId.DAILY_REMINDER)
    val dailyReminderMinutes: Int get() = minutesOfDay(NotificationTypeId.DAILY_REMINDER) ?: (9 * 60)
    val streakRiskEnabled: Boolean get() = isEnabled(NotificationTypeId.STREAK_RISK)
    val streakRiskMinutes: Int get() = minutesOfDay(NotificationTypeId.STREAK_RISK) ?: (21 * 60)
    val streakFrozenEnabled: Boolean get() = isEnabled(NotificationTypeId.STREAK_FROZEN)
    val streakResetEnabled: Boolean get() = isEnabled(NotificationTypeId.STREAK_RESET)

    companion object {
        val DEFAULT = NotificationPrefs(
            masterEnabled = true,
            enabledByType = emptyMap(),
            minutesByType = emptyMap(),
        )
    }
}

class NotificationPreferences(private val context: Context) {

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        fun enabled(t: NotificationTypeId) = booleanPreferencesKey("type_${t.key}_enabled")
        fun minutes(t: NotificationTypeId) = intPreferencesKey("type_${t.key}_minutes")
    }

    val flow: Flow<NotificationPrefs> = context.notificationDataStore.data.map { p ->
        val enabled = NotificationTypeId.values().associateWith { t ->
            p[Keys.enabled(t)] ?: t.defaultEnabled
        }
        val minutes = NotificationTypeId.values()
            .filter { it.hasTime }
            .associateWith { t -> p[Keys.minutes(t)] ?: (t.defaultMinutesOfDay!!) }
        NotificationPrefs(
            masterEnabled = p[Keys.MASTER_ENABLED] ?: true,
            enabledByType = enabled,
            minutesByType = minutes,
        )
    }

    suspend fun current(): NotificationPrefs = flow.first()

    suspend fun setMasterEnabled(enabled: Boolean) = update { it[Keys.MASTER_ENABLED] = enabled }

    suspend fun setTypeEnabled(t: NotificationTypeId, enabled: Boolean) =
        update { it[Keys.enabled(t)] = enabled }

    suspend fun setTypeMinutesOfDay(t: NotificationTypeId, minutes: Int) {
        require(t.hasTime) { "Type ${t.key} has no time" }
        update { it[Keys.minutes(t)] = minutes.coerceIn(0, 1439) }
    }

    // Back-compat setters used by existing Phase 4 tests.
    suspend fun setDailyReminderEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.DAILY_REMINDER, enabled)
    suspend fun setDailyReminderMinutes(minutes: Int) =
        setTypeMinutesOfDay(NotificationTypeId.DAILY_REMINDER, minutes)
    suspend fun setStreakRiskEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.STREAK_RISK, enabled)
    suspend fun setStreakRiskMinutes(minutes: Int) =
        setTypeMinutesOfDay(NotificationTypeId.STREAK_RISK, minutes)
    suspend fun setStreakFrozenEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.STREAK_FROZEN, enabled)
    suspend fun setStreakResetEnabled(enabled: Boolean) =
        setTypeEnabled(NotificationTypeId.STREAK_RESET, enabled)

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.notificationDataStore.edit { block(it) }
    }
}
