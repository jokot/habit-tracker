package com.jktdeveloper.habitto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.jktdeveloper.habitto.sync.SyncTriggers
import com.jktdeveloper.habitto.ui.navigation.AppNavigation
import com.jktdeveloper.habitto.ui.theme.HabitTrackerTheme
import com.habittracker.data.sync.SyncReason
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var lastForegroundSyncAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HabitTrackerApplication).container
        container.notificationScheduler.ensureChannels()
        lifecycleScope.launch { container.notificationScheduler.reschedule() }
        setContent {
            HabitTrackerTheme {
                AppNavigation(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (now - lastForegroundSyncAt > 5_000L) {
            lastForegroundSyncAt = now
            SyncTriggers.enqueue(this, SyncReason.APP_FOREGROUND)
        }
    }
}
