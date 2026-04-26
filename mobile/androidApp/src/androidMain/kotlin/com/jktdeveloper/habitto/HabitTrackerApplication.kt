package com.jktdeveloper.habitto

import android.app.Application

class HabitTrackerApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
