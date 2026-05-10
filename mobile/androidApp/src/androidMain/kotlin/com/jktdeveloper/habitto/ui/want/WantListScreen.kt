package com.jktdeveloper.habitto.ui.want

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                    if (state.hidden.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.showHidden) "Hide hidden"
                                            else "Show hidden (${state.hidden.size})"
                                        )
                                    },
                                    onClick = {
                                        viewModel.toggleShowHidden()
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
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

        val activeCount = state.seeded.size + state.custom.size

        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    text = buildString {
                        append("$activeCount active · ")
                        append("${state.seeded.size} seeded, ${state.custom.size} custom")
                        if (state.showHidden && state.hidden.isNotEmpty()) {
                            append(" · ${state.hidden.size} hidden")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (state.seeded.isNotEmpty()) {
                sectionGroup(
                    title = "Seeded · can be hidden",
                    items = state.seeded,
                    trailing = { activity ->
                        IconButton(onClick = { viewModel.hide(activity.id, activity.name) }) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = "Hide ${activity.name}")
                        }
                    },
                    onTap = onOpenDetail,
                )
            }
            if (state.custom.isNotEmpty()) {
                sectionGroup(
                    title = "Custom · can be edited or deleted",
                    items = state.custom,
                    trailing = { activity ->
                        IconButton(onClick = { onEditWant(activity.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${activity.name}")
                        }
                    },
                    onTap = onOpenDetail,
                )
            }
            if (state.showHidden && state.hidden.isNotEmpty()) {
                sectionGroup(
                    title = "Hidden · ${state.hidden.size}",
                    items = state.hidden,
                    trailing = { activity ->
                        IconButton(onClick = { viewModel.unhide(activity.id) }) {
                            Icon(Icons.Default.Visibility, contentDescription = "Unhide ${activity.name}")
                        }
                    },
                    onTap = onOpenDetail,
                    muted = true,
                )
            }

            item { AddWantTile(onClick = onAddWant) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun LazyListScope.sectionGroup(
    title: String,
    items: List<WantActivity>,
    trailing: @Composable (WantActivity) -> Unit,
    onTap: (String) -> Unit,
    muted: Boolean = false,
) {
    item {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
        )
    }
    item {
        Surface(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Column {
                items.forEachIndexed { index, activity ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    WantRow(
                        activity = activity,
                        onTap = { onTap(activity.id) },
                        trailing = { trailing(activity) },
                        muted = muted,
                    )
                }
            }
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
}

@Composable
private fun WantRow(
    activity: WantActivity,
    onTap: () -> Unit,
    trailing: @Composable () -> Unit,
    muted: Boolean,
) {
    val mutedAlpha = if (muted) 0.62f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                resolveWantIcon(activity.iconKey, activity.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = mutedAlpha),
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    activity.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = mutedAlpha),
                    textDecoration = if (muted) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (activity.isCustom) {
                    Spacer(Modifier.width(6.dp))
                    CustomBadge()
                }
            }
            val costColor =
                if (muted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            Text(
                "${activity.unitsPerPoint} ${activity.unit} = −1 pt",
                style = MaterialTheme.typography.bodySmall,
                color = costColor.copy(alpha = mutedAlpha),
            )
        }
        trailing()
    }
}

@Composable
private fun CustomBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            "CUSTOM",
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AddWantTile(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Add want", style = MaterialTheme.typography.titleSmall)
            Text(
                "Define what you spend points on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

