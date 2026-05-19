package com.jktdeveloper.habitto.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.notifications.NotificationCategory
import com.jktdeveloper.habitto.notifications.NotificationPrefs
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationsSettingsViewModel(private val container: AppContainer) : ViewModel() {

    val prefs: StateFlow<NotificationPrefs> = container.notificationPreferences.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationPrefs.DEFAULT)

    fun setMaster(enabled: Boolean) {
        viewModelScope.launch {
            container.notificationPreferences.setMasterEnabled(enabled)
            container.notificationScheduler.reschedule()
        }
    }

    fun setTypeEnabled(t: NotificationTypeId, enabled: Boolean) {
        viewModelScope.launch {
            container.notificationPreferences.setTypeEnabled(t, enabled)
            container.notificationScheduler.reschedule()
        }
    }

    fun setTypeMinutes(t: NotificationTypeId, minutes: Int) {
        viewModelScope.launch {
            container.notificationPreferences.setTypeMinutesOfDay(t, minutes)
            container.notificationScheduler.reschedule()
        }
    }

    val types: List<NotificationTypeId> = NotificationTypeId.values().toList()
    val categories: List<NotificationCategory> = NotificationCategory.values().toList()
}
