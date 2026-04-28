package com.habittracker.domain.model

import kotlinx.datetime.LocalDate

enum class StreakDayState {
    /** User logged ≥ 1 active habit on this day. */
    COMPLETE,

    /** Missed day, but isolated — streak survives (one-miss tolerance). */
    FROZEN,

    /** Second consecutive miss — streak resets on this day. */
    BROKEN,

    /** Before user's first log (anchor). Never applied to today. */
    EMPTY,

    /** Today, no log yet. Not yet counted as miss. */
    TODAY_PENDING,

    /** After today — not yet reachable. */
    FUTURE,
}

data class StreakDay(
    val date: LocalDate,
    val state: StreakDayState,
    val heatLevel: Int = 0,  // 0..4 — only meaningful for renderer; state still drives streak walk
)

data class StreakRangeResult(
    val days: List<StreakDay>,
    val firstLogDate: LocalDate?,
)

data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalDaysComplete: Int,
    val firstLogDate: LocalDate?,
)

/** Half-open [start, endExclusive) range of local dates. */
data class DateRange(
    val start: LocalDate,
    val endExclusive: LocalDate,
) {
    init {
        require(start.toEpochDays() <= endExclusive.toEpochDays()) {
            "DateRange end ($endExclusive) must be ≥ start ($start)"
        }
    }
}
