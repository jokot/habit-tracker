package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.ui.theme.*
import kotlinx.datetime.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthCalendar(
    month: MonthData,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.xl)) {
        Text(
            text = "${java.time.Month.of(month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.md),
        )

        if (month.isLoading) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (month.error != null) {
            Text("Couldn't load — ${month.error}", color = MaterialTheme.colorScheme.error)
            return@Column
        }

        // 7-column grid: pad start with empty placeholders so the 1st aligns under the right weekday.
        val firstDay = LocalDate(month.year, month.month, 1)
        val firstDow = firstDay.dayOfWeek
        // We want columns laid out Mon..Sun (kotlinx-datetime DayOfWeek.MONDAY = ordinal 0).
        val padStart = firstDow.ordinal
        val cells: List<StreakDay?> = List(padStart) { null } + month.days
        val rows = cells.chunked(7)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    row.forEach { day ->
                        if (day == null) Box(modifier = Modifier.size(32.dp))
                        else DayCell(day = day, isToday = day.date == today)
                    }
                    repeat(7 - row.size) { Box(modifier = Modifier.size(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: StreakDay, isToday: Boolean) {
    val isDark = isSystemInDarkTheme()
    val (color, onColor) = when (day.state) {
        StreakDayState.COMPLETE -> (if (isDark) StreakCompleteDark else StreakComplete) to Color.White
        StreakDayState.FROZEN -> (if (isDark) StreakFrozenDark else StreakFrozen) to Color.White
        StreakDayState.BROKEN -> (if (isDark) StreakBrokenDark else StreakBroken) to Color.White
        StreakDayState.EMPTY -> (if (isDark) StreakEmptyDark else StreakEmpty) to MaterialTheme.colorScheme.onSurface
        StreakDayState.TODAY_PENDING -> (if (isDark) StreakEmptyDark else StreakEmpty) to MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .let { if (isToday || day.state == StreakDayState.TODAY_PENDING) it.border(1.5.dp, StreakTodayOutline, RoundedCornerShape(6.dp)) else it },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = onColor,
        )
    }
}
