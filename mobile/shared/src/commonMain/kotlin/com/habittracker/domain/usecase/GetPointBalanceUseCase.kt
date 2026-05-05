package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.PointBalance
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * @param getUserStreakOnDayUseCase Phase 6 rate-at-log-day source. When `null`, [spentOnDay]
 *   falls back to rate=1.0 (pre-Phase-6 parity). Production wiring in `AppContainer` MUST
 *   pass a non-null instance; the nullable default exists only for back-compat in tests
 *   that predate Phase 6 and don't exercise rate behavior.
 */
class GetPointBalanceUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val wantLogRepo: WantLogRepository,
    private val habitRepo: HabitRepository,
    private val wantActivityRepo: WantActivityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
    private val getUserStreakOnDayUseCase: GetUserStreakOnDayUseCase? = null,
) {
    suspend fun execute(userId: String): Result<PointBalance> = runCatching {
        val today = clock.now().toLocalDateTime(timeZone).date
        val weekStartDate = currentWeekStartDate()
        val habits = habitRepo.getHabitsForUser(userId).associateBy { it.id }
        val activities = wantActivityRepo.getWantActivities(userId).associateBy { it.id }
        val dailyEarnCap = habits.values.sumOf { it.dailyTarget }
        val rolloverCap = dailyEarnCap * 2

        val weekStartInstant = weekStartDate.atStartOfDayIn(timeZone)
        val habitLogs = habitLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStartInstant }
        val wantLogs = wantLogRepo.getAllActiveLogsForUser(userId)
            .filter { it.loggedAt >= weekStartInstant }

        var balance = 0
        var totalEarned = 0
        var totalSpent = 0
        var earnedToday = 0
        var spentToday = 0

        var day = weekStartDate
        while (day <= today) {
            val earnedThisDay = earnedOnDay(day, habitLogs, habits)
            val spentThisDay = spentOnDay(userId, day, wantLogs, activities)
            if (day != weekStartDate) balance = minOf(balance, rolloverCap)
            balance = (balance + earnedThisDay - spentThisDay).coerceAtLeast(0)
            totalEarned += earnedThisDay
            totalSpent += spentThisDay
            if (day == today) {
                earnedToday = earnedThisDay
                spentToday = spentThisDay
            }
            day = day.plus(1, DateTimeUnit.DAY)
        }

        PointBalance(
            earned = totalEarned,
            spent = totalSpent,
            balance = balance,
            earnedToday = earnedToday,
            spentToday = spentToday,
        )
    }

    private fun earnedOnDay(
        day: LocalDate,
        weekHabitLogs: List<HabitLog>,
        habits: Map<String, Habit>,
    ): Int {
        val dayStart = day.atStartOfDayIn(timeZone)
        val nextDayStart = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val byHabit = weekHabitLogs
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .groupBy { it.habitId }
        return byHabit.entries.sumOf { (habitId, logs) ->
            val habit = habits[habitId] ?: return@sumOf 0
            logs.sumOf { PointCalculator.pointsEarned(it.quantity, habit.thresholdPerPoint) }
                .coerceAtMost(habit.dailyTarget)
        }
    }

    private suspend fun spentOnDay(
        userId: String,
        day: LocalDate,
        weekWantLogs: List<WantLog>,
        activities: Map<String, WantActivity>,
    ): Int {
        val dayStart = day.atStartOfDayIn(timeZone)
        val nextDayStart = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val rate = getUserStreakOnDayUseCase?.let {
            ExchangeRateCalculator.rateFor(it.execute(userId, day))
        } ?: 1.0
        return weekWantLogs
            .filter { it.loggedAt >= dayStart && it.loggedAt < nextDayStart }
            .sumOf { log ->
                activities[log.activityId]?.let {
                    PointCalculator.pointsSpentWithRate(log.quantity, it.costPerUnit, rate)
                } ?: 0
            }
    }

    /** Local Monday 00:00 of the current week, as a LocalDate. */
    internal fun currentWeekStartDate(): LocalDate {
        val today = clock.now().toLocalDateTime(timeZone).date
        val daysFromMonday = (today.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal + 7) % 7
        return today.minus(daysFromMonday, DateTimeUnit.DAY)
    }

    /** Backwards-compatible: weekStart as Instant in local TZ. */
    internal fun currentWeekStart(): Instant =
        currentWeekStartDate().atStartOfDayIn(timeZone)
}
