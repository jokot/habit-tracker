package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.model.StreakRangeResult
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.FlameSoft
import com.jktdeveloper.habitto.ui.theme.FlameSoftDark
import com.jktdeveloper.habitto.ui.theme.NumeralStyle
import com.jktdeveloper.habitto.ui.theme.StreakBroken
import com.jktdeveloper.habitto.ui.theme.StreakBrokenBg
import com.jktdeveloper.habitto.ui.theme.StreakBrokenBgDark
import com.jktdeveloper.habitto.ui.theme.StreakBrokenDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozen
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBg
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBgDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozenDark

// ── Streak display state resolution ──────────────────────────────────────────

private enum class StreakDisplayState { Fresh, Frozen, Broken, Mid }

private fun streakDisplayState(range: StreakRangeResult, currentStreak: Int): StreakDisplayState {
    if (range.firstLogDate == null ||
        (currentStreak <= 0 && range.days.lastOrNull()?.state != StreakDayState.BROKEN)
    ) {
        return StreakDisplayState.Fresh
    }
    val today = range.days.lastOrNull()
    if (today?.state == StreakDayState.BROKEN) return StreakDisplayState.Broken
    val yesterday = range.days.getOrNull(range.days.size - 2)
    if (yesterday?.state == StreakDayState.FROZEN) return StreakDisplayState.Frozen
    return StreakDisplayState.Mid
}

// ── Public composable ─────────────────────────────────────────────────────────

@Composable
fun DailyStatusCard(
    range: StreakRangeResult,
    currentStreak: Int,
    earned: Int,
    spent: Int,
    balance: Int,
    onDayTap: (StreakDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val state = streakDisplayState(range, currentStreak)

    // State-based icon container color, icon tint, and icon image
    val containerBg: Color
    val iconColor: Color
    val iconImage = when (state) {
        StreakDisplayState.Broken -> {
            containerBg = if (isDark) StreakBrokenBgDark else StreakBrokenBg
            iconColor = if (isDark) StreakBrokenDark else StreakBroken
            Icons.Default.Refresh
        }
        StreakDisplayState.Frozen -> {
            containerBg = if (isDark) StreakFrozenBgDark else StreakFrozenBg
            iconColor = if (isDark) StreakFrozenDark else StreakFrozen
            Icons.Default.AcUnit
        }
        else -> {
            containerBg = if (isDark) FlameSoftDark else FlameSoft
            iconColor = if (isDark) FlameOrangeDark else FlameOrange
            Icons.Default.LocalFireDepartment
        }
    }

    val supportingText = when (state) {
        StreakDisplayState.Broken -> "Streak reset. Start fresh today."
        StreakDisplayState.Frozen -> "Yesterday was frozen — don't miss today."
        StreakDisplayState.Fresh  -> "Log a habit to start your streak."
        StreakDisplayState.Mid    -> "Keep going — log a habit today."
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            // ── Streak header (ConstraintLayout per canvas) ───────────────────
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
                val (iconRef, streakRef, daysRef, supportRef) = createRefs()

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(containerBg)
                        .constrainAs(iconRef) {
                            start.linkTo(parent.start)
                            top.linkTo(parent.top)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconImage,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Text(
                    text = currentStreak.toString(),
                    style = NumeralStyle.copy(
                        fontSize = 44.sp,
                        lineHeight = 44.sp,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.constrainAs(streakRef) {
                        start.linkTo(iconRef.end, margin = 12.dp)
                        top.linkTo(iconRef.top)
                        bottom.linkTo(iconRef.bottom)
                    },
                )

                Text(
                    text = if (currentStreak == 1) "day" else "days",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.constrainAs(daysRef) {
                        start.linkTo(streakRef.end, margin = 6.dp)
                        baseline.linkTo(streakRef.baseline)
                    },
                )

                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.constrainAs(supportRef) {
                        start.linkTo(streakRef.start)
                        top.linkTo(streakRef.bottom, margin = 2.dp)
                        end.linkTo(parent.end)
                        width = Dimension.fillToConstraints
                    },
                )
            }

            // ── 7-day heatmap row with weekday labels ─────────────────────────
            val weekdayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
            val sevenDays = range.days.takeLast(7)
            Row(
                modifier = Modifier
                    .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                sevenDays.forEachIndexed { index, day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        StreakDayCell(day = day, onTap = { onDayTap(day) })
                        Text(
                            text = weekdayLabels.getOrElse(index) { "" },
                            fontSize = 10.sp,
                            fontWeight = if (index == sevenDays.lastIndex) FontWeight.SemiBold
                                         else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Divider ───────────────────────────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── KPI row ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(top = 14.dp, bottom = 16.dp),
            ) {
                KpiCell(
                    label = "EARNED",
                    value = "+$earned",
                    valueColor = MaterialTheme.colorScheme.primary,
                    emphasized = false,
                    showLeftBorder = false,
                    modifier = Modifier.weight(1f),
                )
                KpiCell(
                    label = "SPENT",
                    value = "−$spent",
                    valueColor = MaterialTheme.colorScheme.error,
                    emphasized = false,
                    showLeftBorder = true,
                    modifier = Modifier.weight(1f),
                )
                KpiCell(
                    label = "BALANCE · PTS",
                    value = balance.toString(),
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    emphasized = true,
                    showLeftBorder = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ── KPI cell ──────────────────────────────────────────────────────────────────

@Composable
private fun KpiCell(
    label: String,
    value: String,
    valueColor: Color,
    emphasized: Boolean,
    showLeftBorder: Boolean,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Row(modifier = modifier) {
        if (showLeftBorder) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(outlineColor),
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = NumeralStyle.copy(
                        fontSize = if (emphasized) 32.sp else 26.sp,
                        lineHeight = if (emphasized) 35.2.sp else 28.6.sp,
                    ),
                    color = valueColor,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Heatmap cell (reused in StreakHistory full-grid) ──────────────────────────

@Suppress("unused") // Kept for StreakHistory full-grid usage via LazyRow
private fun LazyListScope.streakDayItems(
    days: List<StreakDay>,
    onDayTap: (StreakDay) -> Unit,
) {
    items(days.size, key = { idx -> days[idx].date.toString() }) { idx ->
        StreakDayCell(day = days[idx], onTap = { onDayTap(days[idx]) })
    }
}

@Composable
internal fun StreakDayCell(
    day: StreakDay,
    onTap: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val appearance = resolveCellAppearance(day, isDark)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(appearance.fill.copy(alpha = appearance.alpha))
            .let { m ->
                if (appearance.ringColor != null) {
                    m.border(appearance.ringWidth, appearance.ringColor, RoundedCornerShape(8.dp))
                } else {
                    m.border(1.dp, appearance.border, RoundedCornerShape(8.dp))
                }
            }
            .clickable(onClick = onTap),
    )
}
