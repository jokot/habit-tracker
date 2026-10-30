package com.habittracker.domain.usecase

import com.habittracker.data.repository.FakeHabitLogRepository
import com.habittracker.data.repository.FakeHabitRepository
import com.habittracker.data.repository.FakeWantActivityRepository
import com.habittracker.data.repository.FakeWantLogRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantTimer
import com.habittracker.domain.model.WantTimerState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChargeWantTimerUseCaseTest {
    private val tz = TimeZone.UTC
    private val userId = "u1"
    private val start = LocalDateTime(LocalDate(2026, 5, 5), LocalTime(12, 0)).toInstant(tz)

    @Test
    fun `5 min per point with a 5 min timer spends 1 point at the end`() = runTest {
        val sut = makeSut(unitsPerPoint = 5, balance = 100)
        sut.startTimer(durationMin = 5, minutesPerPoint = 5)

        sut.at(5.minutes)
        val charge = sut.useCase.charge("t1")!!

        assertEquals(WantTimerState.FINISHED, charge.timer.state)
        assertEquals(1, charge.timer.pointsSpent)
        assertEquals(5, charge.minutesLogged)
        assertEquals(listOf(5.0), sut.wantLogRepo.logs.map { it.quantity })
        assertEquals(listOf(1), sut.wantLogRepo.logs.map { it.pointsSpent })
    }

    @Test
    fun `1 min per point spends 1 point at the end of each minute`() = runTest {
        val sut = makeSut(unitsPerPoint = 1, balance = 100)
        sut.startTimer(durationMin = 5, minutesPerPoint = 1)

        sut.at(59.seconds)
        assertEquals(0, sut.useCase.charge("t1")!!.pointsCharged)

        sut.at(1.minutes + 1.seconds)
        val first = sut.useCase.charge("t1")!!
        assertEquals(1, first.pointsCharged)
        assertEquals(WantTimerState.RUNNING, first.timer.state)
        assertEquals(WantTimerState.RUNNING, sut.timerRepo.getById("t1")?.state)

        // A late tick catches up. A second tick at the same time spends nothing.
        sut.at(3.minutes + 1.seconds)
        assertEquals(2, sut.useCase.charge("t1")!!.pointsCharged)
        assertEquals(0, sut.useCase.charge("t1")!!.pointsCharged)
        assertEquals(3, sut.timerRepo.getById("t1")?.pointsSpent)

        sut.at(5.minutes)
        val end = sut.useCase.charge("t1")!!
        assertEquals(WantTimerState.FINISHED, end.timer.state)
        assertEquals(5, end.timer.pointsSpent)
        assertEquals(5.0, sut.wantLogRepo.logs.sumOf { it.quantity })
        assertEquals(5, sut.wantLogRepo.logs.sumOf { it.pointsSpent })
    }

    @Test
    fun `a short last block costs 1 point at the end`() = runTest {
        val sut = makeSut(unitsPerPoint = 15, balance = 100)
        sut.startTimer(durationMin = 20, minutesPerPoint = 15)

        sut.at(15.minutes + 1.seconds)
        assertEquals(1, sut.useCase.charge("t1")!!.pointsCharged)

        sut.at(20.minutes)
        val end = sut.useCase.charge("t1")!!
        assertEquals(2, end.timer.pointsSpent)
        assertEquals(20, end.minutesLogged)
        assertEquals(listOf(15.0, 5.0), sut.wantLogRepo.logs.map { it.quantity })
    }

    @Test
    fun `cancel spends 1 point for each block that ran for 1 minute or more`() = runTest {
        val sut = makeSut(unitsPerPoint = 5, balance = 100)
        sut.startTimer(durationMin = 10, minutesPerPoint = 5)

        sut.at(5.minutes + 1.seconds)
        assertEquals(1, sut.useCase.charge("t1")!!.pointsCharged)

        sut.at(6.minutes + 10.seconds)
        val cancel = sut.useCase.charge("t1", cancel = true)!!
        assertEquals(WantTimerState.CANCELLED, cancel.timer.state)
        assertEquals(2, cancel.timer.pointsSpent)
        assertEquals(6, cancel.minutesLogged)
        assertEquals(listOf(5.0, 1.0), sut.wantLogRepo.logs.map { it.quantity })
    }

    @Test
    fun `cancel before 1 minute spends nothing`() = runTest {
        val sut = makeSut(unitsPerPoint = 5, balance = 100)
        sut.startTimer(durationMin = 10, minutesPerPoint = 5)

        sut.at(30.seconds)
        val cancel = sut.useCase.charge("t1", cancel = true)!!
        assertEquals(WantTimerState.CANCELLED, cancel.timer.state)
        assertEquals(0, cancel.timer.pointsSpent)
        assertTrue(sut.wantLogRepo.logs.isEmpty())
    }

    @Test
    fun `a balance that is too low stops the timer after the last point`() = runTest {
        val sut = makeSut(unitsPerPoint = 1, balance = 2)
        sut.startTimer(durationMin = 5, minutesPerPoint = 1)

        sut.at(4.minutes + 1.seconds)
        val charge = sut.useCase.charge("t1")!!
        assertTrue(charge.outOfPoints)
        assertEquals(WantTimerState.CANCELLED, charge.timer.state)
        assertEquals(WantTimerState.CANCELLED, sut.timerRepo.getById("t1")?.state)
        assertEquals(2, charge.timer.pointsSpent)
        assertEquals(2, charge.minutesLogged)
        assertNull(sut.useCase.charge("t1"))
    }

    @Test
    fun `a timer for a want that is not timed stops and spends nothing`() = runTest {
        val sut = makeSut(unitsPerPoint = 1, balance = 100, unit = "cup")
        sut.startTimer(durationMin = 5, minutesPerPoint = 1)

        sut.at(5.minutes)
        val charge = sut.useCase.charge("t1")!!
        assertEquals(WantTimerState.FINISHED, charge.timer.state)
        assertEquals(0, charge.timer.pointsSpent)
        assertFalse(charge.outOfPoints)
        assertTrue(sut.wantLogRepo.logs.isEmpty())
    }

    @Test
    fun `nextChargeAt is the end of the current block, and not after the end`() {
        val timer = WantTimer(
            id = "t", userId = userId, activityId = "w",
            durationSec = 20 * 60, startedAt = start, endsAt = start + 20.minutes,
            state = WantTimerState.RUNNING, minutesPerPoint = 15,
        )
        assertEquals(start + 15.minutes, WantTimerPoints.nextChargeAt(timer, start))
        assertEquals(start + 15.minutes, WantTimerPoints.nextChargeAt(timer, start + 14.minutes))
        assertEquals(start + 20.minutes, WantTimerPoints.nextChargeAt(timer, start + 15.minutes))
    }

    // ── helpers ─────────────────────────────────────────────────────

    private class Sut(
        val useCase: ChargeWantTimerUseCase,
        val timerRepo: InMemoryWantTimerRepository,
        val wantLogRepo: FakeWantLogRepository,
        private val clock: MutableClock,
        private val start: Instant,
        private val userId: String,
    ) {
        fun at(offset: Duration) { clock.now = start + offset }

        suspend fun startTimer(durationMin: Int, minutesPerPoint: Int) {
            timerRepo.insert(
                WantTimer(
                    id = "t1", userId = userId, activityId = "w",
                    durationSec = durationMin * 60,
                    startedAt = start, endsAt = start + durationMin.minutes,
                    state = WantTimerState.RUNNING,
                    minutesPerPoint = minutesPerPoint,
                ),
            )
        }
    }

    private class MutableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    private suspend fun makeSut(unitsPerPoint: Int, balance: Int, unit: String = "min"): Sut {
        val habitRepo = FakeHabitRepository()
        val habitLogRepo = FakeHabitLogRepository()
        val wantActivityRepo = FakeWantActivityRepository()
        val wantLogRepo = FakeWantLogRepository()
        val timerRepo = InMemoryWantTimerRepository()
        val clock = MutableClock(start)

        wantActivityRepo.activities.add(
            WantActivity(id = "w", name = "Scroll", unit = unit, unitsPerPoint = unitsPerPoint),
        )
        // One habit that earns [balance] points today.
        val dayStart = LocalDateTime(LocalDate(2026, 5, 5), LocalTime(0, 0)).toInstant(tz)
        habitRepo.saveHabit(
            Habit(
                id = "earnH", userId = userId, templateId = null, name = "earn", unit = "x",
                thresholdPerPoint = 1.0, dailyTarget = balance,
                createdAt = dayStart, updatedAt = dayStart, effectiveFrom = dayStart,
            ),
        )
        habitLogRepo.insertLog(
            id = "earn-log", userId = userId, habitId = "earnH",
            quantity = balance.toDouble(), loggedAt = dayStart + 1.minutes,
        )

        val getBalance = GetPointBalanceUseCase(habitLogRepo, wantLogRepo, habitRepo, wantActivityRepo, tz, clock)
        val streakOnDay = GetUserStreakOnDayUseCase(ComputeStreakUseCase(habitLogRepo, habitRepo, tz, clock), tz)
        val logWant = LogWantUseCase(
            wantLogRepository = wantLogRepo,
            wantActivityRepository = wantActivityRepo,
            getPointBalanceUseCase = getBalance,
            getUserStreakOnDayUseCase = streakOnDay,
            clock = clock,
            timeZone = tz,
        )
        val useCase = ChargeWantTimerUseCase(timerRepo, wantActivityRepo, logWant, getBalance, clock)
        return Sut(useCase, timerRepo, wantLogRepo, clock, start, userId)
    }

    private class InMemoryWantTimerRepository : WantTimerRepository {
        private val rows = mutableMapOf<String, WantTimer>()
        override suspend fun insert(timer: WantTimer) { rows[timer.id] = timer }
        override suspend fun startReplacing(timer: WantTimer) {
            rows.values.filter { it.userId == timer.userId && it.state == WantTimerState.RUNNING }
                .forEach { rows[it.id] = it.copy(state = WantTimerState.CANCELLED) }
            rows[timer.id] = timer
        }
        override suspend fun getActive(userId: String): WantTimer? =
            rows.values.firstOrNull { it.userId == userId && it.state == WantTimerState.RUNNING }
        override suspend fun getById(id: String): WantTimer? = rows[id]
        override suspend fun setState(id: String, state: WantTimerState) {
            rows[id]?.let { rows[id] = it.copy(state = state) }
        }
        override suspend fun addPointsSpent(id: String, points: Int) {
            rows[id]?.let { rows[id] = it.copy(pointsSpent = it.pointsSpent + points) }
        }
        override suspend fun getAllRunning(): List<WantTimer> =
            rows.values.filter { it.state == WantTimerState.RUNNING }
    }
}
