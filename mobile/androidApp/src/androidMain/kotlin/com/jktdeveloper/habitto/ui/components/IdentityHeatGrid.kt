package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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

@Composable
fun IdentityHeatGrid(heat: List<Int>, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    LazyVerticalGrid(
        columns = GridCells.Fixed(15),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        userScrollEnabled = false,
    ) {
        items(heat) { level ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isDark) heatColorDark(level) else heatColor(level)),
            )
        }
    }
}

private fun heatColor(level: Int) = when (level) {
    1 -> HeatL1; 2 -> HeatL2; 3 -> HeatL3; 4 -> HeatL4; else -> HeatL0
}

private fun heatColorDark(level: Int) = when (level) {
    1 -> HeatL1Dark; 2 -> HeatL2Dark; 3 -> HeatL3Dark; 4 -> HeatL4Dark; else -> HeatL0Dark
}
