package com.jktdeveloper.habitto.ui.want

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habittracker.domain.model.WantActivity
import com.jktdeveloper.habitto.ui.components.resolveWantIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WantListScreen(
    viewModel: WantListViewModel,
    onBack: () -> Unit,
    onAddWant: () -> Unit,
    onEditWant: (id: String) -> Unit,
    onOpenDetail: (id: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

    val toast = state.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wants", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.showHidden) "Hide hidden" else "Show hidden (${state.hidden.size})") },
                                onClick = {
                                    viewModel.toggleShowHidden()
                                    menuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add want") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onAddWant,
            )
        },
    ) { padding ->
        if (state.seeded.isEmpty() && state.custom.isEmpty() && state.hidden.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No wants yet — tap + to add one",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.seeded.isNotEmpty()) {
                item { SectionHeader("Seeded · ${state.seeded.size}") }
                items(state.seeded, key = { it.id }) { activity ->
                    WantRow(
                        activity = activity,
                        trailing = {
                            IconButton(onClick = { viewModel.hide(activity.id, activity.name) }) {
                                Icon(Icons.Default.VisibilityOff,
                                     contentDescription = "Hide ${activity.name}")
                            }
                        },
                        onTap = { onOpenDetail(activity.id) },
                    )
                }
            }
            if (state.custom.isNotEmpty()) {
                item { SectionHeader("Custom · ${state.custom.size}") }
                items(state.custom, key = { it.id }) { activity ->
                    WantRow(
                        activity = activity,
                        trailing = {
                            IconButton(onClick = { onEditWant(activity.id) }) {
                                Icon(Icons.Default.Edit,
                                     contentDescription = "Edit ${activity.name}")
                            }
                        },
                        onTap = { onOpenDetail(activity.id) },
                    )
                }
            }
            if (state.showHidden && state.hidden.isNotEmpty()) {
                item { SectionHeader("Hidden · ${state.hidden.size}") }
                items(state.hidden, key = { it.id }) { activity ->
                    WantRow(
                        activity = activity,
                        trailing = {
                            IconButton(onClick = { viewModel.unhide(activity.id) }) {
                                Icon(Icons.Default.Visibility,
                                     contentDescription = "Unhide ${activity.name}")
                            }
                        },
                        onTap = { onOpenDetail(activity.id) },
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun WantRow(
    activity: WantActivity,
    trailing: @Composable () -> Unit,
    onTap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                resolveWantIcon(activity.iconKey, activity.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(activity.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "−${activity.costPerUnit} pt / ${activity.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        trailing()
    }
}
