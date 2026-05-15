package com.jktdeveloper.habitto.ui.habit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.PerHabitDayState
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.cellColor
import com.jktdeveloper.habitto.ui.components.habitIcon
import com.jktdeveloper.habitto.ui.theme.FlameOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(
    viewModel: HabitDetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val s = state
                    if (s is HabitDetailState.Loaded) {
                        IconButton(onClick = { onEdit(s.habit.id) }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit habit",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        when (val s = state) {
            HabitDetailState.Loading -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HabitDetailState.NotFound -> {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Habit not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is HabitDetailState.Loaded -> LoadedContent(state = s, contentPadding = padding)
        }
    }
}

@Composable
private fun LoadedContent(state: HabitDetailState.Loaded, contentPadding: PaddingValues) {
    val hue = if (state.firstIdentity != null)
        IdentityHue.forIdentity(state.firstIdentity)
    else 0f

    LazyColumn(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
        item {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                HabitGlyph(icon = habitIcon(state.habit.name), hue = hue, size = 56.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = state.habit.name,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 44.sp,
                )
                Spacer(Modifier.height(4.dp))
                val identityLabel = state.identityNames.joinToString(", ").ifBlank { "Unlinked" }
                Text(
                    text = "$identityLabel · ${formatThreshold(state.habit.thresholdPerPoint)} ${state.habit.unit} per pt · target ${state.habit.dailyTarget}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            StatsGrid(state = state, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        item {
            ThirtyDayCard(
                cells = state.streak.last30Days,
                hue = hue,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun StatsGrid(state: HabitDetailState.Loaded, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                label = "Per-habit streak",
                value = state.streak.currentStreak.toString(),
                suffix = "days",
                tint = FlameOrange,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Total logs",
                value = state.streak.totalLogs.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatTile(
                label = "Longest streak",
                value = state.streak.longestStreak.toString(),
                suffix = "days",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Points earned",
                value = state.streak.pointsEarned.toString(),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    suffix: String? = null,
    tint: Color? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tint ?: MaterialTheme.colorScheme.onSurface,
                    lineHeight = 32.sp,
                )
                if (suffix != null) {
                    Text(
                        suffix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThirtyDayCard(cells: List<PerHabitDayState>, hue: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "Last 30 days",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(10).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEach { cell ->
                            DayCell(
                                state = cell.state,
                                level = cell.heatLevel,
                                hue = hue,
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(state: StreakDayState, level: Int, hue: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val isDark = isSystemInDarkTheme()
    // Match identity heat grid palette (HeatL0..HeatL4 + StreakFrozen/StreakBroken
    // solid fills). COMPLETE bucket reflects how much of dailyTarget was hit:
    // bucket 1 = bare-min log, 4 = full target.
    val bg = cellColor(level, state, isDark)
    val baseModifier = modifier.clip(shape).background(
        if (state == StreakDayState.FUTURE) bg.copy(alpha = 0.5f) else bg,
    )
    val finalModifier = if (state == StreakDayState.TODAY_PENDING) {
        baseModifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        baseModifier
    }
    @Suppress("UNUSED_PARAMETER") val unusedHue = hue
    Box(modifier = finalModifier)
}

private fun formatThreshold(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
