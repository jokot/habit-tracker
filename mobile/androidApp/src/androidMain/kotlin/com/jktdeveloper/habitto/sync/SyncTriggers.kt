package com.jktdeveloper.habitto.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.habittracker.data.sync.SyncReason
import java.util.concurrent.TimeUnit

object SyncTriggers {

    fun enqueue(context: Context, reason: SyncReason) {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(Data.Builder().putString(SyncWorker.KEY_REASON, reason.name).build())
            .setConstraints(constraints(reason))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueName(reason), ExistingWorkPolicy.KEEP, workRequest)
    }

    /**
     * Offline, a job that runs only fails, counts as a sync failure, and waits for its backoff.
     * So a background job waits for the network, and WorkManager starts it on reconnect (#36).
     * A manual sync runs at once, so the user sees "No network" instead of nothing.
     */
    private fun constraints(reason: SyncReason): Constraints {
        val network = if (reason == SyncReason.MANUAL) NetworkType.NOT_REQUIRED else NetworkType.CONNECTED
        return Constraints.Builder().setRequiredNetworkType(network).build()
    }

    internal fun uniqueName(reason: SyncReason): String = when (reason) {
        SyncReason.POST_LOG -> "sync-post-log"
        SyncReason.APP_FOREGROUND -> "sync-foreground"
        SyncReason.WIDGET_WRITE -> "sync-widget-write"
        SyncReason.MANUAL -> "sync-manual"
        SyncReason.POST_SIGN_IN -> "sync-post-sign-in"
    }
}

/**
 * Pushes a widget log, so other devices show it without a visit to the app (#34). A guest has
 * nothing to push. The call only puts a job in the queue, so the next tap does not wait.
 */
internal fun syncAfterWidgetLog(context: Context, logged: Boolean, signedIn: Boolean) {
    if (logged && signedIn) SyncTriggers.enqueue(context, SyncReason.WIDGET_WRITE)
}
