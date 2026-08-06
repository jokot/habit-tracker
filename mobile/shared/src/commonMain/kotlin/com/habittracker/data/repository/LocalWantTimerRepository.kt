package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalWantTimer as Row
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import kotlinx.datetime.Instant

class LocalWantTimerRepository(
    private val db: HabitTrackerDatabase,
) : WantTimerRepository {

    private val q get() = db.habitTrackerDatabaseQueries

    override suspend fun insert(timer: WantTimer) {
        q.insertWantTimer(
            id = timer.id,
            userId = timer.userId,
            activityId = timer.activityId,
            durationSec = timer.durationSec.toLong(),
            startedAt = timer.startedAt.toEpochMilliseconds(),
            endsAt = timer.endsAt.toEpochMilliseconds(),
            state = timer.state.name,
        )
    }

    override suspend fun startReplacing(timer: WantTimer) {
        q.transaction {
            val current = q.getActiveWantTimer(timer.userId).executeAsOneOrNull()
            if (current != null) {
                q.updateWantTimerState(WantTimerState.CANCELLED.name, current.id)
            }
            q.insertWantTimer(
                id = timer.id,
                userId = timer.userId,
                activityId = timer.activityId,
                durationSec = timer.durationSec.toLong(),
                startedAt = timer.startedAt.toEpochMilliseconds(),
                endsAt = timer.endsAt.toEpochMilliseconds(),
                state = timer.state.name,
            )
        }
    }

    override suspend fun getActive(userId: String): WantTimer? =
        q.getActiveWantTimer(userId).executeAsOneOrNull()?.toDomain()

    override suspend fun getById(id: String): WantTimer? =
        q.getWantTimerById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun setState(id: String, state: WantTimerState) {
        q.updateWantTimerState(state.name, id)
    }

    override suspend fun getAllRunning(): List<WantTimer> =
        q.getAllRunningTimers().executeAsList().map { it.toDomain() }

    private fun Row.toDomain(): WantTimer = WantTimer(
        id = id,
        userId = userId,
        activityId = activityId,
        durationSec = durationSec.toInt(),
        startedAt = Instant.fromEpochMilliseconds(startedAt),
        endsAt = Instant.fromEpochMilliseconds(endsAt),
        state = WantTimerState.valueOf(state),
    )
}
