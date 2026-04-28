package com.jktdeveloper.habitto.ui.streak

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakHistoryScreen(
    viewModel: StreakHistoryViewModel,
    onBack: () -> Unit,
) {
    val summary by viewModel.summary.collectAsState()
    val months by viewModel.months.collectAsState()
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Streak History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                SummaryCard(summary)
            }
            itemsIndexed(months) { index, month ->
                LaunchedEffect(index, months.size) {
                    if (index == months.lastIndex) viewModel.loadOlderMonth()
                }
                MonthCalendar(month = month, today = today)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: com.habittracker.domain.model.StreakSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(label = "Current", value = "${summary.currentStreak}")
            Stat(label = "Longest", value = "${summary.longestStreak}")
            Stat(label = "Total days", value = "${summary.totalDaysComplete}")
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
