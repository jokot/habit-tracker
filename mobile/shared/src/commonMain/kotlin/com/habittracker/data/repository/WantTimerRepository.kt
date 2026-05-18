package com.habittracker.data.repository

import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState

interface WantTimerRepository {
    suspend fun insert(timer: WantTimer)
    /** Atomically cancels any RUNNING row for the user, then inserts [timer] as RUNNING. */
    suspend fun startReplacing(timer: WantTimer)
    suspend fun getActive(userId: String): WantTimer?
    suspend fun getById(id: String): WantTimer?
    suspend fun setState(id: String, state: WantTimerState)
    suspend fun getAllRunning(): List<WantTimer>
}
