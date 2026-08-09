package com.habittracker.domain.usecase

import com.habittracker.data.repository.WantActivityRepository

/**
 * The single data entry point for every home-screen widget. Widgets call this once
 * from provideGlance and render the result — they never assemble domain data.
 *
 * Slot counts differ per widget and per size, so they are passed in rather than
 * baked in here.
 */
class GetWidgetDataUseCase(
    private val getTodayHabitsUseCase: GetTodayHabitsUseCase,
    private val wantActivityRepository: WantActivityRepository,
    private val getPointBalanceUseCase: GetPointBalanceUseCase,
    private val computeStreakUseCase: ComputeStreakUseCase,
) {
    suspend fun execute(userId: String, habitSlots: Int, wantSlots: Int): WidgetData {
        val habits = getTodayHabitsUseCase.execute(userId)
        val wants = wantActivityRepository.getWantActivities(userId)
        val balance = getPointBalanceUseCase.execute(userId).getOrNull()?.balance ?: 0
        val currentStreak = computeStreakUseCase.computeSummaryNow(userId).currentStreak
        val rate = ExchangeRateCalculator.rateFor(currentStreak)
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
        )
    }
}
