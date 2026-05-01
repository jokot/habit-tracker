package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.jktdeveloper.habitto.ui.theme.StreakBrokenBg
import com.jktdeveloper.habitto.ui.theme.StreakBrokenBgDark
import com.jktdeveloper.habitto.ui.theme.StreakBrokenDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozen
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBg
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBgDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozenDark

@Composable
fun IdentitySparkline(
    heat: List<Int>,
    states: List<StreakDayState>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        heat.forEachIndexed { i, level ->
            val state = states.getOrNull(i) ?: StreakDayState.EMPTY
            HeatCell(level = level, state = state, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
internal fun HeatCell(level: Int, state: StreakDayState, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(3.dp)
    when (state) {
        StreakDayState.FROZEN -> {
            val bg = if (isDark) StreakFrozenBgDark else StreakFrozenBg
            val accent = if (isDark) StreakFrozenDark else StreakFrozen
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, accent, shape),
            ) {
                FrozenOverlay(accent)
            }
        }
        StreakDayState.BROKEN -> {
            val bg = if (isDark) StreakBrokenBgDark else StreakBrokenBg
            val accent = if (isDark) StreakBrokenDark else StreakBroken
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(bg)
                    .border(1.dp, accent, shape),
            ) {
                BrokenOverlay(accent)
            }
        }
        else -> Box(
            modifier = modifier
                .clip(shape)
                .background(heatColor(level, isDark)),
        )
    }
}

@Composable
private fun FrozenOverlay(color: Color) {
    // Two diagonal strokes forming an X. Inset ~15% of cell, stroke ~6% thickness.
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = minOf(w, h) * 0.15f
        val stroke = minOf(w, h) * 0.12f
        drawLine(
            color = color,
            start = Offset(pad, pad),
            end = Offset(w - pad, h - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w - pad, pad),
            end = Offset(pad, h - pad),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun BrokenOverlay(color: Color) {
    // Horizontal dash centered: ~50% wide, ~12% tall.
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val length = w * 0.5f
        val stroke = (h * 0.18f).coerceAtLeast(1.5f)
        drawLine(
            color = color,
            start = Offset((w - length) / 2f, h / 2f),
            end = Offset((w + length) / 2f, h / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

internal fun heatColor(level: Int, isDark: Boolean): Color = when (level) {
    1 -> if (isDark) HeatL1Dark else HeatL1
    2 -> if (isDark) HeatL2Dark else HeatL2
    3 -> if (isDark) HeatL3Dark else HeatL3
    4 -> if (isDark) HeatL4Dark else HeatL4
    else -> if (isDark) HeatL0Dark else HeatL0
}
