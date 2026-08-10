package com.jktdeveloper.habitto.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jktdeveloper.habitto.notifications.NotificationCategory
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils
import com.jktdeveloper.habitto.ui.components.SettingsGroup
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.FlameSoft
import com.jktdeveloper.habitto.ui.theme.FlameSoftDark
import com.jktdeveloper.habitto.ui.theme.OnWarnContainer
import com.jktdeveloper.habitto.ui.theme.OnWarnContainerDark
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.jktdeveloper.habitto.ui.theme.Surface1Dark
import com.jktdeveloper.habitto.ui.theme.Surface1Light
import com.jktdeveloper.habitto.ui.theme.WarnContainer
import com.jktdeveloper.habitto.ui.theme.WarnContainerDark

@Composable
fun NotificationsSettingsScreen(viewModel: NotificationsSettingsViewModel, onBack: () -> Unit) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current
    val hasPermission = rememberNotificationPermissionGranted()
    // Nothing under the master switch can fire while Android blocks us or everything
    // is muted, so every category card says so by fading and going inert.
    val inert = !hasPermission || !prefs.masterEnabled

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar ──────────────────────────────────────────────────────────
        // Same plain Row as SettingsScreen: the host already applies the status-bar
        // inset, so a Scaffold + TopAppBar would apply it twice. Outside the scrolling
        // column so it stays put.
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!hasPermission) {
                NotificationBanner(
                    icon = Icons.Default.NotificationsOff,
                    title = "Notifications are blocked",
                    body = "Turn on in system settings to receive these.",
                    container = MaterialTheme.colorScheme.errorContainer,
                    onContainer = MaterialTheme.colorScheme.onErrorContainer,
                    actionLabel = "Open system settings",
                    onAction = { PermissionUtils.openAppNotificationSettings(context) },
                )
            } else if (!prefs.masterEnabled) {
                NotificationBanner(
                    icon = Icons.Default.PauseCircle,
                    title = "Notifications are paused",
                    body = "Turn on the master switch below to enable.",
                    container = if (isSystemInDarkTheme()) WarnContainerDark else WarnContainer,
                    onContainer = if (isSystemInDarkTheme()) OnWarnContainerDark else OnWarnContainer,
                )
            }

            MasterCard(enabled = prefs.masterEnabled, onToggle = viewModel::setMaster)

            for (category in viewModel.categories) {
                val types = viewModel.types.filter { it.category == category }
                if (types.isEmpty()) continue
                SettingsGroup(
                    title = category.displayName,
                    // System is plumbing rather than something you tune, so it reads as
                    // a subgroup under the three user-facing sections.
                    prominent = category != NotificationCategory.SYSTEM,
                    dimmed = inert,
                ) {
                    types.forEachIndexed { index, type ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        NotificationRow(
                            type = type,
                            enabled = prefs.isEnabled(type),
                            minutesOfDay = prefs.minutesOfDay(type),
                            controlsEnabled = !inert,
                            onToggle = { viewModel.setTypeEnabled(type, it) },
                            onMinutes = { viewModel.setTypeMinutes(type, it) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xxxl))
        }
    }
}

/** The one switch that overrides all the others. */
@Composable
private fun MasterCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val isDark = isSystemInDarkTheme()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xl),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = Icons.Default.Notifications,
                tint = if (isDark) FlameOrangeDark else FlameOrange,
                background = if (isDark) FlameSoftDark else FlameSoft,
                size = 40.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.size(Spacing.lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "All notifications",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Master switch · disables every category",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

/**
 * One notification type: tinted icon, name, optional firing time, switch.
 * The time chip shows only for types that have a time *and* are on — a schedule you
 * can't receive is noise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationRow(
    type: NotificationTypeId,
    enabled: Boolean,
    minutesOfDay: Int?,
    controlsEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onMinutes: (Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    val pickerState = remember(minutesOfDay) {
        TimePickerState(
            initialHour = (minutesOfDay ?: 0) / 60,
            initialMinute = (minutesOfDay ?: 0) % 60,
            is24Hour = true,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(
            icon = type.uiIcon(enabled),
            tint = type.category.accent(),
            background = if (isSystemInDarkTheme()) Surface1Dark else Surface1Light,
        )
        Spacer(Modifier.size(Spacing.lg))
        Text(
            text = type.uiLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (type.hasTime && enabled && minutesOfDay != null) {
            TimeChip(
                minutesOfDay = minutesOfDay,
                enabled = controlsEnabled,
                onClick = { showTimePicker = true },
            )
            Spacer(Modifier.size(Spacing.md))
        }
        Switch(checked = enabled, onCheckedChange = onToggle, enabled = controlsEnabled)
    }
    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onMinutes(pickerState.hour * 60 + pickerState.minute)
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@Composable
private fun TimeChip(minutesOfDay: Int, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSystemInDarkTheme()) Surface1Dark else Surface1Light,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            text = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
        )
    }
}

@Composable
private fun IconTile(
    icon: ImageVector,
    tint: Color,
    background: Color,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Blocked-by-Android and paused-by-you say the same shape of thing. */
@Composable
private fun NotificationBanner(
    icon: ImageVector,
    title: String,
    body: String,
    container: Color,
    onContainer: Color,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.xl),
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = Spacing.lg)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(text = body, fontSize = 12.sp, lineHeight = 16.sp, color = onContainer)
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onAction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = onContainer,
                            contentColor = container,
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
