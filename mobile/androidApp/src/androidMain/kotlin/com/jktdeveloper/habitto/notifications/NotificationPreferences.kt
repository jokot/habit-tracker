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
    val dailyReminderEnabled: Boolean,
    val dailyReminderMinutes: Int,
    val streakRiskEnabled: Boolean,
    val streakRiskMinutes: Int,
    val streakFrozenEnabled: Boolean,
    val streakResetEnabled: Boolean,
) {
    companion object {
        val DEFAULT = NotificationPrefs(
            dailyReminderEnabled = true,
            dailyReminderMinutes = 9 * 60,   // 09:00
            streakRiskEnabled = true,
            streakRiskMinutes = 21 * 60,     // 21:00
            streakFrozenEnabled = true,
            streakResetEnabled = true,
        )
    }
}

class NotificationPreferences(private val context: Context) {

    private object Keys {
        val DAILY_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val DAILY_MINUTES = intPreferencesKey("daily_reminder_minutes")
        val RISK_ENABLED = booleanPreferencesKey("streak_risk_enabled")
        val RISK_MINUTES = intPreferencesKey("streak_risk_minutes")
        val FROZEN_ENABLED = booleanPreferencesKey("streak_frozen_enabled")
        val RESET_ENABLED = booleanPreferencesKey("streak_reset_enabled")
    }

    val flow: Flow<NotificationPrefs> = context.notificationDataStore.data.map { p ->
        val d = NotificationPrefs.DEFAULT
        NotificationPrefs(
            dailyReminderEnabled = p[Keys.DAILY_ENABLED] ?: d.dailyReminderEnabled,
            dailyReminderMinutes = p[Keys.DAILY_MINUTES] ?: d.dailyReminderMinutes,
            streakRiskEnabled = p[Keys.RISK_ENABLED] ?: d.streakRiskEnabled,
            streakRiskMinutes = p[Keys.RISK_MINUTES] ?: d.streakRiskMinutes,
            streakFrozenEnabled = p[Keys.FROZEN_ENABLED] ?: d.streakFrozenEnabled,
            streakResetEnabled = p[Keys.RESET_ENABLED] ?: d.streakResetEnabled,
        )
    }

    suspend fun current(): NotificationPrefs = flow.first()

    suspend fun setDailyReminderEnabled(enabled: Boolean) = update { it[Keys.DAILY_ENABLED] = enabled }
    suspend fun setDailyReminderMinutes(minutes: Int) = update { it[Keys.DAILY_MINUTES] = minutes.coerceIn(0, 1439) }
    suspend fun setStreakRiskEnabled(enabled: Boolean) = update { it[Keys.RISK_ENABLED] = enabled }
    suspend fun setStreakRiskMinutes(minutes: Int) = update { it[Keys.RISK_MINUTES] = minutes.coerceIn(0, 1439) }
    suspend fun setStreakFrozenEnabled(enabled: Boolean) = update { it[Keys.FROZEN_ENABLED] = enabled }
    suspend fun setStreakResetEnabled(enabled: Boolean) = update { it[Keys.RESET_ENABLED] = enabled }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.notificationDataStore.edit { block(it) }
    }
}
