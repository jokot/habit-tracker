package com.jktdeveloper.habitto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.NotificationPrefs
import com.jktdeveloper.habitto.notifications.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val notificationPrefs: NotificationPreferences,
    private val scheduler: NotificationScheduler,
) : ViewModel() {

    val prefs: StateFlow<NotificationPrefs> = notificationPrefs.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationPrefs.DEFAULT,
    )

    fun setDailyReminderEnabled(enabled: Boolean) = update {
        notificationPrefs.setDailyReminderEnabled(enabled)
    }

    fun setDailyReminderMinutes(minutes: Int) = update {
        notificationPrefs.setDailyReminderMinutes(minutes)
    }

    fun setStreakRiskEnabled(enabled: Boolean) = update {
        notificationPrefs.setStreakRiskEnabled(enabled)
    }

    fun setStreakRiskMinutes(minutes: Int) = update {
        notificationPrefs.setStreakRiskMinutes(minutes)
    }

    fun setStreakFrozenEnabled(enabled: Boolean) = update {
        notificationPrefs.setStreakFrozenEnabled(enabled)
    }

    fun setStreakResetEnabled(enabled: Boolean) = update {
        notificationPrefs.setStreakResetEnabled(enabled)
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            scheduler.reschedule()
        }
    }
}
