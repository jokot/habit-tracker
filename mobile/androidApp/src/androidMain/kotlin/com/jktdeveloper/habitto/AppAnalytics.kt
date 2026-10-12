package com.jktdeveloper.habitto

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.habittracker.domain.usecase.LogHabitResult
import com.habittracker.domain.usecase.LogWantResult

/**
 * Crash reports and the events of the closed test. A return on day 7 needs no event: the
 * retention report of Firebase uses its own app_open and session events.
 *
 * Without google-services.json, Firebase does not start, and this class does nothing. A debug
 * build sends no crash and no event, so QA does not fill the reports.
 */
class AppAnalytics(context: Context) {
    private val analytics: FirebaseAnalytics? =
        if (FirebaseApp.getApps(context).isEmpty()) null else FirebaseAnalytics.getInstance(context)

    init {
        if (analytics != null) {
            analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        }
    }

    fun habitLogged(result: LogHabitResult) {
        analytics?.logEvent("habit_logged", Bundle().apply {
            putString("status", result.status.name.lowercase())
            putLong("points", result.pointsEarned.toLong())
        })
    }

    fun wantSpent(result: LogWantResult) {
        analytics?.logEvent("want_spent", Bundle().apply {
            putLong("points", result.pointsSpent.toLong())
            putString("device_mode", result.log.deviceMode.name.lowercase())
        })
    }
}
