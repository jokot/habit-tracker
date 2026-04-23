package com.habittracker.android.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.habittracker.data.sync.SyncReason
import java.util.concurrent.TimeUnit

object SyncTriggers {

    fun enqueue(context: Context, reason: SyncReason) {
        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(Data.Builder().putString(SyncWorker.KEY_REASON, reason.name).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(uniqueName(reason), ExistingWorkPolicy.KEEP, workRequest)
    }

    private fun uniqueName(reason: SyncReason): String = when (reason) {
        SyncReason.POST_LOG -> "sync-post-log"
        SyncReason.APP_FOREGROUND -> "sync-foreground"
        SyncReason.WIDGET_WRITE -> "sync-widget-write"
        SyncReason.MANUAL -> "sync-manual"
        SyncReason.POST_SIGN_IN -> "sync-post-sign-in"
    }
}
