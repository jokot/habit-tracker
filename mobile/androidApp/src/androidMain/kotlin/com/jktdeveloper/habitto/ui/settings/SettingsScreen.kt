package com.jktdeveloper.habitto.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
            // Notifications section
            item { SectionHeader("Notifications") }
            if (!permissionGranted) {
                item {
                    PermissionBanner(
                        onRequestPermission = { permLauncher.launch(PermissionUtils.PERMISSION_NAME) },
                        onOpenSettings = {
                            val intent = PermissionUtils.appNotificationSettingsIntent(context.packageName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                    )
                }
            }
            item {
                SwitchRow(
                    title = "Daily reminder",
                    supporting = "Reminds you each day to log habits",
                    checked = prefs.dailyReminderEnabled,
                    enabled = permissionGranted,
                    timeMinutes = prefs.dailyReminderMinutes,
                    onCheckedChange = viewModel::setDailyReminderEnabled,
                    onTimeChange = viewModel::setDailyReminderMinutes,
                )
            }
            item {
                SwitchRow(
                    title = "Streak at risk",
                    supporting = "Late-day nudge if your streak is about to break",
                    checked = prefs.streakRiskEnabled,
                    enabled = permissionGranted,
                    timeMinutes = prefs.streakRiskMinutes,
                    onCheckedChange = viewModel::setStreakRiskEnabled,
                    onTimeChange = viewModel::setStreakRiskMinutes,
                )
            }
            item {
                SwitchRow(
                    title = "Streak frozen alerts",
                    supporting = "Notify when one missed day used your freeze",
                    checked = prefs.streakFrozenEnabled,
                    enabled = permissionGranted,
                    timeMinutes = null,
                    onCheckedChange = viewModel::setStreakFrozenEnabled,
                    onTimeChange = {},
                )
            }
            item {
                SwitchRow(
                    title = "Streak reset alerts",
                    supporting = "Notify when your streak resets to zero",
                    checked = prefs.streakResetEnabled,
                    enabled = permissionGranted,
                    timeMinutes = null,
                    onCheckedChange = viewModel::setStreakResetEnabled,
                    onTimeChange = {},
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }

            // Account section
            item { SectionHeader("Account") }
            item {
                AccountSection(
                    isAuthenticated = isAuthenticated,
                    email = accountEmail,
                    onSignOut = onSignOut,
                    onSignIn = onSignIn,
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline) }

            // About section
            item { SectionHeader("About") }
            item {
                AboutSection(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
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
private fun PermissionBanner(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md).fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Notifications are blocked.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row {
                    TextButton(onClick = onRequestPermission) { Text("Allow") }
                    TextButton(onClick = onOpenSettings) { Text("System settings") }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    timeMinutes: Int?,
    onCheckedChange: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
) {
    var showTimePicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (timeMinutes != null && enabled && checked) {
                TextButton(onClick = { showTimePicker = true }) {
                    Text(formatMinutes(timeMinutes))
                }
            }
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }

    if (showTimePicker && timeMinutes != null) {
        TimePickerDialogStub(
            initialMinutes = timeMinutes,
            onDismiss = { showTimePicker = false },
            onConfirm = { newMinutes ->
                onTimeChange(newMinutes)
                showTimePicker = false
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

@Composable
private fun AccountSection(
    isAuthenticated: Boolean,
    email: String?,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.xl)) {
        if (isAuthenticated) {
            Text(email ?: "Signed in", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.xl))
            TextButton(
                onClick = onSignOut,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Sign out") }
        } else {
            Text("Sign in to sync across devices.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(Spacing.md))
            TextButton(onClick = onSignIn) { Text("Sign in") }
        }
    }
}

@Composable
private fun AboutSection(versionName: String, versionCode: Int) {
    Column(modifier = Modifier.padding(Spacing.xl)) {
        Text("Version $versionName ($versionCode)", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    val period = if (h < 12) "AM" else "PM"
    val h12 = ((h + 11) % 12) + 1
    val mm = m.toString().padStart(2, '0')
    return "$h12:$mm $period"
}
