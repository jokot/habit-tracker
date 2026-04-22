package com.habittracker.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.habittracker.android.ui.theme.Spacing
import com.habittracker.android.ui.theme.streakCompleteColor
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSignIn: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingMap by viewModel.pending.collectAsState()
    val pendingWantMap by viewModel.pendingWants.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Habit Tracker") },
                actions = {
                    if (uiState.isAuthenticated) {
                        Text(
                            text = "Synced",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.md),
                        )
                    } else {
                        TextButton(onClick = onSignIn) {
                            Text("Sign in")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Spacer(Modifier.height(Spacing.md))
                PointBalanceCard(
                    earned = uiState.pointBalance.earned,
                    spent = uiState.pointBalance.spent,
                    balance = uiState.pointBalance.balance,
                )
                Spacer(Modifier.height(Spacing.xl))
                Text("Today's Habits", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
            }

            if (uiState.habitsWithProgress.isEmpty()) {
                item {
                    Text(
                        "No habits yet. Complete onboarding to add habits.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.habitsWithProgress) { hwp ->
                    HabitCard(
                        habitWithProgress = hwp,
                        pending = pendingMap[hwp.habit.id],
                        onTap = { viewModel.tapHabit(hwp.habit) },
                        onCancel = { viewModel.cancelPending(hwp.habit.id) },
                    )
                }
            }

            if (uiState.wantActivities.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(Spacing.xl))
                    HorizontalDivider()
                    Spacer(Modifier.height(Spacing.md))
                    Text("Want Activities", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                }
                items(uiState.wantActivities) { activity ->
                    WantActivityRow(
                        activity = activity,
                        pending = pendingWantMap[activity.id],
                        onTap = { viewModel.tapWant(activity) },
                        onCancel = { viewModel.cancelPendingWant(activity.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(Spacing.xxxl)) }
        }
    }
}

@Composable
private fun PointBalanceCard(earned: Int, spent: Int, balance: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Text("Point Balance", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(Spacing.sm))
            Text("$balance pts", style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(Spacing.xs))
            Text("Earned: $earned  ·  Spent: $spent",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun HabitCard(
    habitWithProgress: HabitWithProgress,
    pending: PendingHabitLog?,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(habitWithProgress.habit.name, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pending != null) {
                        Text(
                            "×${pending.count}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = Spacing.md),
                        )
                    }
                    Text(
                        habitWithProgress.progressText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (habitWithProgress.isGoalMet) streakCompleteColor()
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            LinearProgressIndicator(
                progress = { habitWithProgress.progressFraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (habitWithProgress.isGoalMet) streakCompleteColor()
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Threshold: ${habitWithProgress.habit.thresholdPerPoint.toInt()} ${habitWithProgress.habit.unit} = 1 pt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pending != null) {
                Spacer(Modifier.height(Spacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Commits in ${pending.secondsRemaining}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun WantActivityRow(
    activity: WantActivity,
    pending: PendingWantLog?,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(activity.name, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pending != null) {
                    Text(
                        "×${pending.count}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = Spacing.md),
                    )
                }
                val costText = if (activity.costPerUnit >= 1.0) {
                    "${activity.costPerUnit.toInt()} pt/${activity.unit}"
                } else {
                    "1 pt/${(1.0 / activity.costPerUnit).toInt()} ${activity.unit}"
                }
                Text(
                    costText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (pending != null) {
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Commits in ${pending.secondsRemaining}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
