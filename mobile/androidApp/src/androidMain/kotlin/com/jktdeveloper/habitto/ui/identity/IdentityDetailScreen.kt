package com.jktdeveloper.habitto.ui.identity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.Identity
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHeatGrid
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.dashedBorder
import com.jktdeveloper.habitto.ui.components.identityIcon
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.NumeralStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDetailScreen(
    viewModel: IdentityDetailViewModel,
    onBack: () -> Unit,
    onRemoveSuccess: () -> Unit = {},
    onHabitClick: (String) -> Unit = {},
    onAddHabit: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val showRemoveDialog by viewModel.showRemoveDialog.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.removeSuccess.collect { onRemoveSuccess() }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissRemoveDialog,
            title = { Text("Remove identity?") },
            text = { Text("Removing keeps your habits — they stay associated with the identities they support.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRemoveDialog) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
        when (val s = state) {
            IdentityDetailState.Loading -> Box(modifier = Modifier.padding(padding).fillMaxSize())
            IdentityDetailState.NotFound -> Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Identity not found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is IdentityDetailState.Loaded -> Body(s, padding, viewModel, onHabitClick, onAddHabit)
        }
    }
}

@Composable
private fun Body(
    state: IdentityDetailState.Loaded,
    padding: PaddingValues,
    viewModel: IdentityDetailViewModel,
    onHabitClick: (String) -> Unit,
    onAddHabit: () -> Unit,
) {
    val identity = state.identity
    val stats = state.stats
    val hue = IdentityHue.forIdentity(identity)
    val isDark = isSystemInDarkTheme()
    val gradStart = if (isDark) Color.hsl(hue, 0.30f, 0.18f) else Color.hsl(hue, 0.30f, 0.92f)
    val gradEnd = MaterialTheme.colorScheme.surface

    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.linearGradient(colorStops = arrayOf(0f to gradStart, 0.75f to gradEnd)),
                    ),
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 64.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            identity.name,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            identity.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            HeroStat(value = stats.currentStreak, label = "STREAK · DAYS", color = FlameOrange)
                            HeroStat(value = stats.daysActive, label = "TOTAL DAYS")
                            HeroStat(value = stats.habitCount, label = "HABITS")
                        }
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Activity · 90 days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "logged a ${identity.name.lowercase()} habit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(modifier = Modifier.padding(14.dp)) {
                        IdentityHeatGrid(stats.last90Heat, stats.last90States)
                    }
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Habits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
                Text(
                    "What I do because I'm a ${identity.name.lowercase()}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.habits.forEach { habit ->
                        HabitRow(
                            habit = habit,
                            hue = hue,
                            otherIdentities = state.otherIdentitiesByHabit[habit.id].orEmpty(),
                            onClick = { onHabitClick(habit.id) },
                        )
                    }
                    AddHabitRow(onClick = onAddHabit)
                }
            }
        }
        item {
            WhyCard(
                whyText = state.whyText,
                isEditing = state.isEditingWhy,
                pendingDraft = state.pendingWhyDraft.orEmpty(),
                onStartEditing = viewModel::startEditingWhy,
                onUpdateDraft = viewModel::updateWhyDraft,
                onSave = viewModel::saveWhyText,
                onCancel = viewModel::cancelEditingWhy,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            ManageActions(
                isPinned = state.isPinned,
                onTogglePin = viewModel::togglePin,
                onRemove = viewModel::beginRemove,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun WhyCard(
    whyText: String?,
    isEditing: Boolean,
    pendingDraft: String,
    onStartEditing: () -> Unit,
    onUpdateDraft: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Why this identity",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        if (isEditing) {
            OutlinedTextField(
                value = pendingDraft,
                onValueChange = onUpdateDraft,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                placeholder = { Text("Why does this identity matter to you?") },
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onSave) { Text("Save") }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = whyText?.let { "\"$it\"" } ?: "Tap edit to add a reflection.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(16.dp),
                )
            }
            TextButton(
                onClick = onStartEditing,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Edit", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ManageActions(
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onTogglePin,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            Icon(
                if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isPinned) "Unpin from Home" else "Pin to Home")
        }
        TextButton(
            onClick = onRemove,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Remove identity")
        }
        Text(
            "Removing keeps your habits — they stay associated with the identities they support.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HeroStat(value: Int, label: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(value.toString(), style = NumeralStyle.copy(fontSize = 26.sp), color = color)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddHabitRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
                strokeWidth = 1.dp,
                dashLength = 6.dp,
                gapLength = 4.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Add habit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HabitRow(
    habit: Habit,
    hue: Float,
    otherIdentities: List<Identity>,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HabitGlyph(icon = identityIcon(habit.name), hue = hue, size = 36.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleSmall)
                if (otherIdentities.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "Also:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        otherIdentities.forEach { other ->
                            OtherIdentityPill(other)
                        }
                    }
                }
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun OtherIdentityPill(identity: Identity) {
    val isDark = isSystemInDarkTheme()
    val hue = IdentityHue.forIdentity(identity)
    val bg = if (isDark) Color.hsl(hue, 0.30f, 0.20f) else Color.hsl(hue, 0.50f, 0.94f)
    val fg = if (isDark) Color.hsl(hue, 0.30f, 0.85f) else Color.hsl(hue, 0.50f, 0.30f)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 14.dp)
            Text(
                text = identity.name.split(" ").first(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
            )
        }
    }
}
