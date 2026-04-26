package com.jktdeveloper.habitto.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jktdeveloper.habitto.HabitTrackerApplication
import com.habittracker.data.sync.SyncReason

class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HabitTrackerApplication
        val engine = app.container.syncEngine
        val reasonName = inputData.getString(KEY_REASON) ?: SyncReason.POST_LOG.name
        val reason = runCatching { SyncReason.valueOf(reasonName) }.getOrDefault(SyncReason.POST_LOG)
        return engine.sync(reason).fold(
            onSuccess = { Result.success() },
            onFailure = { err ->
                // Transient vs terminal
                if (err is IllegalStateException) Result.failure() else Result.retry()
            },
        )
    }

    companion object {
        const val KEY_REASON = "sync.reason"
    }
}
