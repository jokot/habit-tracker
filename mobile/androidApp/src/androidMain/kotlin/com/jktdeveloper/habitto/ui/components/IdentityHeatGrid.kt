package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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

/**
 * Non-lazy heat grid. Replaces an earlier LazyVerticalGrid impl that crashed when
 * nested inside the LazyColumn-based IdentityDetailScreen (infinite vertical
 * constraint). Uses Column-of-Rows so width is bounded by parent and height is
 * determined by row count × cell aspect ratio.
 */
@Composable
fun IdentityHeatGrid(heat: List<Int>, modifier: Modifier = Modifier, columns: Int = 15) {
    val isDark = isSystemInDarkTheme()
    val rows = heat.chunked(columns)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rows.forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                rowCells.forEach { level ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isDark) heatColorDark(level) else heatColor(level)),
                    )
                }
                // Pad short last row with invisible spacers so cell widths stay uniform
                repeat(columns - rowCells.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

private fun heatColor(level: Int) = when (level) {
    1 -> HeatL1; 2 -> HeatL2; 3 -> HeatL3; 4 -> HeatL4; else -> HeatL0
}

private fun heatColorDark(level: Int) = when (level) {
    1 -> HeatL1Dark; 2 -> HeatL2Dark; 3 -> HeatL3Dark; 4 -> HeatL4Dark; else -> HeatL0Dark
}
