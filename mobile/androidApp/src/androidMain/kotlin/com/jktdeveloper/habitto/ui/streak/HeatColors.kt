package com.jktdeveloper.habitto.ui.streak

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.ui.theme.HeatCellBorder
import com.jktdeveloper.habitto.ui.theme.HeatCellBorderDark
import com.jktdeveloper.habitto.ui.theme.HeatL0
import com.jktdeveloper.habitto.ui.theme.HeatL0Dark
import com.jktdeveloper.habitto.ui.theme.HeatL1
import com.jktdeveloper.habitto.ui.theme.HeatL1Dark
import com.jktdeveloper.habitto.ui.theme.HeatL2
import com.jktdeveloper.habitto.ui.theme.HeatL2Dark
import com.jktdeveloper.habitto.ui.theme.HeatL3
import com.jktdeveloper.habitto.ui.theme.HeatL3Dark
import com.jktdeveloper.habitto.ui.theme.HeatL4
import com.jktdeveloper.habitto.ui.theme.HeatL4Dark
import com.jktdeveloper.habitto.ui.theme.StreakBroken
import com.jktdeveloper.habitto.ui.theme.StreakBrokenDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozen
import com.jktdeveloper.habitto.ui.theme.StreakFrozenDark

data class CellAppearance(
    val fill: Color,
    val ringColor: Color?,
    val ringWidth: Dp,
    val alpha: Float,
    val border: Color,
)

@Composable
fun resolveCellAppearance(day: StreakDay, isDark: Boolean): CellAppearance {
    val heatColors = if (isDark) {
        listOf(HeatL0Dark, HeatL1Dark, HeatL2Dark, HeatL3Dark, HeatL4Dark)
    } else {
        listOf(HeatL0, HeatL1, HeatL2, HeatL3, HeatL4)
    }
    val border = if (isDark) HeatCellBorderDark else HeatCellBorder
    return when (day.state) {
        StreakDayState.FROZEN -> CellAppearance(
            fill = if (isDark) StreakFrozenDark else StreakFrozen,
            ringColor = null,
            ringWidth = 0.dp,
            alpha = 1f,
            border = border,
        )
        StreakDayState.BROKEN -> CellAppearance(
            fill = if (isDark) StreakBrokenDark else StreakBroken,
            ringColor = null,
            ringWidth = 0.dp,
            alpha = 1f,
            border = border,
        )
        StreakDayState.TODAY_PENDING -> CellAppearance(
            fill = heatColors[0],
            ringColor = MaterialTheme.colorScheme.primary,
            ringWidth = 2.dp,
            alpha = 1f,
            border = border,
        )
        StreakDayState.FUTURE -> CellAppearance(
            fill = heatColors[0],
            ringColor = null,
            ringWidth = 0.dp,
            alpha = 0.5f,
            border = border,
        )
        StreakDayState.COMPLETE, StreakDayState.EMPTY -> CellAppearance(
            fill = heatColors[day.heatLevel.coerceIn(0, 4)],
            ringColor = null,
            ringWidth = 0.dp,
            alpha = 1f,
            border = border,
        )
    }
}
