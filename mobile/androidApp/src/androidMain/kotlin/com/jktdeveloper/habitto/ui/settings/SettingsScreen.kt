package com.jktdeveloper.habitto.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
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
import com.jktdeveloper.habitto.ui.components.SettingsGroup
import com.jktdeveloper.habitto.ui.theme.FlameOrange
import com.jktdeveloper.habitto.ui.theme.FlameOrangeDark

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isAuthenticated: Boolean,
    accountEmail: String?,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    onOpenNotificationsSettings: (() -> Unit)? = null,
    onOpenDevTools: (() -> Unit)? = null,
) {
    val notificationSummary by viewModel.notificationSummary.collectAsState()
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }

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

        // ── Notifications section ─────────────────────────────────────────────
        // All per-type detail lives in NotificationsSettingsScreen; this row is the
        // only entry point, so the two can't drift.
        SettingsGroup(title = "Notifications") {
            SettingsRow(
                title = "Notifications",
                supporting = if (permissionGranted) notificationSummary else "Blocked by system",
                leading = Icons.Default.Notifications,
                leadingColor = if (isSystemInDarkTheme()) FlameOrangeDark else FlameOrange,
                trailing = {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    if (permissionGranted) {
                        onOpenNotificationsSettings?.invoke()
                    } else {
                        PermissionUtils.openAppNotificationSettings(context)
                    }
                },
            )
        }

        // ── Account section ───────────────────────────────────────────────────
        SettingsGroup(title = "Account") {
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

        // ── Developer section (debug only) ────────────────────────────────────
        if (onOpenDevTools != null) {
            SettingsGroup(title = "Developer") {
                SettingsRow(
                    title = "Dev tools",
                    supporting = "Seed test data — debug builds only",
                    leading = Icons.Default.Build,
                    onClick = onOpenDevTools,
                )
            }
        }

        // ── About section ─────────────────────────────────────────────────────
        SettingsGroup(title = "About") {
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

