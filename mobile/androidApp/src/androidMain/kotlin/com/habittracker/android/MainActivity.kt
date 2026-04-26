package com.habittracker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.habittracker.android.sync.SyncTriggers
import com.habittracker.android.ui.navigation.AppNavigation
import com.habittracker.android.ui.theme.HabitTrackerTheme
import com.habittracker.data.sync.SyncReason

class MainActivity : ComponentActivity() {
    private var lastForegroundSyncAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HabitTrackerApplication).container
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
