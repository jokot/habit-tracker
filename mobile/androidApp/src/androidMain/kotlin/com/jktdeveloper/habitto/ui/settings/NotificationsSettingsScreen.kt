package com.jktdeveloper.habitto.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PermissionUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(viewModel: NotificationsSettingsViewModel, onBack: () -> Unit) {
    val prefs by viewModel.prefs.collectAsState()
    val context = LocalContext.current
    val hasPermission = remember(prefs) { PermissionUtils.hasNotificationPermission(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!hasPermission) {
                BannerCard(
                    text = "Notifications blocked by Android.",
                    actionLabel = "Open system Settings",
                    onAction = { PermissionUtils.openAppNotificationSettings(context) },
                )
            }

            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Text("All notifications", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Switch(checked = prefs.masterEnabled, onCheckedChange = { viewModel.setMaster(it) })
                }
            }

            if (!prefs.masterEnabled) {
                Text(
                    "Notifications muted",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (cat in viewModel.categories) {
                Spacer(Modifier.height(12.dp))
                Text(
                    cat.displayName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                )
                for (t in viewModel.types.filter { it.category == cat }) {
                    TypeRow(
                        type = t,
                        enabled = prefs.isEnabled(t),
                        minutesOfDay = prefs.minutesOfDay(t),
                        masterEnabled = prefs.masterEnabled,
                        onToggle = { viewModel.setTypeEnabled(t, it) },
                        onMinutes = { viewModel.setTypeMinutes(t, it) },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BannerCard(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp),
        ) {
            Text(text, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeRow(
    type: NotificationTypeId,
    enabled: Boolean,
    minutesOfDay: Int?,
    masterEnabled: Boolean,
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
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(type.key.replace('_', ' ').replaceFirstChar { it.uppercase() })
                if (type.hasTime && minutesOfDay != null) {
                    TextButton(onClick = { showTimePicker = true }, enabled = enabled && masterEnabled) {
                        Text("at ${"%02d".format(minutesOfDay / 60)}:${"%02d".format(minutesOfDay % 60)}")
                    }
                }
            }
            Switch(checked = enabled, onCheckedChange = onToggle, enabled = masterEnabled)
        }
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
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = pickerState) },
        )
    }
}
