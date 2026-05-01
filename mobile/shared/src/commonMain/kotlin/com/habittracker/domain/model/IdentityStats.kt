package com.habittracker.domain.model

data class IdentityStats(
    val identityId: String,
    val currentStreak: Int,
    val daysActive: Int,
    val habitCount: Int,
    val last14Heat: List<Int>,
    val last90Heat: List<Int>,
    val last14States: List<StreakDayState>,
    val last90States: List<StreakDayState>,
) {
    init {
        require(last14Heat.size == 14) { "last14Heat must have 14 entries" }
        require(last90Heat.size == 90) { "last90Heat must have 90 entries" }
        require(last14States.size == 14) { "last14States must have 14 entries" }
        require(last90States.size == 90) { "last90States must have 90 entries" }
    }

    companion object {
        val Empty = IdentityStats(
            identityId = "",
            currentStreak = 0,
            daysActive = 0,
            habitCount = 0,
            last14Heat = List(14) { 0 },
            last90Heat = List(90) { 0 },
            last14States = List(14) { StreakDayState.EMPTY },
            last90States = List(90) { StreakDayState.EMPTY },
        )
        fun emptyFor(identityId: String) = Empty.copy(identityId = identityId)
    }
}

data class IdentityWithStats(
    val identity: Identity,
    val stats: IdentityStats,
)
