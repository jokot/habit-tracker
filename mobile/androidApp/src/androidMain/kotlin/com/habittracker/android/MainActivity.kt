package com.habittracker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.habittracker.android.ui.navigation.AppNavigation
import com.habittracker.android.ui.theme.HabitTrackerTheme

class MainActivity : ComponentActivity() {
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
}
