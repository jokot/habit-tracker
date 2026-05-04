package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.IdentityStats
import com.habittracker.domain.model.StreakDayState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class ComputeIdentityStatsUseCase(
    private val habitLogRepo: HabitLogRepository,
    private val identityRepo: IdentityRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    fun observe(userId: String, identityId: String): Flow<IdentityStats> {
        val habitsFlow = identityRepo.observeHabitsForIdentity(userId, identityId)
        val logsFlow = habitLogRepo.observeAllActiveLogsForUser(userId)
        return combine(habitsFlow, logsFlow) { habits, logs -> compute(identityId, habits, logs) }
    }

    suspend fun computeNow(userId: String, identityId: String): IdentityStats {
        val habits = identityRepo.observeHabitsForIdentity(userId, identityId).first()
        val logs = habitLogRepo.getAllActiveLogsForUser(userId)
        return compute(identityId, habits, logs)
    }

    private fun compute(identityId: String, habits: List<Habit>, allLogs: List<HabitLog>): IdentityStats {
        if (habits.isEmpty()) return IdentityStats.emptyFor(identityId)
        val today = clock.now().toLocalDateTime(timeZone).date
        val habitIds = habits.map { it.id }.toSet()
        val habitsById = habits.associateBy { it.id }
        val logsForIdentity = allLogs.filter { it.habitId in habitIds }

        // Per-(day,habit) point sum (capped per habit at dailyTarget when summed for day).
        val rawByDayHabit = mutableMapOf<Pair<LocalDate, String>, Int>()
        val loggedHabitsByDay = mutableMapOf<LocalDate, MutableSet<String>>()
        logsForIdentity.forEach { log ->
            val date = log.loggedAt.toLocalDateTime(timeZone).date
            val habit = habitsById[log.habitId] ?: return@forEach
            val pts = PointCalculator.pointsEarned(log.quantity, habit.thresholdPerPoint)
            val key = date to log.habitId
            rawByDayHabit[key] = (rawByDayHabit[key] ?: 0) + pts
            loggedHabitsByDay.getOrPut(date) { mutableSetOf() }.add(log.habitId)
        }
        val pointsByDay = mutableMapOf<LocalDate, Int>()
        rawByDayHabit.forEach { (key, pts) ->
            val (date, habitId) = key
            val target = habitsById[habitId]?.dailyTarget ?: return@forEach
            pointsByDay[date] = (pointsByDay[date] ?: 0) + pts.coerceAtMost(target)
        }

        val streak = computeStreak(today, habitIds, loggedHabitsByDay)
        val daysActive = loggedHabitsByDay.keys.count { it <= today }
        val firstActivity = loggedHabitsByDay.keys.minOrNull()
        val last14 = buildHeatList(today, 14, pointsByDay, loggedHabitsByDay, habits)
        val last90 = buildHeatList(today, 90, pointsByDay, loggedHabitsByDay, habits)
        val last14States = buildStateList(today, 14, habitIds, loggedHabitsByDay, firstActivity, habits)
        val last90States = buildStateList(today, 90, habitIds, loggedHabitsByDay, firstActivity, habits)

        return IdentityStats(
            identityId = identityId,
            currentStreak = streak,
            daysActive = daysActive,
            habitCount = habits.size,
            last14Heat = last14,
            last90Heat = last90,
            last14States = last14States,
            last90States = last90States,
        )
    }

    private fun computeStreak(today: LocalDate, habitIds: Set<String>, loggedHabitsByDay: Map<LocalDate, Set<String>>): Int {
        val isComplete: (LocalDate) -> Boolean = { d ->
            val logged = loggedHabitsByDay[d].orEmpty()
            habitIds.all { it in logged }
        }
        var run = 0
        var cursor = today
        if (!isComplete(cursor)) cursor = cursor.minus(1, DateTimeUnit.DAY)
        while (isComplete(cursor)) {
            run++
            cursor = cursor.minus(1, DateTimeUnit.DAY)
        }
        return run
    }

    private fun buildHeatList(
        today: LocalDate,
        length: Int,
        pointsByDay: Map<LocalDate, Int>,
        loggedHabitsByDay: Map<LocalDate, Set<String>>,
        habits: List<Habit>,
    ): List<Int> {
        val list = ArrayList<Int>(length)
        var cursor = today.minus(length - 1, DateTimeUnit.DAY)
        while (list.size < length) {
            // 5e-1: per-day filter by Habit.effectiveFrom/effectiveTo only.
            // HabitIdentityRow.effectiveFrom/effectiveTo per-day filter is deferred —
            // users who unlink/re-link habits to identities mid-history will see a
            // minor inaccuracy in identity-scoped streak retro (rare). Add when needed.
            val dayStart = cursor.atStartOfDayIn(timeZone)
            val activeHabitsToday = activeHabitsOn(habits, dayStart)
            val bareMin = activeHabitsToday.size
            val full = activeHabitsToday.sumOf { it.dailyTarget }.coerceAtLeast(1)
            val pts = pointsByDay[cursor] ?: 0
            val logged = loggedHabitsByDay[cursor]?.count { id -> activeHabitsToday.any { it.id == id } } ?: 0
            // Mirror user-level engine (ComputeStreakUseCase line ~147): when the
            // identity has no active habits on a day, heat is 0. Without this,
            // an empty active set makes `allLogged` vacuously true and any stray
            // log paints the cell green — wrong for newly-added identities whose
            // habits aren't active until the next day under 5c-2 grace.
            val allLogged = activeHabitsToday.isNotEmpty() && logged == activeHabitsToday.size
            list += if (activeHabitsToday.isEmpty()) 0 else bucketFor(pts, allLogged, bareMin, full)
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        return list
    }

    /**
     * Per-day state walk for the per-identity heat surfaces. Mirrors ComputeStreakUseCase's
     * state machine but scoped to this identity's habits.
     *
     * - COMPLETE: all this-identity's habits earned ≥1 point that day
     * - FROZEN: PAST day where the prior day was COMPLETE but this day wasn't
     *           (one-day grace bridge — streak still alive across the gap)
     * - BROKEN: PAST day reached after 2+ consecutive non-COMPLETE days
     * - TODAY_PENDING: today, not yet COMPLETE
     * - EMPTY: pre-activity OR post-broken inactive stretch
     */
    private fun buildStateList(
        today: LocalDate,
        length: Int,
        habitIds: Set<String>,
        loggedHabitsByDay: Map<LocalDate, Set<String>>,
        firstActivity: LocalDate?,
        habits: List<Habit>,
    ): List<StreakDayState> {
        val isComplete: (LocalDate) -> Boolean = { d ->
            // 5e-1: per-day filter by Habit.effectiveFrom/effectiveTo only.
            // HabitIdentityRow.effectiveFrom/effectiveTo per-day filter is deferred —
            // users who unlink/re-link habits to identities mid-history will see a
            // minor inaccuracy in identity-scoped streak retro (rare). Add when needed.
            val dayStart = d.atStartOfDayIn(timeZone)
            val activeIds = activeHabitsOn(habits, dayStart).map { it.id }.toSet()
            activeIds.isNotEmpty() && activeIds.all { it in loggedHabitsByDay[d].orEmpty() }
        }
        // Walk from firstActivity (or rangeStart) up to today to know the carrying state
        // when rangeStart is reached. Then emit only the requested window.
        val rangeStart = today.minus(length - 1, DateTimeUnit.DAY)
        val anchor = firstActivity ?: rangeStart
        val walkStart = if (anchor < rangeStart) anchor else rangeStart
        val perDay = mutableMapOf<LocalDate, StreakDayState>()
        var prev: StreakDayState? = null
        var cursor = walkStart
        while (cursor <= today) {
            val state = when {
                cursor < anchor -> StreakDayState.EMPTY
                isComplete(cursor) -> StreakDayState.COMPLETE
                cursor == today -> StreakDayState.TODAY_PENDING
                prev == StreakDayState.COMPLETE -> StreakDayState.FROZEN
                prev == StreakDayState.FROZEN -> StreakDayState.BROKEN
                prev == StreakDayState.BROKEN -> StreakDayState.BROKEN
                prev == StreakDayState.TODAY_PENDING -> StreakDayState.FROZEN
                else -> StreakDayState.EMPTY
            }
            perDay[cursor] = state
            prev = state
            cursor = cursor.plus(1, DateTimeUnit.DAY)
        }
        val list = ArrayList<StreakDayState>(length)
        var d = rangeStart
        while (list.size < length) {
            list += perDay[d] ?: StreakDayState.EMPTY
            d = d.plus(1, DateTimeUnit.DAY)
        }
        return list
    }

    /**
     * Conditional grace per day for identity engine.
     *
     * - PAST days: date-overlap (effectiveFrom < dayEnd). Past mid-day creations
     *   count active for their creation day so historical heat/streak is correct.
     *
     * - TODAY: use a "settled-baseline" subset to absorb mid-day adds:
     *   - Find the newest habit's effectiveFrom.
     *   - Habits strictly older than that newest are the "settled" subset.
     *   - If settled is non-empty, today's COMPLETE check uses ONLY the settled
     *     subset. The just-added newest habit is excluded for today, so adding
     *     a habit to an identity that already had heat doesn't reset it.
     *   - If settled is empty (all habits share the same effectiveFrom — fresh
     *     identity batch or single-habit), fall back to date-overlap so logging
     *     the whole batch paints today green.
     *
     * Stable wrt clock advancement: the rule depends on relative effectiveFroms,
     * not on time-since-now, so the heat doesn't "decay" minutes after a change.
     */
    private fun activeHabitsOn(habits: List<Habit>, dayStart: Instant): List<Habit> {
        val today = clock.now().toLocalDateTime(timeZone).date
        val dayDate = dayStart.toLocalDateTime(timeZone).date
        val dayEnd = dayStart.plus(1, DateTimeUnit.DAY, timeZone)
        val endOk = { h: Habit -> h.effectiveTo?.let { it > dayStart } ?: true }
        val dateOverlap = { h: Habit ->
            (h.effectiveFrom?.let { it < dayEnd } ?: true) && endOk(h)
        }
        if (dayDate != today) {
            return habits.filter(dateOverlap)
        }
        val newestEff = habits.mapNotNull { it.effectiveFrom }.maxOrNull()
        val settled = if (newestEff != null) {
            habits.filter { h ->
                (h.effectiveFrom == null || h.effectiveFrom!! < newestEff) && endOk(h)
            }
        } else emptyList()
        return if (settled.isNotEmpty()) settled else habits.filter(dateOverlap)
    }

    /**
     * Heat bucket anchored at domain concepts:
     * - 0: no logs OR partial day
     * - 1: bare minimum (all habits earned ≥1)
     * - 2-3: thirds between bare min and full target
     * - 4: full target met
     */
    private fun bucketFor(pointsCapped: Int, allLogged: Boolean, bareMin: Int, full: Int): Int {
        if (pointsCapped == 0 || !allLogged) return 0
        val span = (full - bareMin).coerceAtLeast(0)
        val third = span / 3
        val mid1 = bareMin + third
        val mid2 = bareMin + 2 * third
        return when {
            pointsCapped < mid1 -> 1
            pointsCapped < mid2 -> 2
            pointsCapped < full -> 3
            else -> 4
        }
    }
}
