package com.jktdeveloper.habitto

import android.app.Application
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.widget.WidgetRefresher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

@OptIn(DelicateCoroutinesApi::class)
class HabitTrackerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.ensureChannels(this)
        GlobalScope.launch(Dispatchers.Default) {
            runCatching { container.wantTimerRecovery.scanOnStart() }
        }
        WidgetRefresher(
            context = this,
            container = container,
            scope = GlobalScope + Dispatchers.Default,
        ).start()
    }
}
