package com.jktdeveloper.habitto.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.BuildConfig
import com.jktdeveloper.habitto.notifications.PermissionUtils
import com.jktdeveloper.habitto.ui.theme.SyncRunningBg
import com.jktdeveloper.habitto.ui.theme.SyncRunningBgDark
import com.jktdeveloper.habitto.ui.theme.SyncRunningFg
import com.jktdeveloper.habitto.ui.theme.SyncRunningFgDark

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isAuthenticated: Boolean,
    accountEmail: String?,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permissionGranted = PermissionUtils.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // ── Permission banner ─────────────────────────────────────────────────
        if (!permissionGranted) {
            PermissionBanner(
                onOpenSettings = { PermissionUtils.openAppNotificationSettings(context) },
            )
        }

        // ── Notifications section ─────────────────────────────────────────────
        SettingsSection(title = "Notifications") {
            val masterParentEnabled = permissionGranted
            SettingsRow(
                title = "All notifications",
                trailing = {
                    Switch(
                        checked = prefs.masterEnabled,
                        enabled = masterParentEnabled,
                        onCheckedChange = viewModel::setMasterEnabled,
                    )
                },
            )

            val parentEnabled = permissionGranted && prefs.masterEnabled

            NotifRow(
                title = "Daily reminder",
                timeMinutes = prefs.dailyReminderMinutes,
                enabled = prefs.dailyReminderEnabled,
                parentEnabled = parentEnabled,
                onToggle = viewModel::setDailyReminderEnabled,
                onTimeChange = viewModel::setDailyReminderMinutes,
            )

            NotifRow(
                title = "Streak at risk",
                timeMinutes = prefs.streakRiskMinutes,
                enabled = prefs.streakRiskEnabled,
                parentEnabled = parentEnabled,
                onToggle = viewModel::setStreakRiskEnabled,
                onTimeChange = viewModel::setStreakRiskMinutes,
            )

            NotifRow(
                title = "Streak frozen alerts",
                timeMinutes = null,
                enabled = prefs.streakFrozenEnabled,
                parentEnabled = parentEnabled,
                onToggle = viewModel::setStreakFrozenEnabled,
                onTimeChange = {},
            )

            NotifRow(
                title = "Streak reset alerts",
                timeMinutes = null,
                enabled = prefs.streakResetEnabled,
                parentEnabled = parentEnabled,
                onToggle = viewModel::setStreakResetEnabled,
                onTimeChange = {},
            )
        }

        // ── Account section ───────────────────────────────────────────────────
        SettingsSection(title = "Account") {
            if (isAuthenticated) {
                SettingsRow(
                    title = accountEmail ?: "Signed in",
                    supporting = if (accountEmail != null) "Signed in" else null,
                    leading = Icons.Default.AccountCircle,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    title = "Sign out",
                    titleColor = MaterialTheme.colorScheme.error,
                    leading = Icons.AutoMirrored.Filled.Logout,
                    leadingColor = MaterialTheme.colorScheme.error,
                    onClick = onSignOut,
                )
            } else {
                SettingsRow(
                    title = "Sign in to sync",
                    supporting = "Local data stays put",
                    leading = Icons.AutoMirrored.Filled.Login,
                    onClick = onSignIn,
                )
            }
        }

        // ── About section ─────────────────────────────────────────────────────
        SettingsSection(title = "About") {
            SettingsRow(
                title = "Version",
                supporting = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
                title = "Privacy policy",
                trailing = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = { /* TODO open privacy URL */ },
            )
        }

        // ── Bottom spacer ─────────────────────────────────────────────────────
        Spacer(Modifier.height(32.dp))
    }
}

// ─── Section primitive ────────────────────────────────────────────────────────

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(content = content)
        }
    }
}

// ─── Row primitive ────────────────────────────────────────────────────────────

@Composable
private fun SettingsRow(
    title: String,
    supporting: String? = null,
    leading: ImageVector? = null,
    leadingColor: Color? = null,
    titleColor: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (leading != null) {
            Icon(
                imageVector = leading,
                contentDescription = null,
                tint = leadingColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = titleColor ?: MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
    }
}

// ─── Permission banner ────────────────────────────────────────────────────────

@Composable
private fun PermissionBanner(onOpenSettings: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val bannerBg = if (isDark) SyncRunningBgDark else SyncRunningBg
    val bannerFg = if (isDark) SyncRunningFgDark else SyncRunningFg

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .clickable(onClick = onOpenSettings),
        color = bannerBg,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = bannerFg,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Notifications blocked. Tap to open system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = bannerFg,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = bannerFg,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─── NotifRow ─────────────────────────────────────────────────────────────────

@Composable
private fun NotifRow(
    title: String,
    timeMinutes: Int?,
    enabled: Boolean,
    parentEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    SettingsRow(
        title = title,
        supporting = if (timeMinutes != null && enabled) formatMinutes(timeMinutes) else null,
        trailing = {
            Switch(checked = enabled, enabled = parentEnabled, onCheckedChange = onToggle)
        },
        onClick = if (parentEnabled && enabled && timeMinutes != null) {
            { showPicker = true }
        } else null,
    )

    if (showPicker && timeMinutes != null) {
        TimePickerDialogStub(
            initialMinutes = timeMinutes,
            onDismiss = { showPicker = false },
            onConfirm = { newMinutes ->
                onTimeChange(newMinutes)
                showPicker = false
            },
        )
    }
}

// ─── Time picker dialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialogStub(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val period = if (h < 12) "AM" else "PM"
    val h12 = ((h + 11) % 12) + 1
    val mm = m.toString().padStart(2, '0')
    return "$h12:$mm $period"
}
