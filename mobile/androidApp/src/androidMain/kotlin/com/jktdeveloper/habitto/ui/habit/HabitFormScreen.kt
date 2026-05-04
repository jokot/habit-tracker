package com.jktdeveloper.habitto.ui.habit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Identity
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.habitIcon
import com.jktdeveloper.habitto.ui.components.identityIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFormScreen(
    viewModel: HabitFormViewModel,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.saveSuccess.collect { onSaved() }
    }
    LaunchedEffect(viewModel) {
        viewModel.deleteSuccess.collect { onDeleted() }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete habit?") },
            text = { Text("Past activity stays in your history. Future days will exclude it.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.mode == HabitFormMode.Edit) "Edit habit" else "New habit",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = state.canSave,
                    ) {
                        Text(
                            "Save",
                            color = if (state.canSave) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item { NameRow(state, viewModel::onNameChange) }
            item { IdentitiesSection(state, viewModel::onIdentitiesChange) }
            item { GoalSection(state, viewModel::onThresholdChange, viewModel::onTargetChange, viewModel::onUnitChange) }
            if (state.mode == HabitFormMode.Edit) {
                item { DeleteRow(onClick = { showDeleteDialog = true }) }
            }
            state.error?.let { item { ErrorBanner(it) } }
        }
    }
}

@Composable
private fun NameRow(state: HabitFormState, onNameChange: (String) -> Unit) {
    val firstHueId = state.selectedIdentityIds.firstOrNull()?.let { id ->
        state.availableIdentities.firstOrNull { it.id == id }?.name?.lowercase()
    }
    val hue = if (firstHueId != null) IdentityHue.forIdentityId(firstHueId) else 0f
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HabitGlyph(icon = habitIcon(state.name.ifBlank { "default" }), hue = hue, size = 56.dp)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Name", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                placeholder = { Text("e.g. Walk outside") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IdentitiesSection(state: HabitFormState, onChange: (Set<String>) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("Identities", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "at least 1 required",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Who you become by doing this. Affects all selected identities' streaks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        val selected = state.availableIdentities.filter { it.id in state.selectedIdentityIds }
        val unselected = state.availableIdentities.filter { it.id !in state.selectedIdentityIds }

        // Selected chips
        FlowChips {
            selected.forEach { id ->
                SelectedIdentityPill(id, onRemove = { onChange(state.selectedIdentityIds - id.id) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (unselected.isNotEmpty()) {
            Text(
                "Suggested",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            FlowChips {
                unselected.forEach { id ->
                    SuggestionPill(id, onAdd = { onChange(state.selectedIdentityIds + id.id) })
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun SelectedIdentityPill(identity: Identity, onRemove: () -> Unit) {
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    val bg = Color.hsl(hue, 0.50f, 0.94f)
    val fg = Color.hsl(hue, 0.50f, 0.30f)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 24.dp)
            Text(identity.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp), tint = fg)
            }
        }
    }
}

@Composable
private fun SuggestionPill(identity: Identity, onAdd: () -> Unit) {
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    Surface(
        onClick = onAdd,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 20.dp)
            Text("+ ${identity.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GoalSection(
    state: HabitFormState,
    onThreshold: (Double) -> Unit,
    onTarget: (Int) -> Unit,
    onUnit: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text("Goal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = if (state.threshold == state.threshold.toLong().toDouble())
                    state.threshold.toLong().toString() else state.threshold.toString(),
                onValueChange = { v -> v.toDoubleOrNull()?.let(onThreshold) },
                label = { Text("1 point per") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.target.toString(),
                onValueChange = { v -> v.toIntOrNull()?.let(onTarget) },
                label = { Text("Daily target") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.unit,
            onValueChange = onUnit,
            label = { Text("Unit (e.g. min, reps, pages)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeleteRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Delete habit")
        }
    }
}

@Composable
private fun ErrorBanner(msg: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            msg,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}
