package com.jktdeveloper.habitto.ui.habit

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.habitIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitListScreen(
    viewModel: HabitListViewModel,
    onBack: () -> Unit,
    onHabitClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Habits",
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
        when (val s = state) {
            HabitListState.Loading -> {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            is HabitListState.Loaded -> {
                if (s.habits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "No habits yet.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Add some via Identities.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        itemsIndexed(s.habits, key = { _, it -> it.habit.id }) { index, row ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 1.dp,
                                )
                            }
                            HabitRow(row = row, onClick = { onHabitClick(row.habit.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRow(row: HabitRowItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        val firstIdentityName = row.identityNames.firstOrNull()
        val hue = if (firstIdentityName != null)
            IdentityHue.forIdentityId(firstIdentityName.lowercase())
        else 0f
        HabitGlyph(
            icon = habitIcon(row.habit.name),
            hue = hue,
            size = 40.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.habit.name,
                style = MaterialTheme.typography.titleSmall,
            )
            val subtitle = buildString {
                append(row.identityNames.joinToString(" · ").ifBlank { "Unlinked" })
                append(" · target ")
                append(row.habit.dailyTarget)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
