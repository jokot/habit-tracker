package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.StreakDay
import com.habittracker.domain.model.StreakDayState
import com.habittracker.domain.model.StreakSummary
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.NumeralStyle
import com.jktdeveloper.habitto.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakHistoryScreen(
    viewModel: StreakHistoryViewModel,
) {
    val summary by viewModel.summary.collectAsState()
    val months by viewModel.months.collectAsState()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    var selectedDay by remember { mutableStateOf<StreakDay?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Streak History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
                windowInsets = WindowInsets(0.dp),
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xxl),
        ) {
            item {
                SummaryCard(summary)
            }
            itemsIndexed(months) { index, month ->
                LaunchedEffect(index, months.size) {
                    if (index == months.lastIndex) viewModel.loadOlderMonth()
                }
                val topPad = if (index == 0) Spacing.xxl else Spacing.xl
                MonthCalendar(
                    month = month,
                    today = today,
                    modifier = Modifier.padding(top = topPad),
                    onDayClick = { day -> selectedDay = day },
                )
            }
        }
    }

    // Day-detail bottom sheet
    val day = selectedDay
    if (day != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedDay = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = null,
        ) {
            DayDetailSheet(day = day)
        }
    }
}

@Composable
private fun SummaryCard(summary: StreakSummary) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    Box(modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.md)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, outlineVariant),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Current streak — flame color
                StatCell(
                    label = "Current",
                    value = "${summary.currentStreak}",
                    valueColor = FlameOrange,
                    modifier = Modifier.weight(1f),
                )
                // Vertical divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(outlineVariant),
                )
                // Longest streak — onSurface
                StatCell(
                    label = "Longest",
                    value = "${summary.longestStreak}",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Vertical divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(outlineVariant),
                )
                // Total days — primary
                StatCell(
                    label = "Total",
                    value = "${summary.totalDaysComplete}",
                    valueColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = NumeralStyle.copy(fontSize = 36.sp, lineHeight = 36.sp),
            color = valueColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun DayDetailSheet(day: StreakDay) {
    val isActive = day.state == StreakDayState.COMPLETE || day.state == StreakDayState.TODAY_PENDING
    val isFrozen = day.state == StreakDayState.FROZEN
    val isBroken = day.state == StreakDayState.BROKEN

    // Format date as "Mon, MMM d" using java.time
    val javaDate = java.time.LocalDate.of(day.date.year, day.date.monthNumber, day.date.dayOfMonth)
    val dayOfWeekShort = javaDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val monthShort = javaDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val formattedDate = "$dayOfWeekShort, $monthShort ${day.date.dayOfMonth}"

    // State label & subtitle
    val stateLabel = when (day.state) {
        StreakDayState.COMPLETE -> "Active"
        StreakDayState.TODAY_PENDING -> "Pending"
        StreakDayState.FROZEN -> "Frozen"
        StreakDayState.BROKEN -> "Broken"
        StreakDayState.EMPTY -> "No data"
        StreakDayState.FUTURE -> "Future"
    }

    // Net points approximation from heatLevel (each level = ~3 points)
    val netPoints = day.heatLevel * 3

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .width(40.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(2.dp),
                )
                .align(Alignment.CenterHorizontally),
        )

        // Date row — date + net points
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (isActive || isFrozen || isBroken) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (netPoints > 0) {
                Text(
                    text = "+$netPoints",
                    style = NumeralStyle.copy(fontSize = 32.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Divider
        Spacer(Modifier.height(Spacing.xl))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(Spacing.xl))

        // "Logged" section header
        Text(
            text = "Logged",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.md),
        )

        // State detail row — use heat level as a proxy count
        if (isActive) {
            Text(
                text = "Day logged — heat level ${day.heatLevel}/4",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = "No habits logged this day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
