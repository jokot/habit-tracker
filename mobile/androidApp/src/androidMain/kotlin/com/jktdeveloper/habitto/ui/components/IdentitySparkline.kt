package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDayState
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

@Composable
fun IdentitySparkline(
    heat: List<Int>,
    states: List<StreakDayState>,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        heat.forEachIndexed { i, level ->
            val state = states.getOrNull(i) ?: StreakDayState.EMPTY
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(cellColor(level, state, isDark)),
            )
        }
    }
}

internal fun cellColor(level: Int, state: StreakDayState, isDark: Boolean): Color = when (state) {
    StreakDayState.FROZEN -> if (isDark) StreakFrozenDark else StreakFrozen
    StreakDayState.BROKEN -> if (isDark) StreakBrokenDark else StreakBroken
    else -> heatColor(level, isDark)
}

internal fun heatColor(level: Int, isDark: Boolean): Color = when (level) {
    1 -> if (isDark) HeatL1Dark else HeatL1
    2 -> if (isDark) HeatL2Dark else HeatL2
    3 -> if (isDark) HeatL3Dark else HeatL3
    4 -> if (isDark) HeatL4Dark else HeatL4
    else -> if (isDark) HeatL0Dark else HeatL0
}
