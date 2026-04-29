package com.jktdeveloper.habitto.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.data.sync.SyncReason
import com.habittracker.data.sync.SyncState
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.ui.auth.LogoutDialog
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.SyncChip
import com.jktdeveloper.habitto.ui.streak.DailyStatusCard
import com.jktdeveloper.habitto.ui.theme.InterFontFamily
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.jktdeveloper.habitto.ui.theme.Surface1Dark
import com.jktdeveloper.habitto.ui.theme.Surface1Light

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSignIn: () -> Unit,
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
            // Custom sticky top bar — no TopAppBar, plain Row on background color
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "habitto",
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.4).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (uiState.isAuthenticated) {
                        SyncChip(syncState, onRetry = viewModel::triggerManualSync)
                    } else {
                        TextButton(onClick = onSignIn) { Text("Sign in") }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { Snackbar(it) }
        },
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxxl),
            ) {

                // ── DailyStatusCard ───────────────────────────────────────────
                item {
                    val streakRange by viewModel.streakStrip.collectAsState()
                    val streakSummary by viewModel.streakSummary.collectAsState()
                    Box(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                        DailyStatusCard(
                            range = streakRange,
                            currentStreak = streakSummary.currentStreak,
                            earned = uiState.pointBalance.earnedToday,
                            spent = uiState.pointBalance.spentToday,
                            balance = uiState.pointBalance.balance,
                            onDayTap = { onOpenStreakHistory() },
                        )
                    }
                }

                // ── Today's habits section header ─────────────────────────────
                item {
                    Column(
                        modifier = Modifier.padding(
                            start = Spacing.xl,
                            end = Spacing.xl,
                            top = Spacing.xxl,
                        ),
                    ) {
                        Text(
                            text = "Today's habits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (uiState.habitsWithProgress.isNotEmpty()) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "${uiState.habitsWithProgress.count { it.isGoalMet }} of ${uiState.habitsWithProgress.size} goals met",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // ── Habit cards ───────────────────────────────────────────────
                if (uiState.habitsWithProgress.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                            EmptyState("No habits yet. Complete onboarding to add them.")
                        }
                    }
                } else {
                    items(uiState.habitsWithProgress, key = { it.habit.id }) { hwp ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.xl)
                                .padding(top = Spacing.md),
                        ) {
                            HabitCard(
                                habitWithProgress = hwp,
                                pending = pendingMap[hwp.habit.id],
                                onTap = { viewModel.tapHabit(hwp.habit) },
                                onCancel = { viewModel.cancelPending(hwp.habit.id) },
                            )
                        }
                    }
                }

                // ── Wants section ─────────────────────────────────────────────
                if (uiState.wantActivities.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.padding(
                                start = Spacing.xl,
                                end = Spacing.xl,
                                top = Spacing.xxl,
                            ),
                        ) {
                            Text(
                                text = "Wants",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "Tap to spend points · ${uiState.pointBalance.balance} available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(uiState.wantActivities, key = { it.id }) { activity ->
                        val canAfford = uiState.pointBalance.balance >= perTapCostInt(activity)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = Spacing.xl)
                                .padding(top = Spacing.md),
                        ) {
                            WantActivityCard(
                                activity = activity,
                                pending = pendingWantMap[activity.id],
                                balance = uiState.pointBalance.balance,
                                canAfford = canAfford,
                                onTap = { viewModel.tapWant(activity) },
                                onCancel = { viewModel.cancelPendingWant(activity.id) },
                            )
                        }
                    }
                }

                // Bottom padding handled by LazyColumn contentPadding
            }
        }
    }
}

// ── Habit Card ────────────────────────────────────────────────────────────────

