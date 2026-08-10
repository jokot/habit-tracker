package com.jktdeveloper.habitto.ui.onboarding

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.jktdeveloper.habitto.notifications.PermissionUtils
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark
import com.jktdeveloper.habitto.ui.theme.FlameSoft
import com.jktdeveloper.habitto.ui.theme.FlameSoftDark
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.jktdeveloper.habitto.ui.theme.Surface1Dark
import com.jktdeveloper.habitto.ui.theme.Surface1Light
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.permissionPromptStore: DataStore<Preferences> by preferencesDataStore("notif_prompt")
private val KEY_SKIPPED = booleanPreferencesKey("permission_prompt_skipped")

@OptIn(ExperimentalMaterial3Api::class)
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
    if (!visible) return

    val isDark = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        // Swiping away without choosing leaves the prompt for next launch — only
        // "Maybe later" writes the skip flag.
        onDismissRequest = { visible = false },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isDark) FlameSoftDark else FlameSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (isDark) FlameOrangeDark else FlameOrange,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = "Get the most out of Habitto",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "Three small nudges. You can change any of them later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xxl))
            BenefitRow(
                Icons.Default.NotificationsActive,
                "Daily nudge",
                "One reminder at a time you pick.",
            )
            BenefitRow(
                Icons.Default.WarningAmber,
                "Late-day rescue",
                "A heads-up before a streak breaks.",
            )
            BenefitRow(
                Icons.Default.EmojiEvents,
                "Milestones",
                "The moments worth noticing.",
            )
            Spacer(Modifier.height(Spacing.xxl))
            Button(
                onClick = { launcher.launch(PermissionUtils.PERMISSION_NAME) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("Allow notifications", fontWeight = FontWeight.SemiBold)
            }
            TextButton(
                onClick = {
                    scope.launch {
                        context.permissionPromptStore.edit { it[KEY_SKIPPED] = true }
                    }
                    visible = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text("Maybe later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun BenefitRow(icon: ImageVector, title: String, body: String) {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Surface1Dark else Surface1Light),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isDark) FlameOrangeDark else FlameOrange,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(Spacing.lg))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
