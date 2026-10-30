package com.habittracker.domain.usecase

import com.habittracker.domain.model.WantTimer
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

/**
 * The point math of a want timer. Pure + stateless.
 *
 * 1 point buys [WantTimer.minutesPerPoint] minutes. A block is that number of minutes.
 * - While the timer runs, it spends 1 point when a block is complete.
 * - At the end, it spends 1 point for each block, also for the last block if it is short.
 * - At a cancel, it spends 1 point for each block that ran for 1 minute or more.
 */
object WantTimerPoints {

    fun totalMinutes(timer: WantTimer): Int = (timer.durationSec / 60).coerceAtLeast(1)

    /** Full minutes that the timer ran at [now], from 0 to [totalMinutes]. */
    fun elapsedMinutes(timer: WantTimer, now: Instant): Int =
        ((now - timer.startedAt).inWholeMinutes.toInt()).coerceIn(0, totalMinutes(timer))

    fun pointsWhileRunning(timer: WantTimer, now: Instant): Int =
        elapsedMinutes(timer, now) / blockMinutes(timer)

    fun pointsAtCancel(timer: WantTimer, now: Instant): Int =
        ceilDiv(elapsedMinutes(timer, now), blockMinutes(timer))

    fun pointsAtEnd(timer: WantTimer): Int = ceilDiv(totalMinutes(timer), blockMinutes(timer))

    /** Minutes that [points] cover, not more than the duration of the timer. */
    fun minutesFor(timer: WantTimer, points: Int): Int =
        (points * blockMinutes(timer)).coerceAtMost(totalMinutes(timer))

    /**
     * When the timer spends its next point after [now]: the end of the current block, or
     * the end of the timer. It does not use [WantTimer.pointsSpent], so a charge that
     * failed does not make the time go back.
     */
    fun nextChargeAt(timer: WantTimer, now: Instant): Instant {
        val block = blockMinutes(timer)
        val blocksDone = (now - timer.startedAt).inWholeMinutes.coerceAtLeast(0) / block
        val nextBlockEnd = timer.startedAt + ((blocksDone + 1) * block).minutes
        return minOf(nextBlockEnd, timer.endsAt)
    }

    private fun blockMinutes(timer: WantTimer): Int = timer.minutesPerPoint.coerceAtLeast(1)

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
