package com.habittracker.android.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.habittracker.android.ui.theme.Spacing
import com.habittracker.android.ui.theme.streakCompleteColor
import com.habittracker.domain.usecase.LogHabitStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogHabitScreen(viewModel: LogHabitViewModel, onBack: () -> Unit) {
    val habit by viewModel.habit.collectAsState()
    val quantityInput by viewModel.quantityInput.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val undoState by viewModel.undoState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(habit?.name ?: "Log Habit") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.xxl)
                .imePadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            habit?.let { h ->
                Text(h.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    "Threshold: ${h.thresholdPerPoint.toInt()} ${h.unit} = 1 point",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xxl))

                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text("How many ${h.unit}?") },
                    placeholder = { Text("${h.thresholdPerPoint.toInt()}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.log() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                when (val state = uiState) {
                    is LogHabitUiState.Error -> {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    is LogHabitUiState.Success -> {
                        Spacer(Modifier.height(Spacing.md))
                        val (text, isWin) = when (state.status) {
                            LogHabitStatus.EARNED -> "+${state.pointsEarned} pts earned!" to true
                            LogHabitStatus.BELOW_THRESHOLD ->
                                "Below threshold — logged, 0 pts" to false
                            LogHabitStatus.DAILY_TARGET_MET ->
                                "Daily goal already met today — logged, 0 pts" to false
                        }
                        Text(
                            text,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isWin) streakCompleteColor()
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {}
                }

                Spacer(Modifier.height(Spacing.xl))

                if (uiState is LogHabitUiState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = viewModel::log,
                        enabled = uiState !is LogHabitUiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Log")
                    }
                }

                undoState?.let { undo ->
                    Spacer(Modifier.height(Spacing.md))
                    val mins = undo.secondsRemaining / 60
                    val secs = undo.secondsRemaining % 60
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Undo available: %d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { viewModel.undo(undo.logId) }) {
                            Text("Undo")
                        }
                    }
                }
            } ?: CircularProgressIndicator()
        }
    }
}
