package com.jktdeveloper.habitto.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SmokingRooms
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.jktdeveloper.habitto.ui.components.habitIcon
import androidx.compose.material.icons.outlined.AccountCircle
import com.jktdeveloper.habitto.ui.theme.StreakFrozen
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBg
import com.jktdeveloper.habitto.ui.theme.StreakFrozenBgDark
import com.jktdeveloper.habitto.ui.theme.StreakFrozenDark
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.HabitTemplate
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.TemplateWithIdentities
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.identityIcon
import com.jktdeveloper.habitto.ui.components.StepProgressBar

// ── Step copy ────────────────────────────────────────────────────────────────

private data class StepCopy(val title: String, val subtitle: String)

private fun stepCopy(step: OnboardingStep) = when (step) {
    OnboardingStep.IDENTITY -> StepCopy(
        title = "Who are you becoming?",
        subtitle = "Pick everyone that's true. You'll see habits for each.",
    )
    OnboardingStep.HABITS -> StepCopy(
        title = "Pick habits that prove it.",
        subtitle = "Each habit earns points. Stay above your daily target to bank them.",
    )
    OnboardingStep.WANTS -> StepCopy(
        title = "What pulls you away?",
        subtitle = "Wants cost points. Pick the ones you do without thinking.",
    )
    OnboardingStep.SYNC -> StepCopy(
        title = "Sync across devices?",
        subtitle = "Sign in to sync. Skip if you'd rather stay local.",
    )
}

private fun stepIndex(step: OnboardingStep) = when (step) {
    OnboardingStep.IDENTITY -> 1
    OnboardingStep.HABITS -> 2
    OnboardingStep.WANTS -> 3
    OnboardingStep.SYNC -> 4
}

