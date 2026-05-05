package com.jktdeveloper.habitto.devtools

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsScreen(viewModel: DevToolsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val toast = state.toast
    LaunchedEffect(toast) {
        toast ?: return@LaunchedEffect
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Dev tools",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            SectionLabel("Seed streak")
            ModeRow(state.mode, viewModel::onModeChange)
            Spacer(Modifier.height(12.dp))

            DaysSlider(state.days, viewModel::onDaysChange)
            Spacer(Modifier.height(8.dp))

            if (state.mode == SeedMode.Constant) {
                LevelChips(state.constantLevel, viewModel::onLevelChange)
                Spacer(Modifier.height(8.dp))
            }

            CountStepper("Freeze days", state.freezeCount, viewModel::onFreezeChange)
            CountStepper("Broken pairs", state.brokenCount, viewModel::onBrokenChange)
            HelperText(
                "Each broken = 2-day gap. Budget: ${state.days} − " +
                    "${state.freezeCount} − 2×${state.brokenCount} = " +
                    "${(state.days - state.freezeCount - 2 * state.brokenCount).coerceAtLeast(0)} complete days."
            )

            Spacer(Modifier.height(20.dp))
            SectionLabel("Want spends")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.seedWantSpends, onCheckedChange = viewModel::onSeedWantsToggle)
                Spacer(Modifier.width(12.dp))
                Text("Also seed 1 want spend per complete day")
            }
            if (state.seedWantSpends) {
                Spacer(Modifier.height(8.dp))
                ActivityDropdown(state, viewModel::onActivitySelect)
                Spacer(Modifier.height(8.dp))
                val rawQty = remember(state.seedWantSpends) { mutableStateOf(state.wantQuantity.toString()) }
                OutlinedTextField(
                    value = rawQty.value,
                    onValueChange = { input ->
                        rawQty.value = input
                        input.toDoubleOrNull()?.let(viewModel::onWantQuantityChange)
                    },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.validationError?.let { err ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        err,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = viewModel::onSeedClick,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Seed") }

            Spacer(Modifier.height(40.dp))
        }
    }

    val onConfirmSeed = remember(viewModel) { viewModel::confirmSeed }
    val onDismissConfirm = remember(viewModel) { viewModel::dismissConfirm }

    state.pendingConfirm?.let { confirm ->
        AlertDialog(
            onDismissRequest = onDismissConfirm,
            title = { Text("Confirm seed") },
            text = {
                Column {
                    Text("Will write:")
                    Text("• ${confirm.completeSlots} complete days")
                    Text("• ${confirm.freezeSlots} freeze days")
                    Text("• ${confirm.brokenSlots} broken slots")
                    Spacer(Modifier.height(8.dp))
                    Text("Will delete:")
                    Text("• ${confirm.habitLogsToDelete} habit logs")
                    Text("• ${confirm.wantLogsToDelete} want logs")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Expected rate after seed: ${confirm.expectedRate}×",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Logs will sync to cloud on next sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { Button(onClick = onConfirmSeed) { Text("Seed") } },
            dismissButton = { OutlinedButton(onClick = onDismissConfirm) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeRow(mode: SeedMode, onChange: (SeedMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == SeedMode.Constant,
            onClick = { onChange(SeedMode.Constant) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text("Constant") }
        SegmentedButton(
            selected = mode == SeedMode.Random,
            onClick = { onChange(SeedMode.Random) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("Random") }
    }
}

@Composable
private fun DaysSlider(days: Int, onChange: (Int) -> Unit) {
    Column {
        Text("Days: $days")
        Slider(
            value = days.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 1f..35f,
            steps = 33,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LevelChips(level: Int, onChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in 1..4) {
            FilterChip(
                selected = level == l,
                onClick = { onChange(l) },
                label = { Text("Heat $l") },
            )
        }
    }
}

@Composable
private fun CountStepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrement $label")
        }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = { onChange(value + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Increment $label")
        }
    }
}

@Composable
private fun HelperText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDropdown(state: DevToolsState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.activities.firstOrNull { it.id == state.selectedActivityId }

    Box(modifier = Modifier.fillMaxWidth()) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(selected?.name ?: "Pick activity") },
            colors = AssistChipDefaults.assistChipColors(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (activity in state.activities) {
                DropdownMenuItem(
                    text = { Text(activity.name) },
                    onClick = {
                        onSelect(activity.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
