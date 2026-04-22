package com.habittracker.android.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.habittracker.android.ui.theme.Spacing
import com.habittracker.domain.model.HabitTemplate
import com.habittracker.domain.model.Identity
import com.habittracker.domain.model.WantActivity

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.finished.collect { onFinished() }
    }

    val (stepIndex, stepTotal) = when (uiState.step) {
        OnboardingStep.IDENTITY -> 1 to 3
        OnboardingStep.HABITS -> 2 to 3
        OnboardingStep.WANTS -> 3 to 3
    }

    Scaffold(
        topBar = { StepProgressBar(stepIndex, stepTotal) },
    ) { padding ->
        when (uiState.step) {
            OnboardingStep.IDENTITY -> IdentityStep(
                identities = uiState.identities,
                selectedId = uiState.selectedIdentityId,
                onSelect = viewModel::selectIdentity,
                onContinue = viewModel::continueFromIdentity,
                modifier = Modifier.padding(padding),
            )
            OnboardingStep.HABITS -> HabitsStep(
                templates = uiState.habitTemplates,
                selectedIds = uiState.selectedTemplateIds,
                onToggle = viewModel::toggleHabit,
                onContinue = viewModel::continueFromHabits,
                onSkip = viewModel::continueFromHabits,
                modifier = Modifier.padding(padding),
            )
            OnboardingStep.WANTS -> WantsStep(
                activities = uiState.wantActivities,
                selectedIds = uiState.selectedActivityIds,
                onToggle = viewModel::toggleWantActivity,
                onFinish = viewModel::finish,
                onSkip = viewModel::finish,
                isLoading = uiState.isLoading,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun StepProgressBar(current: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xl),
    ) {
        Text(
            text = "Step $current of $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        LinearProgressIndicator(
            progress = { current.toFloat() / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
        )
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrimaryActionBar(
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    skipText: String? = null,
    onSkip: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(primaryText, style = MaterialTheme.typography.titleMedium)
        }
        if (skipText != null && onSkip != null) {
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = onSkip,
                enabled = primaryEnabled || onSkip !== onPrimary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(skipText)
            }
        }
    }
}

@Composable
private fun IdentityStep(
    identities: List<Identity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.xl),
    ) {
        StepHeader(
            title = "Who do you want to become?",
            subtitle = "Pick an identity. We'll suggest habits that fit.",
        )
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(identities, key = { it.id }) { identity ->
                IdentityCard(
                    identity = identity,
                    selected = identity.id == selectedId,
                    onSelect = { onSelect(identity.id) },
                )
            }
            item { Spacer(Modifier.height(Spacing.md)) }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryActionBar(
            primaryText = "Continue",
            primaryEnabled = selectedId != null,
            onPrimary = onContinue,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun IdentityCard(identity: Identity, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(identity.icon, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.size(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    identity.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    identity.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HabitsStep(
    templates: List<HabitTemplate>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.xl)) {
        StepHeader(
            title = "Recommended habits",
            subtitle = "Pick 2–3 to start. Less is more. (${selectedIds.size} selected)",
        )
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(templates, key = { it.id }) { template ->
                SelectableRow(
                    title = template.name,
                    subtitle = "Start with ${template.defaultThreshold.toInt()} ${template.unit} = 1 pt",
                    selected = template.id in selectedIds,
                    onToggle = { onToggle(template.id) },
                )
            }
            item { Spacer(Modifier.height(Spacing.md)) }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryActionBar(
            primaryText = "Continue",
            primaryEnabled = true,
            onPrimary = onContinue,
            skipText = "Skip",
            onSkip = onSkip,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun WantsStep(
    activities: List<WantActivity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = Spacing.xl)) {
        StepHeader(
            title = "What do you do for fun?",
            subtitle = "These cost points. Earn them first. (${selectedIds.size} selected)",
        )
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(activities, key = { it.id }) { activity ->
                val costText = if (activity.costPerUnit >= 1.0) {
                    "${activity.costPerUnit.toInt()} pt per ${activity.unit}"
                } else {
                    "1 pt per ${(1.0 / activity.costPerUnit).toInt()} ${activity.unit}"
                }
                SelectableRow(
                    title = activity.name,
                    subtitle = costText,
                    selected = activity.id in selectedIds,
                    onToggle = { onToggle(activity.id) },
                )
            }
            item { Spacer(Modifier.height(Spacing.md)) }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryActionBar(
            primaryText = if (isLoading) "Setting up…" else "Get Started",
            primaryEnabled = !isLoading,
            onPrimary = onFinish,
            skipText = "Skip",
            onSkip = onSkip,
        )
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline,
                ),
            )
            Column(modifier = Modifier.padding(start = Spacing.sm).weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
