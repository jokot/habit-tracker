package com.jktdeveloper.habitto.ui.identity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.habittracker.domain.model.IdentityWithStats
import com.jktdeveloper.habitto.ui.components.HabitGlyph
import com.jktdeveloper.habitto.ui.components.IdentityHue
import com.jktdeveloper.habitto.ui.components.IdentitySparkline
import com.jktdeveloper.habitto.ui.components.dashedBorder
import com.jktdeveloper.habitto.ui.components.identityIcon
import com.jktdeveloper.habitto.ui.theme.FlameOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityListScreen(
    viewModel: IdentityListViewModel,
    onBack: () -> Unit,
    onIdentityClick: (String) -> Unit,
    onAddIdentityClick: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identities", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
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
        val loaded = state as? IdentityListState.Loaded
        val items = loaded?.items.orEmpty()
        val pinnedIdentityId = loaded?.pinnedIdentityId
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = "You're committing to ${items.size} ${if (items.size == 1) "identity" else "identities"}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
            items(items, key = { it.identity.id }) { iws ->
                IdentityCard(
                    iws = iws,
                    isPinned = iws.identity.id == pinnedIdentityId,
                    onClick = { onIdentityClick(iws.identity.id) },
                )
                Spacer(Modifier.height(10.dp))
            }
            item {
                AddIdentityCtaCard(onClick = onAddIdentityClick)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun IdentityCard(iws: IdentityWithStats, isPinned: Boolean, onClick: () -> Unit) {
    val identity = iws.identity
    val stats = iws.stats
    val hue = IdentityHue.forIdentityId(identity.name.lowercase())
    val isDark = isSystemInDarkTheme()
    val gradStart = if (isDark) Color.hsl(hue, 0.30f, 0.18f) else Color.hsl(hue, 0.30f, 0.92f)
    val gradEnd = MaterialTheme.colorScheme.surface
    val hueTint = Color.hsl(hue, 0.60f, if (isDark) 0.65f else 0.40f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isPinned) hueTint else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(colorStops = arrayOf(0f to gradStart, 0.75f to gradEnd)),
            ),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                HabitGlyph(icon = identityIcon(identity.name), hue = hue, size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(identity.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = hueTint,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Text(
                        identity.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatsRow(stats.currentStreak, stats.habitCount, stats.daysActive)
                    Spacer(Modifier.height(12.dp))
                    IdentitySparkline(stats.last14Heat, stats.last14States)
                }
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AddIdentityCtaCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .dashedBorder(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
                strokeWidth = 2.dp,
                dashLength = 6.dp,
                gapLength = 4.dp,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = "Add identity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pick from suggestions or define your own.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(streak: Int, habitCount: Int, daysActive: Int) {
    val divider = MaterialTheme.colorScheme.outlineVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(14.dp))
            Text(streak.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("day streak", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.width(1.dp).height(12.dp).background(divider))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(habitCount.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(if (habitCount == 1) "habit" else "habits", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.width(1.dp).height(12.dp).background(divider))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(daysActive.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("days as", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
