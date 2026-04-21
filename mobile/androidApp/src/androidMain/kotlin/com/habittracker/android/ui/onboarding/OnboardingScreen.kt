package com.habittracker.android.ui.onboarding

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold { padding ->
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
private fun IdentityStep(
    identities: List<Identity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
    ) {
        Text("Who do you want to become?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(identities) { identity ->
                val selected = identity.id == selectedId
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(identity.id) },
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
                        Text(identity.icon, style = MaterialTheme.typography.headlineMedium)
                        Column(modifier = Modifier.padding(start = Spacing.md)) {
                            Text(identity.name, style = MaterialTheme.typography.titleMedium)
                            Text(identity.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Button(
            onClick = onContinue,
            enabled = selectedId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
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
    Column(modifier = modifier.fillMaxSize().padding(Spacing.xl)) {
        Text("Recommended habits", style = MaterialTheme.typography.headlineSmall)
        Text("Pick 2–3 to start. Less is more.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(templates) { template ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(template.id) }.padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = template.id in selectedIds, onCheckedChange = { onToggle(template.id) })
                    Column(modifier = Modifier.padding(start = Spacing.sm)) {
                        Text(template.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Start with ${template.defaultThreshold.toInt()} ${template.unit} = 1 point",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
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
    Column(modifier = modifier.fillMaxSize().padding(Spacing.xl)) {
        Text("What do you do for fun?", style = MaterialTheme.typography.headlineSmall)
        Text("These cost points. Earn them first.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.xl))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(activities) { activity ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(activity.id) }.padding(vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = activity.id in selectedIds, onCheckedChange = { onToggle(activity.id) })
                    Column(modifier = Modifier.padding(start = Spacing.sm)) {
                        Text(activity.name, style = MaterialTheme.typography.bodyLarge)
                        val costText = if (activity.costPerUnit >= 1.0) {
                            "${activity.costPerUnit.toInt()} pt / ${activity.unit}"
                        } else {
                            "1 pt / ${(1.0 / activity.costPerUnit).toInt()} ${activity.unit}"
                        }
                        Text(costText, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onFinish, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Get Started")
        }
        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onSkip, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
            Text("Skip")
        }
    }
}