@Composable
private fun HabitCard(
    habitWithProgress: HabitWithProgress,
    pending: PendingHabitLog?,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    val isPending = pending != null
    val borderColor = if (isPending) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val hue = IdentityHue.DEFAULT

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isPending) 3.dp else 1.dp,
            color = borderColor,
        ),
        tonalElevation = if (isPending) 2.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Glyph
                HabitGlyph(
                    icon = habitIcon(habitWithProgress.habit.name),
                    hue = hue,
                    size = 44.dp,
                )

                // Right column
                Column(modifier = Modifier.weight(1f)) {
                    // Title row with trailing action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Name + done checkmark
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = habitWithProgress.habit.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (habitWithProgress.isGoalMet && !isPending) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        // Trailing: count pill when pending, Add icon when idle
                        if (pending != null) {
                            HabitCountPill(pending.count)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Log habit",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Subtitle
                    Spacer(Modifier.height(Spacing.xs))
                    val subtitleText = if (isPending) {
                        val threshold = habitWithProgress.habit.thresholdPerPoint.toInt()
                        val unit = habitWithProgress.habit.unit
                        val plural = if (threshold != 1) "s" else ""
                        "$threshold $unit$plural = 1 pt"
                    } else {
                        val v = habitWithProgress.pointsToday
                        val target = habitWithProgress.habit.dailyTarget
                        val threshold = habitWithProgress.habit.thresholdPerPoint.toInt()
                        val unit = habitWithProgress.habit.unit
                        val plural = if (threshold != 1) "s" else ""
                        "$v/$target $unit$plural · $threshold per pt"
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Progress / drain bar
                    Spacer(Modifier.height(Spacing.md))
                    if (pending != null) {
                        DrainBar(
                            fractionRemaining = pending.secondsRemaining / 3f,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        val barColor = Color.hsl(
                            hue = hue,
                            saturation = 0.5f,
                            lightness = 0.55f,
                        )
                        LinearProgressIndicator(
                            progress = { habitWithProgress.progressFraction },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                        )
                    }

                    // Pending action row
                    if (pending != null) {
                        Spacer(Modifier.height(Spacing.md))
                        PendingActionRow(
                            label = "Commits in ${pending.secondsRemaining}s",
                            accent = MaterialTheme.colorScheme.primary,
                            onCancel = onCancel,
                        )
                    }
                }
            }
        }
    }
}

// ── Want Activity Card ────────────────────────────────────────────────────────

