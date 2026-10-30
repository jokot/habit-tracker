package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import com.habittracker.domain.model.isTimed
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/** What one charge of a want timer did. */
data class WantTimerCharge(
    /** The timer after the charge. Its state is not RUNNING if the charge stopped it. */
    val timer: WantTimer,
    /** Points that this charge spent. */
    val pointsCharged: Int,
    /** Minutes that the timer logged in total. */
    val minutesLogged: Int,
    /** The balance became too low for the next point, so the charge stopped the timer. */
    val outOfPoints: Boolean,
)

/**
 * Spends the points of a running want timer. See [WantTimerPoints] for the math.
 *
 * The service, the finalize worker, the start of the app and a cancel all call [charge].
 * Each call spends only the points that the timer did not spend yet, so a late call
 * catches up and a second call spends nothing. One lock for all callers prevents a
 * double charge when 2 callers run at the same time.
 */
class ChargeWantTimerUseCase(
    private val timerRepository: WantTimerRepository,
    private val wantActivityRepository: WantActivityRepository,
    private val logWantUseCase: LogWantUseCase,
    private val getPointBalanceUseCase: GetPointBalanceUseCase,
    private val clock: Clock = Clock.System,
) {
    private val mutex = Mutex()

    /**
     * Spends the points that the timer owes now. At or after `endsAt`, it finishes the
     * timer. With [cancel], it cancels the timer. Returns null if the timer is not running.
     */
    suspend fun charge(timerId: String, cancel: Boolean = false): WantTimerCharge? = mutex.withLock {
        val timer = timerRepository.getById(timerId)
        if (timer == null || timer.state != WantTimerState.RUNNING) return@withLock null
        val now = clock.now()
        val ended = now >= timer.endsAt

        val owed: Int
        val minutesAtStop: Int
        when {
            ended -> {
                owed = WantTimerPoints.pointsAtEnd(timer)
                minutesAtStop = WantTimerPoints.totalMinutes(timer)
            }
            cancel -> {
                owed = WantTimerPoints.pointsAtCancel(timer, now)
                minutesAtStop = WantTimerPoints.elapsedMinutes(timer, now)
            }
            else -> {
                owed = WantTimerPoints.pointsWhileRunning(timer, now)
                minutesAtStop = WantTimerPoints.minutesFor(timer, owed)
            }
        }

        // Only a timed want spends points. A timer for another want only stops.
        val activity = wantActivityRepository.getWantActivities(timer.userId)
            .firstOrNull { it.id == timer.activityId }
        val due = if (activity?.isTimed == true) (owed - timer.pointsSpent).coerceAtLeast(0) else 0

        var charged = 0
        var outOfPoints = false
        if (due > 0) {
            val balance = getPointBalanceUseCase.execute(timer.userId).getOrThrow().balance
            val points = due.coerceAtMost(balance.coerceAtLeast(0))
            outOfPoints = points < due
            if (points > 0) {
                val minutesBefore = WantTimerPoints.minutesFor(timer, timer.pointsSpent)
                val minutesAfter = if (points == due) {
                    minutesAtStop
                } else {
                    WantTimerPoints.minutesFor(timer, timer.pointsSpent + points)
                }
                // Record the points first. If the app stops between the 2 writes, the
                // user loses no points.
                timerRepository.addPointsSpent(timer.id, points)
                val logged = logWantUseCase.execute(
                    userId = timer.userId,
                    activityId = timer.activityId,
                    taps = points,
                    deviceMode = DeviceMode.THIS_DEVICE,
                    quantity = (minutesAfter - minutesBefore).toDouble(),
                )
                if (logged.isSuccess) {
                    charged = points
                } else {
                    timerRepository.addPointsSpent(timer.id, -points)
                    outOfPoints = logged.exceptionOrNull() is InsufficientPointsException
                }
            }
        }

        val newState = when {
            outOfPoints -> WantTimerState.CANCELLED
            ended -> WantTimerState.FINISHED
            cancel -> WantTimerState.CANCELLED
            else -> WantTimerState.RUNNING
        }
        if (newState != WantTimerState.RUNNING) timerRepository.setState(timer.id, newState)

        val spent = timer.pointsSpent + charged
        WantTimerCharge(
            timer = timer.copy(state = newState, pointsSpent = spent),
            pointsCharged = charged,
            minutesLogged = when {
                spent == 0 -> 0
                outOfPoints -> WantTimerPoints.minutesFor(timer, spent)
                newState == WantTimerState.RUNNING -> WantTimerPoints.minutesFor(timer, spent)
                else -> minutesAtStop
            },
            outOfPoints = outOfPoints,
        )
    }
}