// ── Root screen ──────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
    onSignIn: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.finished.collect { event ->
            when (event) {
                OnboardingFinishEvent.Home -> onFinished()
                OnboardingFinishEvent.SignIn -> onSignIn()
            }
        }
    }

    val currentStep = uiState.step
    val index = stepIndex(currentStep)
    val copy = stepCopy(currentStep)

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
            ) {
                StepProgressBar(step = index, total = 4)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Step $index of 4",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = copy.title,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 28.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.4).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = copy.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (currentStep == OnboardingStep.IDENTITY) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pick 1–4 to start. You can add more later.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            OnboardingBottomBar(
                step = currentStep,
                primaryEnabled = when (currentStep) {
                    OnboardingStep.IDENTITY -> uiState.selectedIdentityIds.isNotEmpty()
                    else -> true
                },
                isLoading = uiState.isLoading,
                onLeftAction = {
                    when (currentStep) {
                        // "Skip" on identity bails the whole onboarding flow without setup
                        OnboardingStep.IDENTITY -> viewModel.finish()
                        else -> viewModel.back()
                    }
                },
                onRightAction = {
                    when (currentStep) {
                        OnboardingStep.IDENTITY -> viewModel.continueFromIdentity()
                        OnboardingStep.HABITS -> viewModel.continueFromHabits()
                        OnboardingStep.WANTS -> viewModel.continueFromWants()
                        OnboardingStep.SYNC -> viewModel.finish()
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        when (currentStep) {
            OnboardingStep.IDENTITY -> IdentityStepBody(
                identities = uiState.identities,
                selectedIds = uiState.selectedIdentityIds,
                onToggle = viewModel::toggleIdentity,
                modifier = Modifier.padding(innerPadding),
            )
            OnboardingStep.HABITS -> HabitsStepBody(
                templates = uiState.habitTemplates,
                selectedIds = uiState.selectedTemplateIds,
                onToggle = viewModel::toggleHabit,
                modifier = Modifier.padding(innerPadding),
            )
            OnboardingStep.WANTS -> WantsStepBody(
                activities = uiState.wantActivities,
                selectedIds = uiState.selectedActivityIds,
                onToggle = viewModel::toggleWantActivity,
                modifier = Modifier.padding(innerPadding),
            )
            OnboardingStep.SYNC -> SyncStepBody(
                onFinishAndSignIn = viewModel::finishAndSignIn,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

// ── Bottom navigation bar ────────────────────────────────────────────────────

@Composable
private fun OnboardingBottomBar(
    step: OnboardingStep,
    primaryEnabled: Boolean,
    isLoading: Boolean,
    onLeftAction: () -> Unit,
    onRightAction: () -> Unit,
) {
    Column {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onLeftAction) {
                Text(
                    text = if (step == OnboardingStep.IDENTITY) "Skip" else "Back",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.weight(1f))
            val primaryLabel = when (step) {
                OnboardingStep.SYNC -> if (isLoading) "Setting up…" else "I'll do it later"
                else -> "Next"
            }
            Button(
                onClick = onRightAction,
                enabled = primaryEnabled && !isLoading,
            ) {
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Step 1: Identity grid ─────────────────────────────────────────────────────

@Composable
private fun PickHint(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) StreakFrozenBgDark else StreakFrozenBg
    val accent = if (isDark) StreakFrozenDark else StreakFrozen
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = buildAnnotatedString {
                    append("Most people pick ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("1–3") }
                    append(" to start. You can add more later.")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun IdentityStepBody(
    identities: List<Identity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        PickHint(modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp),
        ) {
            items(identities, key = { it.id }) { identity ->
                IdentityGridCell(
                    identity = identity,
                    selected = identity.id in selectedIds,
                    onSelect = { onToggle(identity.id) },
                )
            }
        }
    }
}

@Composable
private fun IdentityGridCell(
    identity: Identity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    val selectedBg = Color.hsl(hue = hue, saturation = 0.30f, lightness = 0.94f)
    val selectedBorder = Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.50f)
    // Selected bg is always light regardless of theme — use a dark fg for legibility
    val selectedTitleFg = Color.hsl(hue = hue, saturation = 0.55f, lightness = 0.18f)
    val selectedSubtitleFg = Color.hsl(hue = hue, saturation = 0.40f, lightness = 0.30f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) selectedBg else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) selectedBorder else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            // Selected check badge at top-right
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(selectedBorder)
                        .align(Alignment.TopEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Column {
                HabitGlyph(
                    icon = identityIcon(identity.name),
                    hue = hue,
                    size = 36.dp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = identity.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = if (selected) selectedTitleFg else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = identity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) selectedSubtitleFg else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Step 2: Habits multi-select ───────────────────────────────────────────────

@Composable
private fun HabitsStepBody(
    templates: List<TemplateWithIdentities>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        items(templates, key = { it.template.id }) { row ->
            val selected = row.template.id in selectedIds
            HabitTemplateRow(
                template = row.template,
                recommendedBy = row.recommendedBy,
                selected = selected,
                onToggle = { onToggle(row.template.id) },
            )
        }
    }
}

@Composable
private fun HabitTemplateRow(
    template: HabitTemplate,
    recommendedBy: Set<Identity>,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitGlyph(
                icon = habitIcon(template.name),
                hue = IdentityHue.DEFAULT,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                val threshold = template.defaultThreshold.toInt()
                Text(
                    text = "Target $threshold ${template.unit} · 1 pt per $threshold",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recommendedBy.size > 1) {
                    Text(
                        text = "Recommended by: ${recommendedBy.joinToString(" · ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            CheckSquare(
                checked = selected,
                isError = false,
                onCheckedChange = onToggle,
            )
        }
    }
}

// ── Step 3: Wants multi-select ────────────────────────────────────────────────

@Composable
private fun WantsStepBody(
    activities: List<WantActivity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        items(activities, key = { it.id }) { activity ->
            val selected = activity.id in selectedIds
            WantActivityRow(
                activity = activity,
                selected = selected,
                onToggle = { onToggle(activity.id) },
            )
        }
    }
}

@Composable
private fun WantActivityRow(
    activity: WantActivity,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HabitGlyph(
                icon = wantIcon(activity.name),
                hue = 8f,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                // Canvas format: "−N pt / unit". Use costPerUnit; floor to 1 if < 1
                val cost = activity.costPerUnit.toInt().coerceAtLeast(1)
                Text(
                    text = "−$cost pt / ${activity.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CheckSquare(
                checked = selected,
                isError = true,
                onCheckedChange = onToggle,
            )
        }
    }
}

// ── Step 4: Sync ──────────────────────────────────────────────────────────────

@Composable
private fun SyncStepBody(
    onFinishAndSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 96.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onFinishAndSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        text = "Continue with email",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                OutlinedButton(
                    onClick = onFinishAndSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "You can sign in later from Settings. Your data stays on this device until you do.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

// ── Shared: CheckSquare ───────────────────────────────────────────────────────

@Composable
private fun CheckSquare(
    checked: Boolean,
    isError: Boolean,
    onCheckedChange: () -> Unit,
) {
    val fillColor = if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    val borderColor = if (checked) fillColor
    else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) fillColor else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        if (!checked) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, borderColor),
                modifier = Modifier.fillMaxSize(),
            ) {}
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Icon helpers ──────────────────────────────────────────────────────────────

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
