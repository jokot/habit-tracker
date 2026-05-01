package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDayState

/**
 * Non-lazy heat grid. Replaces an earlier LazyVerticalGrid impl that crashed when
 * nested inside the LazyColumn-based IdentityDetailScreen (infinite vertical
 * constraint). Uses Column-of-Rows so width is bounded by parent and height is
 * determined by row count × cell aspect ratio.
 *
 * Each cell renders via `HeatCell`: state coloring (FROZEN ice / BROKEN red) per
 * canvas tokens.css spec, otherwise heat bucket color.
 */
@Composable
fun IdentityHeatGrid(
    heat: List<Int>,
    states: List<StreakDayState>,
    modifier: Modifier = Modifier,
    columns: Int = 15,
) {
    val pairs = heat.zip(states)
    val rows = pairs.chunked(columns)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        rows.forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                rowCells.forEach { (level, state) ->
                    HeatCell(
                        level = level,
                        state = state,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                    )
                }
                repeat(columns - rowCells.size) {
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}
