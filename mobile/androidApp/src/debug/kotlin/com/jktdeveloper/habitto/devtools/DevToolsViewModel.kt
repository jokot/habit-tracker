package com.jktdeveloper.habitto.devtools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class DevToolsState(
    val isLoading: Boolean = false,
    val mode: SeedMode = SeedMode.Constant,
    val days: Int = 14,
    val constantLevel: Int = 4,
    val freezeCount: Int = 0,
    val brokenCount: Int = 0,
    val seedWantSpends: Boolean = false,
    val activities: List<WantActivity> = emptyList(),
    val selectedActivityId: String? = null,
    val wantQuantity: Double = 1.0,
    val validationError: String? = null,
    val pendingConfirm: ConfirmPlan? = null,
    val toast: String? = null,
)

data class ConfirmPlan(
    val days: Int,
    val completeSlots: Int,
    val freezeSlots: Int,
    val brokenSlots: Int,
    val habitLogsToDelete: Int,
    val wantLogsToDelete: Int,
    val expectedRate: Double,
    val plan: List<DaySlot>,
)

class DevToolsViewModel(
    private val container: AppContainer,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(DevToolsState())
    val state: StateFlow<DevToolsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = container.currentUserId()
            val activities = container.wantActivityRepository.getWantActivities(userId)
            _state.value = _state.value.copy(activities = activities)
        }
    }

    fun onModeChange(mode: SeedMode) { _state.value = _state.value.copy(mode = mode) }
    fun onDaysChange(days: Int) { _state.value = _state.value.copy(days = days.coerceIn(1, 35)) }
    fun onLevelChange(level: Int) { _state.value = _state.value.copy(constantLevel = level.coerceIn(1, 4)) }
    fun onFreezeChange(count: Int) { _state.value = _state.value.copy(freezeCount = count.coerceAtLeast(0)) }
    fun onBrokenChange(count: Int) { _state.value = _state.value.copy(brokenCount = count.coerceAtLeast(0)) }
    fun onSeedWantsToggle(on: Boolean) { _state.value = _state.value.copy(seedWantSpends = on) }
    fun onActivitySelect(id: String) { _state.value = _state.value.copy(selectedActivityId = id) }
    fun onWantQuantityChange(qty: Double) { _state.value = _state.value.copy(wantQuantity = qty) }
    fun dismissConfirm() { _state.value = _state.value.copy(pendingConfirm = null) }
    fun consumeToast() { _state.value = _state.value.copy(toast = null) }

    fun onSeedClick() {
        val s = _state.value
        val error = validate(s)
        if (error != null) {
            _state.value = s.copy(validationError = error)
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(isLoading = true, validationError = null)
            val now = clock.now()
            val today = now.toLocalDateTime(timeZone).date
            val input = SeedInput(s.days, s.mode, s.constantLevel, s.freezeCount, s.brokenCount)
            val planResult = DevSeeder.plan(input, today, Random(now.toEpochMilliseconds()))
            val plan = planResult.getOrElse {
                _state.value = _state.value.copy(isLoading = false, validationError = it.message)
                return@launch
            }
            val userId = container.currentUserId()
            val windowStart = today.minus(s.days, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
            val windowEnd = today.atStartOfDayIn(timeZone)
            val habitLogsToDelete = container.habitLogRepository
                .getAllActiveLogsForUser(userId)
                .count { it.loggedAt >= windowStart && it.loggedAt < windowEnd }
            val wantLogsToDelete = if (s.seedWantSpends) {
                container.wantLogRepository.getAllActiveLogsForUser(userId)
                    .count { it.loggedAt >= windowStart && it.loggedAt < windowEnd }
            } else 0
            val completeSlots = plan.count { it.kind is DaySlotKind.Complete }
            val confirm = ConfirmPlan(
                days = s.days,
                completeSlots = completeSlots,
                freezeSlots = plan.count { it.kind == DaySlotKind.Frozen },
                brokenSlots = plan.count { it.kind == DaySlotKind.Broken },
                habitLogsToDelete = habitLogsToDelete,
                wantLogsToDelete = wantLogsToDelete,
                expectedRate = DevSeeder.expectedRateForCompleteCount(completeSlots),
                plan = plan,
            )
            _state.value = _state.value.copy(isLoading = false, pendingConfirm = confirm)
        }
    }

    fun confirmSeed() {
        val confirm = _state.value.pendingConfirm ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, pendingConfirm = null)
            try {
                runSeed(confirm)
                _state.value = _state.value.copy(
                    isLoading = false,
                    toast = "Seeded ${confirm.completeSlots} complete days. Rate: ${confirm.expectedRate}×.",
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    toast = "Seed failed: ${t.message ?: "unknown"}",
                )
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun runSeed(confirm: ConfirmPlan) = withContext(Dispatchers.IO) {
        val userId = container.currentUserId()
        val rawHabits = container.habitRepository.getHabitsForUser(userId)
        val s = _state.value

        // Backdate effectiveFrom so seeded past-day logs pass habitActiveOn.
        // Streak engine PAST-day filter: effectiveFrom < dayEnd. If habit was
        // created today, all seeded days fail this check → empty heatmap.
        val today = clock.now().toLocalDateTime(timeZone).date
        val seedWindowStart = today.minus(s.days, DateTimeUnit.DAY).atStartOfDayIn(timeZone)
        val habits = rawHabits.map { habit ->
            val current = habit.effectiveFrom
            if (current == null || current > seedWindowStart) {
                val updated = habit.copy(effectiveFrom = seedWindowStart)
                container.habitRepository.saveHabit(updated)
                updated
            } else habit
        }

        val allHabitLogs = container.habitLogRepository.getAllActiveLogsForUser(userId)
        val allWantLogs = if (s.seedWantSpends) {
            container.wantLogRepository.getAllActiveLogsForUser(userId)
        } else emptyList()

        for (slot in confirm.plan) {
            val dayStart = slot.date.atStartOfDayIn(timeZone)
            val dayEnd = slot.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(timeZone)

            val existingHabits = allHabitLogs.filter { it.loggedAt >= dayStart && it.loggedAt < dayEnd }
            for (log in existingHabits) {
                container.habitLogRepository.softDelete(log.id, userId)
            }

            if (s.seedWantSpends) {
                val existingWants = allWantLogs.filter { it.loggedAt >= dayStart && it.loggedAt < dayEnd }
                for (log in existingWants) {
                    container.wantLogRepository.softDelete(log.id, userId)
                }
            }

            val kind = slot.kind
            if (kind !is DaySlotKind.Complete) continue

            val noon = slot.date.atStartOfDayIn(timeZone).plus(12 * 3600L, DateTimeUnit.SECOND)
            for (habit in habits) {
                val qty = DevSeeder.logQuantityForLevel(
                    level = kind.level,
                    target = habit.dailyTarget,
                    threshold = habit.thresholdPerPoint,
                )
                container.habitLogRepository.insertLog(
                    id = Uuid.random().toString(),
                    userId = userId,
                    habitId = habit.id,
                    quantity = qty,
                    loggedAt = noon,
                )
            }

            if (s.seedWantSpends && s.selectedActivityId != null && s.wantQuantity > 0.0) {
                container.wantLogRepository.insertLog(
                    id = Uuid.random().toString(),
                    userId = userId,
                    activityId = s.selectedActivityId,
                    quantity = s.wantQuantity,
                    deviceMode = DeviceMode.OTHER,
                    loggedAt = noon,
                )
            }
        }
    }

    private fun validate(s: DevToolsState): String? {
        if (s.days !in 1..35) return "1–35 days"
        if (s.freezeCount + 2 * s.brokenCount >= s.days) return "Gaps fill window. Add ≥1 complete day."
        if (s.seedWantSpends) {
            if (s.activities.isEmpty()) return "No want activities exist. Create one first."
            if (s.selectedActivityId == null) return "Pick an activity."
            if (s.wantQuantity <= 0.0) return "Quantity > 0."
        }
        return null
    }
}
