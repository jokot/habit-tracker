package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.domain.model.Habit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Wednesday 2026-04-22, noon UTC. */
private val TODAY = LocalDate(2026, 4, 22)
private val FIXED_NOW: Instant = TODAY.atStartOfDayIn(TimeZone.UTC) + 12.hours

class GetWidgetDataUseCaseTest {

    private val habitRepo = FakeHabitRepository()
    private val habitLogRepo = FakeHabitLogRepository()
    private val wantActivityRepo = FakeWantActivityRepository()
    private val wantLogRepo = FakeWantLogRepository()
    private val userId = "user1"

    private val clock = object : Clock {
        override fun now(): Instant = FIXED_NOW
    }

    private fun useCase() = GetWidgetDataUseCase(
        getTodayHabitsUseCase = GetTodayHabitsUseCase(habitRepo, habitLogRepo, TimeZone.UTC, clock),
        wantActivityRepository = wantActivityRepo,
        getPointBalanceUseCase = GetPointBalanceUseCase(
            habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo, TimeZone.UTC, clock,
        ),
        computeStreakUseCase = ComputeStreakUseCase(habitLogRepo, habitRepo, TimeZone.UTC, clock),
        habitRepository = habitRepo,
        habitLogRepository = habitLogRepo,
        wantLogRepository = wantLogRepo,
        timeZone = TimeZone.UTC,
        clock = clock,
    )

    private suspend fun saveHabit() = habitRepo.saveHabit(
        Habit("h1", userId, "tpl", "Read", "pages", 1.0, 5, FIXED_NOW, FIXED_NOW),
    )

    /**
     * The whole point of [GetWidgetDataUseCase.observe]: a widget collecting it repaints
     * itself when a log lands, with nothing pushed at it from outside.
     */
    @Test
    fun `observe re-emits with the new balance after a log is written`() = runTest {
        saveHabit()

        val seen = mutableListOf<WidgetData>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            useCase().observe(userId, habitSlots = Int.MAX_VALUE, wantSlots = Int.MAX_VALUE)
                .toList(seen)
        }

        assertEquals(1, seen.size)
        assertEquals(0, seen.single().balance)

        habitLogRepo.insertLog("l1", userId, "h1", 5.0, FIXED_NOW)

        assertTrue(seen.size > 1, "a log must produce another emission")
        assertEquals(5, seen.last().balance)
        assertEquals(1, seen.last().items.habits.size)
    }

    /** The streak grid rides along with the rest so it is computed once for all widgets. */
    @Test
    fun `execute fills streakDays`() = runTest {
        saveHabit()

        val data = useCase().execute(userId, habitSlots = 5, wantSlots = 5)

        assertTrue(data.streakDays.isNotEmpty())
        assertEquals(TODAY, data.streakDays.last().date)
    }
}
