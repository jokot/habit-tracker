package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.model.StreakRangeResult
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.jktdeveloper.habitto.ui.theme.StreakBroken
import com.jktdeveloper.habitto.ui.theme.StreakBrokenDark
import com.jktdeveloper.habitto.ui.theme.StreakComplete
import com.jktdeveloper.habitto.ui.theme.StreakCompleteDark
import com.jktdeveloper.habitto.ui.theme.StreakEmpty
import com.jktdeveloper.habitto.ui.theme.StreakEmptyDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozen
import com.jktdeveloper.habitto.ui.theme.StreakFrozenDark
import com.jktdeveloper.habitto.ui.theme.StreakTodayOutline

@Composable
fun StreakStrip(
    range: StreakRangeResult,
    currentStreak: Int,
    onViewAll: () -> Unit,
    onDayTap: (StreakDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(Spacing.xl))
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.md))
                if (range.firstLogDate == null) {
                    Text(
                        text = "Log your first habit to start a streak.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.xl))
                } else {
                    Text(
                        text = "Streak",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Text(
                        text = "$currentStreak ${if (currentStreak == 1) "day" else "days"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onViewAll) { Text("View all") }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Spacing.xl,
                    vertical = Spacing.md,
                ),
            ) {
                streakDayItems(range.days, onDayTap)
            }
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

private fun LazyListScope.streakDayItems(
    days: List<StreakDay>,
    onDayTap: (StreakDay) -> Unit,
) {
    items(days.size, key = { idx -> days[idx].date.toString() }) { idx ->
        StreakDayCell(day = days[idx], onTap = { onDayTap(days[idx]) })
    }
}

@Composable
private fun StreakDayCell(
    day: StreakDay,
    onTap: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val color = when (day.state) {
        StreakDayState.COMPLETE -> if (isDark) StreakCompleteDark else StreakComplete
        StreakDayState.FROZEN -> if (isDark) StreakFrozenDark else StreakFrozen
        StreakDayState.BROKEN -> if (isDark) StreakBrokenDark else StreakBroken
        StreakDayState.EMPTY -> if (isDark) StreakEmptyDark else StreakEmpty
        StreakDayState.TODAY_PENDING -> if (isDark) StreakEmptyDark else StreakEmpty
    }
    val showOutline = day.state == StreakDayState.TODAY_PENDING
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .let { m ->
                if (showOutline) m.border(1.5.dp, StreakTodayOutline, RoundedCornerShape(4.dp))
                else m
            }
            .clickable(onClick = onTap),
    )
}
