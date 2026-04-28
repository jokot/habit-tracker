package com.jktdeveloper.habitto

import android.app.Application
import com.jktdeveloper.habitto.notifications.NotificationChannels

class HabitTrackerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Channels must exist before WorkManager fires any notif — even when MainActivity never launches.
        NotificationChannels.ensureChannels(this)
    }
}
