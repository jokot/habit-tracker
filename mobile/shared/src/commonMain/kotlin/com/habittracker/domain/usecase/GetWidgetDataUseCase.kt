package com.habittracker.domain.usecase

import com.habittracker.data.repository.HabitLogRepository
import com.habittracker.data.repository.HabitRepository
import com.habittracker.data.repository.WantActivityRepository
import com.habittracker.data.repository.WantLogRepository
import com.habittracker.domain.model.DateRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * The single data entry point for every home-screen widget. Widgets call this and
 * render the result — they never assemble domain data.
 *
 * Slot counts differ per widget and per size, so they are passed in rather than
 * baked in here.
 */
class GetWidgetDataUseCase(
    private val getTodayHabitsUseCase: GetTodayHabitsUseCase,
    private val wantActivityRepository: WantActivityRepository,
    private val getPointBalanceUseCase: GetPointBalanceUseCase,
    private val computeStreakUseCase: ComputeStreakUseCase,
    private val habitRepository: HabitRepository,
    private val habitLogRepository: HabitLogRepository,
    private val wantLogRepository: WantLogRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) {
    /**
     * Re-emits whenever anything a widget draws changes.
     *
     * Widgets collect this inside their composition, so a log written anywhere — the
     * widget itself, the app, the want timer — repaints them with no update pushed at
     * them from outside. The four Flows are only the change signal; [execute] stays the
     * one assembler of [WidgetData].
     */
    fun observe(userId: String, habitSlots: Int, wantSlots: Int): Flow<WidgetData> =
        combine(
            habitRepository.observeHabitsForUser(userId),
            habitLogRepository.observeAllActiveLogsForUser(userId),
            wantActivityRepository.observeWantActivities(userId),
            wantLogRepository.observeAllActiveLogsForUser(userId),
        ) { _, _, _, _ -> Unit }
            .map { execute(userId, habitSlots, wantSlots) }

    suspend fun execute(userId: String, habitSlots: Int, wantSlots: Int): WidgetData {
        val habits = getTodayHabitsUseCase.execute(userId)
        val wants = wantActivityRepository.getWantActivities(userId)
        val balance = getPointBalanceUseCase.execute(userId).getOrNull()?.balance ?: 0
        val currentStreak = computeStreakUseCase.computeSummaryNow(userId).currentStreak
        val rate = ExchangeRateCalculator.rateFor(currentStreak)
        val today = clock.now().toLocalDateTime(timeZone).date
        val streakDays = runCatching {
            computeStreakUseCase.computeNow(
                userId,
                DateRange(
                    start = today.minus(STREAK_HISTORY_DAYS - 1, DateTimeUnit.DAY),
                    endExclusive = today.plus(1, DateTimeUnit.DAY),
                ),
            ).days
        }.getOrDefault(emptyList())
        return WidgetData(
            balance = balance,
            currentStreak = currentStreak,
            items = WidgetItemSelector.select(
                habits = habits,
                wants = wants,
                balance = balance,
                rate = rate,
                habitSlots = habitSlots,
                wantSlots = wantSlots,
            ),
            streakDays = streakDays,
        )
    }

    private companion object {
        /** Ceiling the largest streak widget could ask for; smaller frames render its tail. */
        const val STREAK_HISTORY_DAYS = 120
    }
}
