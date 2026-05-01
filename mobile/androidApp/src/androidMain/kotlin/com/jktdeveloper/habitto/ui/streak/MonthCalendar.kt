package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.jktdeveloper.habitto.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")
private val CELL_GAP = 4.dp

@Composable
fun MonthCalendar(
    month: MonthData,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onDayClick: ((StreakDay) -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
    ) {
        // Month label
        Text(
            text = "${java.time.Month.of(month.month).getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Surface card wrapper
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (month.isLoading) {
                    Text(
                        text = "Loading…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    return@Column
                }
                if (month.error != null) {
                    Text(
                        text = "Couldn't load — ${month.error}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    return@Column
                }

                // Weekday header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                ) {
                    WEEKDAY_LABELS.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Day grid — 7 columns, padded start
                val firstDay = LocalDate(month.year, month.month, 1)
                val padStart = firstDay.dayOfWeek.ordinal // Mon=0
                val cells: List<StreakDay?> = List(padStart) { null } + month.days
                val rows = cells.chunked(7)

                Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
                        ) {
                            row.forEach { day ->
                                if (day == null) {
                                    // Leading/trailing empty pad — keeps aspect ratio
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                    )
                                } else {
                                    DayCell(
                                        day = day,
                                        isToday = day.date == today,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                        onClick = { onDayClick?.invoke(day) },
                                    )
                                }
                            }
                            // Fill remainder of last row
                            val remaining = 7 - row.size
                            if (remaining > 0) {
                                repeat(remaining) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: StreakDay,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val appearance = resolveCellAppearance(day, isDark)
    val onSurface = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(appearance.fill.copy(alpha = appearance.alpha))
            .let { m ->
                when {
                    isToday -> m.border(2.dp, onSurface, RoundedCornerShape(6.dp))
                    appearance.ringColor != null -> m.border(appearance.ringWidth, appearance.ringColor, RoundedCornerShape(6.dp))
                    else -> m.border(1.dp, appearance.border, RoundedCornerShape(6.dp))
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (appearance.overlay) {
            OverlayKind.FROZEN -> FrozenOverlay(appearance.overlayColor)
            OverlayKind.BROKEN -> BrokenOverlay(appearance.overlayColor)
            OverlayKind.NONE -> Unit
        }
    }
}
