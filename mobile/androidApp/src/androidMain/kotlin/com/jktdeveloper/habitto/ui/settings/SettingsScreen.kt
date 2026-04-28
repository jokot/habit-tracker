package com.jktdeveloper.habitto.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.BuildConfig
import com.jktdeveloper.habitto.notifications.NotificationPrefs
import com.jktdeveloper.habitto.notifications.PermissionUtils
import com.jktdeveloper.habitto.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(Unit) {
        // Re-check on every entrance/resume.
        permissionGranted = PermissionUtils.hasNotificationPermission(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // Permission banner — above the Notifications section header
            if (!permissionGranted) {
                item {
                    PermissionBanner(
                        onOpenSettings = {
                            val intent = PermissionUtils.appNotificationSettingsIntent(context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                    )
                }
            }

            // Notifications section
            item { SectionHeader("Notifications") }
            item {
                NotifRow(
                    title = "Daily reminder",
                    timeMinutes = prefs.dailyReminderMinutes,
                    enabled = prefs.dailyReminderEnabled,
                    permissionGranted = permissionGranted,
                    onToggle = viewModel::setDailyReminderEnabled,
                    onTimeChange = viewModel::setDailyReminderMinutes,
                )
            }
            item {
                NotifRow(
                    title = "Streak at risk",
                    timeMinutes = prefs.streakRiskMinutes,
                    enabled = prefs.streakRiskEnabled,
                    permissionGranted = permissionGranted,
                    onToggle = viewModel::setStreakRiskEnabled,
                    onTimeChange = viewModel::setStreakRiskMinutes,
                )
            }
            item {
                NotifRow(
                    title = "Streak frozen alerts",
                    timeMinutes = null,
                    enabled = prefs.streakFrozenEnabled,
                    permissionGranted = permissionGranted,
                    onToggle = viewModel::setStreakFrozenEnabled,
                    onTimeChange = {},
                )
            }
            item {
                NotifRow(
                    title = "Streak reset alerts",
                    timeMinutes = null,
                    enabled = prefs.streakResetEnabled,
                    permissionGranted = permissionGranted,
                    onToggle = viewModel::setStreakResetEnabled,
                    onTimeChange = {},
                )
            }

            // Account section
            item { SectionHeader("Account") }
            if (isAuthenticated) {
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                accountEmail ?: "Signed in",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        },
                    )
                }
                item {
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onSignOut() },
                        headlineContent = {
                            Text("Sign out", color = MaterialTheme.colorScheme.error)
                        },
                    )
                }
            } else {
                item {
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clickable { onSignIn() },
                        headlineContent = { Text("Sign in to sync") },
                    )
                }
            }

            // About section
            item { SectionHeader("About") }
            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { /* TODO open privacy URL */ },
                    headlineContent = { Text("Privacy policy") },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = Spacing.xl, top = Spacing.xxl, bottom = Spacing.md),
    )
}

@Composable
private fun PermissionBanner(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                "Notifications are blocked",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

@Composable
private fun NotifRow(
    title: String,
    timeMinutes: Int?,
    enabled: Boolean,
    permissionGranted: Boolean,
    onToggle: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val canEdit = permissionGranted
    val rowMod = if (timeMinutes != null && enabled && canEdit)
        Modifier.fillMaxWidth().clickable { showPicker = true }
    else
        Modifier.fillMaxWidth()

    ListItem(
        modifier = rowMod,
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = if (timeMinutes != null && enabled) {
            { Text(formatMinutes(timeMinutes), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else null,
        trailingContent = {
            Switch(checked = enabled, enabled = canEdit, onCheckedChange = onToggle)
        },
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

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val period = if (h < 12) "AM" else "PM"
    val h12 = ((h + 11) % 12) + 1
    val mm = m.toString().padStart(2, '0')
    return "$h12:$mm $period"
}
