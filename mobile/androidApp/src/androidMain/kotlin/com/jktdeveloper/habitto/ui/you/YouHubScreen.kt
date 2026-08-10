package com.jktdeveloper.habitto.ui.you

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktdeveloper.habitto.ui.components.IdentityHubCard
import com.jktdeveloper.habitto.ui.components.SettingsGroup
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.jktdeveloper.habitto.ui.theme.Surface1Dark
import com.jktdeveloper.habitto.ui.theme.Surface1Light

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouHubScreen(
    viewModel: YouHubViewModel,
    onOpenSettings: () -> Unit,
    onOpenIdentities: () -> Unit,
    onHabitsClick: () -> Unit,
    onOpenExchangeRate: () -> Unit,
    onOpenWants: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "You",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                val identities by viewModel.userIdentities.collectAsState()
                val pinnedName by viewModel.pinnedIdentityName.collectAsState()
                IdentityHubCard(
                    identities = identities,
                    pinnedIdentityName = pinnedName,
                    onClick = onOpenIdentities,
                )
                Spacer(Modifier.height(Spacing.md))
            }
            item {
                val habitCount by viewModel.habitCount.collectAsState()
                val wantCount by viewModel.wantCount.collectAsState()
                SettingsGroup("Track") {
                    HubRow(
                        icon = Icons.Outlined.TaskAlt,
                        title = "Habits",
                        subtitle = "$habitCount active",
                        onClick = onHabitsClick,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    HubRow(
                        icon = Icons.Outlined.ShoppingBag,
                        title = "Wants",
                        subtitle = "$wantCount active · spending points",
                        onClick = onOpenWants,
                    )
                }
            }
            item {
                val rate by viewModel.currentRate.collectAsState()
                val streak by viewModel.currentStreak.collectAsState()
                val rateLabel = ((rate * 10).toInt() / 10.0).toString()
                SettingsGroup("Earn & spend") {
                    HubRow(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        title = "Point exchange rate",
                        subtitle = "${rateLabel}× · earned by ${streak}-day streak",
                        accent = if (isSystemInDarkTheme()) FlameOrangeDark else FlameOrange,
                        onClick = onOpenExchangeRate,
                    )
                }
            }
            item {
                SettingsGroup("Account") {
                    HubRow(
                        icon = Icons.Outlined.Settings,
                        title = "Settings",
                        subtitle = "Notifications, sync, data",
                        onClick = onOpenSettings,
                    )
                }
            }
        }
    }
}

/**
 * One navigation row: rounded icon tile, title over a short subtitle, chevron.
 * [accent] tints the icon when the row is worth calling out.
 */
@Composable
private fun HubRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isSystemInDarkTheme()) Surface1Dark else Surface1Light),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
