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
import androidx.compose.material3.FilterChip
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
import com.habittracker.domain.model.DeviceMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWantScreen(viewModel: LogWantViewModel, onBack: () -> Unit) {
    val activity by viewModel.activity.collectAsState()
    val quantityInput by viewModel.quantityInput.collectAsState()
    val deviceMode by viewModel.deviceMode.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val undoState by viewModel.undoState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activity?.name ?: "Log Want") },
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
            activity?.let { act ->
                Text(act.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(Spacing.sm))
                val rateText = if (act.costPerUnit >= 1.0) {
                    "${act.costPerUnit.toInt()} pt per ${act.unit}"
                } else {
                    "1 pt per ${(1.0 / act.costPerUnit).toInt()} ${act.unit}"
                }
                Text(rateText, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.height(Spacing.xxl))

                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text("How many ${act.unit}?") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.log() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(Spacing.xl))

                Text("Device", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    FilterChip(
                        selected = deviceMode == DeviceMode.THIS_DEVICE,
                        onClick = { viewModel.onDeviceModeChange(DeviceMode.THIS_DEVICE) },
                        label = { Text("This device") },
                    )
                    FilterChip(
                        selected = deviceMode == DeviceMode.OTHER,
                        onClick = { viewModel.onDeviceModeChange(DeviceMode.OTHER) },
                        label = { Text("Other / physical") },
                    )
                }

                when (val state = uiState) {
                    is LogWantUiState.Error -> {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(state.message, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    is LogWantUiState.Success -> {
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            "-${state.pointsSpent} pts spent",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {}
                }

                Spacer(Modifier.height(Spacing.xl))

                if (uiState is LogWantUiState.Loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = viewModel::log,
                        enabled = uiState !is LogWantUiState.Loading,
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
