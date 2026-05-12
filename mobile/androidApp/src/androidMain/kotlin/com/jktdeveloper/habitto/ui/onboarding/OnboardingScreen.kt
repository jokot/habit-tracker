package com.jktdeveloper.habitto.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktdeveloper.habitto.ui.components.StepProgressBar
import com.jktdeveloper.habitto.ui.onboarding.steps.HabitsStep
import com.jktdeveloper.habitto.ui.onboarding.steps.IdentityStep
import com.jktdeveloper.habitto.ui.onboarding.steps.SignInStep
import com.jktdeveloper.habitto.ui.onboarding.steps.WantsStep

// ── Step copy ────────────────────────────────────────────────────────────────

private data class StepCopy(val title: String, val subtitle: String)

private fun stepCopy(step: OnboardingStep) = when (step) {
    OnboardingStep.IDENTITY -> StepCopy(
        title = "Who do you want to become?",
        subtitle = "Choose an identity. Habitto suggests habits that support it.",
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
            OnboardingStep.IDENTITY -> IdentityStep(
                identities = uiState.identities,
                selectedIds = uiState.selectedIdentityIds,
                onToggle = viewModel::toggleIdentity,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            OnboardingStep.HABITS -> HabitsStep(
                templates = uiState.habitTemplates,
                selectedIdentityIds = uiState.selectedIdentityIds,
                selectedTemplateIds = uiState.selectedTemplateIds,
                onToggle = viewModel::toggleHabit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            OnboardingStep.WANTS -> WantsStep(
                wants = uiState.wantActivities,
                selectedIds = uiState.selectedActivityIds,
                onToggle = viewModel::toggleWantActivity,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
            OnboardingStep.SYNC -> SignInStep(
                onContinueEmail = { viewModel.finishAndSignIn() },
                onContinueGoogle = { viewModel.finishAndSignIn() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
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
