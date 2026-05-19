package com.jktdeveloper.habitto.ui.onboarding

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.jktdeveloper.habitto.notifications.PermissionUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.permissionPromptStore: DataStore<Preferences> by preferencesDataStore("notif_prompt")
private val KEY_SKIPPED = booleanPreferencesKey("permission_prompt_skipped")

@Composable
fun NotificationPermissionPromptHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (PermissionUtils.hasNotificationPermission(context)) return@LaunchedEffect
        val skipped = context.permissionPromptStore.data.first()[KEY_SKIPPED] ?: false
        if (!skipped) visible = true
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { visible = false },
    )
    if (visible) {
        AlertDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = { Text("Stay on top of your habits") },
            text = {
                Text("Enable notifications for daily reminders, streak alerts, and timer completions.")
            },
            confirmButton = {
                TextButton(onClick = { launcher.launch(PermissionUtils.PERMISSION_NAME) }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        context.permissionPromptStore.edit { it[KEY_SKIPPED] = true }
                    }
                    visible = false
                }) { Text("Skip") }
            },
        )
    }
}
