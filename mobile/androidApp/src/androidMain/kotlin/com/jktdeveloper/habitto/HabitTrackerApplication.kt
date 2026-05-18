package com.jktdeveloper.habitto

import android.app.Application
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.timer.WantTimerRecovery
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@OptIn(DelicateCoroutinesApi::class)
class HabitTrackerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureChannels(this)
        val recovery = WantTimerRecovery(
            context = this,
            timerRepo = container.wantTimerRepository,
            wantActivityRepo = container.wantActivityRepository,
            logWantUseCase = container.logWantUseCase,
            notificationPreferences = container.notificationPreferences,
        )
        GlobalScope.launch(Dispatchers.Default) {
            runCatching { recovery.scanOnStart() }
        }
    }
}
