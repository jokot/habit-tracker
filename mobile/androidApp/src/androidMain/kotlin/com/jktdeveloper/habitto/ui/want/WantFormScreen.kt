package com.jktdeveloper.habitto.ui.want

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.components.WantIconPicker
import com.jktdeveloper.habitto.ui.components.wantIconForKey

private val UNITS = listOf(
    "minutes", "servings", "match", "matches", "episode", "session",
    "item", "drinks", "cups", "pieces", "meals",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WantFormScreen(
    viewModel: WantFormViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var iconPickerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.mode is FormMode.Edit) "Edit want" else "New want",
                         fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save({}) }, enabled = !state.isSaving) {
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = { iconPickerOpen = true }) {
                        Icon(wantIconForKey(state.iconKey), contentDescription = "Pick icon")
                    }
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Unit", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UNITS.forEach { unit ->
                    FilterChip(
                        selected = unit == state.unit,
                        onClick = { viewModel.onUnit(unit) },
                        label = { Text(unit) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Cost", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            CostStepperRow(
                value = state.costInput,
                onChange = viewModel::onCostInput,
                unit = state.unit,
            )

            if (state.showCostEditWarning) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Editing this cost rewrites your spend history.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            state.validationError?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            val mode = state.mode
            if (mode is FormMode.Edit) {
                TextButton(onClick = { viewModel.delete({}) }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null,
                         tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text("Delete want", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (iconPickerOpen) {
        WantIconPicker(
            selected = state.iconKey,
            onPick = viewModel::onIconKey,
            onDismiss = { iconPickerOpen = false },
        )
    }
}

@Composable
private fun CostStepperRow(value: String, onChange: (String) -> Unit, unit: String) {
    val parsed = value.toDoubleOrNull() ?: 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            label = { Text("Cost (pt / $unit)") },
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { onChange(((parsed - 0.1).coerceAtLeast(0.0)).toRoundedString()) }) {
            Icon(Icons.Default.Remove, contentDescription = "Decrement")
        }
        IconButton(onClick = { onChange(((parsed + 0.1)).toRoundedString()) }) {
            Icon(Icons.Default.Add, contentDescription = "Increment")
        }
    }
    val previewPts = if (parsed > 0.0) {
        kotlin.math.ceil(parsed * 30).toInt().coerceAtLeast(1)
    } else 0
    Text(
        "(e.g. 30 $unit = $previewPts pt)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun Double.toRoundedString(): String {
    val rounded = ((this * 10).toInt()) / 10.0
    return rounded.toString()
}
