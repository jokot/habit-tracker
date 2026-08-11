package com.habittracker.domain.model

import kotlinx.datetime.Instant

data class WantActivity(
    val id: String,
    val name: String,
    val unit: String,
    val unitsPerPoint: Int,
    val isCustom: Boolean = false,
    val createdByUserId: String? = null,
    val iconKey: String? = null,
    val hiddenAt: Instant? = null,
    val updatedAt: Instant = Instant.fromEpochMilliseconds(0),
    val syncedAt: Instant? = null,
)

/** Wants measured in this unit run on a timer instead of logging instantly. */
const val MINUTE_UNIT: String = "min"

/**
 * Whether tapping this want starts a timer rather than spending a point outright.
 * Lives next to the model so Home, the widgets and `WidgetItemSelector` read one rule.
 */
val WantActivity.isTimed: Boolean get() = unit == MINUTE_UNIT
