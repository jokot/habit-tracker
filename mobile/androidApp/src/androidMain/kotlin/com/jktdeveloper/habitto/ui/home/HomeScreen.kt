package com.jktdeveloper.habitto.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.data.sync.SyncReason
import com.habittracker.data.sync.SyncState
import com.habittracker.domain.model.HabitWithProgress
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.isTimed
import com.jktdeveloper.habitto.ui.auth.LogoutDialog
import com.jktdeveloper.habitto.ui.components.DurationSheet
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.IdentityStrip
import com.jktdeveloper.habitto.ui.components.ReplaceTimerDialog
import com.jktdeveloper.habitto.ui.components.SyncChip
import com.jktdeveloper.habitto.ui.components.habitIcon
import com.jktdeveloper.habitto.ui.components.resolveWantIcon
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
    onIdentityClick: (String) -> Unit,
    onIdentitiesClick: () -> Unit,
    onOpenExchangeRate: () -> Unit = {},
    onOpenWantDetail: (id: String, openTimer: Boolean) -> Unit = { _, _ -> },
    onOpenTimer: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingMap by viewModel.pending.collectAsState()
    val pendingWantMap by viewModel.pendingWants.collectAsState()
    val homeTimer by viewModel.homeTimer.collectAsState()
    val currentRate by viewModel.currentRate.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val showLogoutDialog by viewModel.showLogoutDialog.collectAsState()
    val logoutUnsyncedCount by viewModel.logoutUnsyncedCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val durationSheetWant by viewModel.durationSheetWant.collectAsState()
    val pendingOverlap by viewModel.pendingOverlap.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is HomeEvent.OpenTimer -> onOpenTimer(event.activityId)
            }
        }
    }

    if (durationSheetWant != null) {
        DurationSheet(
            onPick = viewModel::requestStartTimer,
            onDismiss = viewModel::dismissDurationSheet,
        )
    }

    pendingOverlap?.let { overlap ->
        ReplaceTimerDialog(
            otherWantName = overlap.otherWantName,
            elapsedMin = overlap.elapsedMin,
            minutesLeft = overlap.minutesLeft,
            onReplace = viewModel::confirmReplace,
            onKeep = viewModel::dismissOverlap,
        )
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
        contentWindowInsets = WindowInsets(0.dp),
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

        val isRefreshing = uiState.isAuthenticated &&
            (syncState as? SyncState.Running)?.reason == SyncReason.MANUAL
        val content: @Composable () -> Unit = {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xxxl),
            ) {

                // ── Active Want-timer banner — active point-drain outranks the
                //    one-time migration notice below, so it sits above it. ────
                homeTimer?.let { timer ->
                    item {
                        HomeTimerBanner(
                            timer = timer,
                            onTap = { onOpenTimer(timer.activityId) },
                            onCancel = viewModel::cancelActiveTimer,
                        )
                    }
                }

                // ── Rate-ladder migration banner ──────────────────────────────
                item {
                    val showBanner by viewModel.showRateLadderBanner.collectAsState()
                    if (showBanner) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.markRateLadderBannerSeen()
                                onOpenExchangeRate()
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Spend rates updated — see Exchange rate.",
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                IconButton(onClick = viewModel::markRateLadderBannerSeen) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }
                }

                // ── Identity strip ────────────────────────────────────────────
                item {
                    val identities by viewModel.userIdentities.collectAsState()
                    val pinnedIdentityId by viewModel.pinnedIdentityId.collectAsState()
                    IdentityStrip(
                        identities = identities,
                        onChipClick = { onIdentityClick(it.id) },
                        onMoreClick = onIdentitiesClick,
                        pinnedIdentityId = pinnedIdentityId,
                    )
                }

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
                            onBalanceTap = onOpenExchangeRate,
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
                        val canAfford = uiState.pointBalance.balance >= 1
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
                                onTap = {
                                    when {
                                        !activity.isTimed -> viewModel.tapWant(activity)
                                        // Already running on this want: the duration
                                        // sheet would be wrong, so go to the timer.
                                        homeTimer?.activityId == activity.id ->
                                            onOpenTimer(activity.id)
                                        else -> viewModel.showDurationSheet(activity)
                                    }
                                },
                                onCancel = { viewModel.cancelPendingWant(activity.id) },
                                onLongPress = { onOpenWantDetail(activity.id, false) },
                            )
                        }
                    }
                }

                // Bottom padding handled by LazyColumn contentPadding
            }
        }
        if (uiState.isAuthenticated) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.manualRefresh() },
                modifier = Modifier.fillMaxSize().padding(padding),
            ) { content() }
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
        }
    }
}

// ── Home timer banner ────────────────────────────────────────────────────────

@Composable
private fun HomeTimerBanner(
    timer: HomeTimerUi,
    onTap: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    "${timer.wantName} · Timer running",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                )
                Text(
                    timer.remainingMmSs,
                    fontSize = 19.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        val dividerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.StopCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Cancel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WantActivityCard(
    activity: WantActivity,
    pending: PendingWantLog?,
    balance: Int,
    canAfford: Boolean,
    onTap: () -> Unit,
    onCancel: () -> Unit,
    onLongPress: () -> Unit,
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
            .combinedClickable(
                onClick = { onTap() },
                onLongClick = { onLongPress() },
            )
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
                        imageVector = resolveWantIcon(activity.iconKey, activity.name),
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
                        // Trailing: count pill when pending, otherwise the action a tap
                        // actually takes — a timed want starts its timer, it does not
                        // spend a point outright.
                        if (pending != null) {
                            WantCountPill(pending.count)
                        } else {
                            Icon(
                                imageVector = if (activity.isTimed) Icons.Default.Timer
                                else Icons.Default.Remove,
                                contentDescription = if (activity.isTimed) "Start timer"
                                else "Spend points",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Subtitle
                    Spacer(Modifier.height(Spacing.xs))
                    if (pending != null) {
                        val totalCost = pending.count
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
                                text = "${activity.unitsPerPoint} ${activity.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = " · −1 pt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
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

