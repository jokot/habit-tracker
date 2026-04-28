package com.jktdeveloper.habitto.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.jktdeveloper.habitto.ui.theme.streakCompleteColor
import com.jktdeveloper.habitto.ui.streak.DailyStatusCard
import com.habittracker.data.sync.SyncReason
import com.habittracker.data.sync.SyncState
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSignIn: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStreakHistory: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingMap by viewModel.pending.collectAsState()
    val pendingWantMap by viewModel.pendingWants.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val logoutUnsyncedCount by viewModel.logoutUnsyncedCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    // Short categorized label only; full stack trace stays in logcat.
    LaunchedEffect(syncState) {
        val state = syncState
        if (state is SyncState.Error) {
            snackbarHostState.showSnackbar(state.message)
        }
    }

    if (showLogoutDialog) {
        LogoutDialog(
            unsyncedCount = logoutUnsyncedCount,
            onConfirm = { force -> viewModel.confirmSignOut(force) },
            onDismiss = viewModel::dismissLogoutDialog,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Habit Tracker",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    if (uiState.isAuthenticated) {
                        SyncStatusChip(syncState, onRetry = viewModel::triggerManualSync)
                    } else {
                        TextButton(onClick = onSignIn) { Text("Sign in") }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    Spacer(Modifier.width(Spacing.sm))
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

        val isRefreshing = (syncState as? SyncState.Running)?.reason == SyncReason.MANUAL
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.manualRefresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                item {
                    val streakRange by viewModel.streakStrip.collectAsState()
                    val streakSummary by viewModel.streakSummary.collectAsState()
                    Spacer(Modifier.height(Spacing.sm))
                    DailyStatusCard(
                        range = streakRange,
                        currentStreak = streakSummary.currentStreak,
                        earned = uiState.pointBalance.earned,
                        spent = uiState.pointBalance.spent,
                        balance = uiState.pointBalance.balance,
                        onViewAll = onOpenStreakHistory,
                        onDayTap = { onOpenStreakHistory() },
                    )
                }

                item {
                    SectionHeader(
                        title = "Today's Habits",
                        subtitle = if (uiState.habitsWithProgress.isEmpty()) null
                        else "${uiState.habitsWithProgress.count { it.isGoalMet }} of ${uiState.habitsWithProgress.size} goals met",
                    )
                }

                if (uiState.habitsWithProgress.isEmpty()) {
                    item { EmptyState("No habits yet. Complete onboarding to add them.") }
                } else {
                    items(uiState.habitsWithProgress, key = { it.habit.id }) { hwp ->
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
                        Spacer(Modifier.height(Spacing.md))
                        SectionHeader(title = "Want Activities", subtitle = "Each tap = 1 session")
                    }
                    items(uiState.wantActivities, key = { it.id }) { activity ->
                        WantActivityCard(
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
}

@Composable
private fun SyncedPill() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(horizontal = Spacing.md),
    ) {
        Text(
            text = "Synced",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

@Composable
private fun HabitCard(
    habitWithProgress: HabitWithProgress,
    pending: PendingHabitLog?,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    val accent =
        if (habitWithProgress.isGoalMet) streakCompleteColor() else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.padding(end = Spacing.md)) {
                    Text(
                        text = habitWithProgress.habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "${habitWithProgress.habit.thresholdPerPoint.toInt()} ${habitWithProgress.habit.unit} = 1 pt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pending != null) {
                    CountPill(pending.count, accent)
                } else {
                    ProgressPill(habitWithProgress.progressText, habitWithProgress.isGoalMet, accent)
                }
            }
            Spacer(Modifier.height(Spacing.lg))
            LinearProgressIndicator(
                progress = { habitWithProgress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            if (pending != null) {
                Spacer(Modifier.height(Spacing.md))
                PendingFooter(pending.secondsRemaining, onCancel, accent)
            }
        }
    }
}

@Composable
private fun WantActivityCard(
    activity: WantActivity,
    pending: PendingWantLog?,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.tertiary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.padding(end = Spacing.md)) {
                    Text(
                        text = activity.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = costText(activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (pending != null) {
                    CountPill(pending.count, accent)
                } else {
                    CostPill(perTapCost(activity))
                }
            }
            if (pending != null) {
                Spacer(Modifier.height(Spacing.md))
                PendingFooter(pending.secondsRemaining, onCancel, accent)
            }
        }
    }
}

@Composable
private fun CountPill(count: Int, accent: Color) {
    Surface(
        shape = CircleShape,
        color = accent,
    ) {
        Text(
            text = "×$count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun ProgressPill(text: String, isGoalMet: Boolean, accent: Color) {
    val (bg, fg) = if (isGoalMet) {
        accent to MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = bg) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun CostPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

@Composable
private fun PendingFooter(secondsRemaining: Int, onCancel: () -> Unit, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Commits in ${secondsRemaining}s",
            style = MaterialTheme.typography.bodySmall,
            color = accent,
        )
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

private fun costText(activity: WantActivity): String =
    if (activity.costPerUnit >= 1.0) {
        "${activity.costPerUnit.toInt()} pt per ${activity.unit}"
    } else {
        "1 pt per ${(1.0 / activity.costPerUnit).toInt()} ${activity.unit}"
    }

private fun perTapCost(activity: WantActivity): String {
    val perTap = if (activity.costPerUnit >= 1.0) activity.costPerUnit.toInt()
    else 1 // ceil(1 × cost) with min-1 — matches PointCalculator.pointsSpent
    return "-$perTap pt"
}

@Composable
private fun SyncStatusChip(state: SyncState, onRetry: () -> Unit) {
    val container: androidx.compose.ui.graphics.Color
    val onContainer: androidx.compose.ui.graphics.Color
    val label: String
    val showSpinner: Boolean
    val clickable: Boolean

    when (state) {
        is SyncState.Running -> {
            container = androidx.compose.ui.graphics.Color(0xFFFFF3C4)        // amber 100
            onContainer = androidx.compose.ui.graphics.Color(0xFF7A4F01)      // amber 900
            label = "Syncing"
            showSpinner = true
            clickable = false
        }
        is SyncState.Error -> {
            container = androidx.compose.ui.graphics.Color(0xFFFFD9D9)        // red 100
            onContainer = androidx.compose.ui.graphics.Color(0xFFB71C1C)      // red 900
            label = "Sync failed"
            showSpinner = false
            clickable = true
        }
        else -> {
            container = androidx.compose.ui.graphics.Color(0xFFD7E8FF)        // blue 100
            onContainer = androidx.compose.ui.graphics.Color(0xFF0D47A1)      // blue 900
            label = "Synced"
            showSpinner = false
            clickable = false
        }
    }

    Surface(
        shape = CircleShape,
        color = container,
        modifier = Modifier
            .padding(horizontal = Spacing.xs)
            .let { if (clickable) it.clickable(onClick = onRetry) else it },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = onContainer,
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer,
            )
        }
    }
}