@Composable
private fun WantActivityCard(
    activity: WantActivity,
    pending: PendingWantLog?,
    balance: Int,
    canAfford: Boolean,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    val isPending = pending != null
    val isDark = isSystemInDarkTheme()
    val borderColor = if (isPending) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.outlineVariant
    val iconBg = if (isDark) Surface1Dark else Surface1Light

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (!canAfford && !isPending) 0.5f else 1f)
            .clickable(onClick = onTap)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isPending) 3.dp else 1.dp,
            color = borderColor,
        ),
        tonalElevation = if (isPending) 2.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Icon container (44dp, rounded 12dp, surface1 bg)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = wantIcon(activity.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Right column
                Column(modifier = Modifier.weight(1f)) {
                    // Title row with trailing action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Name
                        Text(
                            text = activity.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // Trailing: count pill when pending, Remove icon when idle
                        if (pending != null) {
                            WantCountPill(pending.count)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Spend points",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Subtitle
                    Spacer(Modifier.height(Spacing.xs))
                    if (pending != null) {
                        val cost = perTapCostInt(activity)
                        val totalCost = cost * pending.count
                        val afterBalance = balance - totalCost
                        Row {
                            Text(
                                text = "−$totalCost pt total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = " · $afterBalance after",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Row {
                            Text(
                                text = "−${perTapCostInt(activity)} pt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = " / ${activity.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Drain bar when pending
                    if (pending != null) {
                        Spacer(Modifier.height(Spacing.md))
                        DrainBar(
                            fractionRemaining = pending.secondsRemaining / 3f,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(Spacing.md))
                        PendingActionRow(
                            label = "Spends in ${pending.secondsRemaining}s",
                            accent = MaterialTheme.colorScheme.error,
                            onCancel = onCancel,
                        )
                    }
                }
            }
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun HabitCountPill(count: Int) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Text(
            text = "×$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun WantCountPill(count: Int) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.error,
    ) {
        Text(
            text = "×$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onError,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun DrainBar(fractionRemaining: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(2.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fractionRemaining.coerceIn(0f, 1f))
                .height(4.dp)
                .background(color = color, shape = RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun PendingActionRow(label: String, accent: Color, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            color = accent,
        )
        TextButton(onClick = onCancel) {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                ),
                color = accent,
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

// ── Icon mapping helpers ──────────────────────────────────────────────────────

private fun wantIcon(name: String): ImageVector = when {
    name.contains("twitter", ignoreCase = true) || name.contains("/x", ignoreCase = true) -> Icons.Default.ChatBubble
    name.contains("instagram", ignoreCase = true) -> Icons.Default.PhotoCamera
    name.contains("tiktok", ignoreCase = true) || name.contains("scroll", ignoreCase = true) || name.contains("reel", ignoreCase = true) || name.contains("short", ignoreCase = true) -> Icons.Default.PlayCircle
    name.contains("youtube", ignoreCase = true) -> Icons.Default.SmartDisplay
    name.contains("netflix", ignoreCase = true) || name.contains("stream", ignoreCase = true) -> Icons.Default.SmartDisplay
    name.contains("reddit", ignoreCase = true) -> Icons.Default.Forum
    name.contains("game", ignoreCase = true) || name.contains("valorant", ignoreCase = true) || name.contains("pc gaming", ignoreCase = true) -> Icons.Default.SportsEsports
    name.contains("snack", ignoreCase = true) || name.contains("food", ignoreCase = true) || name.contains("junk", ignoreCase = true) || name.contains("donut", ignoreCase = true) || name.contains("dessert", ignoreCase = true) -> Icons.Default.Restaurant
    name.contains("smoke", ignoreCase = true) || name.contains("smoking", ignoreCase = true) -> Icons.Default.SmokingRooms
    name.contains("shop", ignoreCase = true) || name.contains("purchase", ignoreCase = true) -> Icons.Default.Restaurant
    name.contains("drink", ignoreCase = true) || name.contains("sugary", ignoreCase = true) -> Icons.Default.Restaurant
    else -> Icons.Default.MoreHoriz
}

private fun habitIcon(name: String?): ImageVector {
    if (name == null) return Icons.Default.CheckCircle
    return when {
        name.contains("read", ignoreCase = true) -> Icons.AutoMirrored.Filled.MenuBook
        name.contains("water", ignoreCase = true) || name.contains("drink", ignoreCase = true) -> Icons.Default.WaterDrop
        name.contains("sleep", ignoreCase = true) || name.contains("bedtime", ignoreCase = true) -> Icons.Default.Bedtime
        name.contains("run", ignoreCase = true) || name.contains("walk", ignoreCase = true)
            || name.contains("cycl", ignoreCase = true) || name.contains("push", ignoreCase = true)
            || name.contains("squat", ignoreCase = true) || name.contains("plank", ignoreCase = true)
            || name.contains("stretch", ignoreCase = true) -> Icons.AutoMirrored.Filled.DirectionsRun
        name.contains("meditat", ignoreCase = true) || name.contains("pray", ignoreCase = true)
            || name.contains("journal", ignoreCase = true) -> Icons.Default.SelfImprovement
        name.contains("school", ignoreCase = true) || name.contains("course", ignoreCase = true)
            || name.contains("flash", ignoreCase = true) || name.contains("language", ignoreCase = true)
            || name.contains("learn", ignoreCase = true) -> Icons.Default.School
        name.contains("video", ignoreCase = true) || name.contains("watch", ignoreCase = true) -> Icons.Default.SmartDisplay
        name.contains("write", ignoreCase = true) || name.contains("blog", ignoreCase = true) || name.contains("draft", ignoreCase = true) -> Icons.Default.Forum
        name.contains("code", ignoreCase = true) || name.contains("test", ignoreCase = true) || name.contains("refactor", ignoreCase = true) -> Icons.Default.Forum
        else -> Icons.Default.CheckCircle
    }
}

// ── Point helpers ─────────────────────────────────────────────────────────────

private fun perTapCostInt(activity: WantActivity): Int =
    if (activity.costPerUnit >= 1.0) activity.costPerUnit.toInt()
    else 1
