package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habittracker.data.sync.SyncState
import com.jktdeveloper.habitto.ui.theme.SyncErrorBg
import com.jktdeveloper.habitto.ui.theme.SyncErrorBgDark
import com.jktdeveloper.habitto.ui.theme.SyncErrorFg
import com.jktdeveloper.habitto.ui.theme.SyncErrorFgDark
import com.jktdeveloper.habitto.ui.theme.SyncRunningBg
import com.jktdeveloper.habitto.ui.theme.SyncRunningBgDark
import com.jktdeveloper.habitto.ui.theme.SyncRunningFg
import com.jktdeveloper.habitto.ui.theme.SyncRunningFgDark
import com.jktdeveloper.habitto.ui.theme.SyncSyncedBg
import com.jktdeveloper.habitto.ui.theme.SyncSyncedBgDark
import com.jktdeveloper.habitto.ui.theme.SyncSyncedFg
import com.jktdeveloper.habitto.ui.theme.SyncSyncedFgDark

/**
 * Pill-shaped chip that displays the current sync state.
 *
 * Extracted from HomeScreen.kt (was SyncStatusChip). Renamed to SyncChip to match
 * the JSX SyncChip from /tmp/habitto-design/habitto/project/shared.jsx.
 */
@Composable
fun SyncChip(state: SyncState, onRetry: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val container: androidx.compose.ui.graphics.Color
    val onContainer: androidx.compose.ui.graphics.Color
    val label: String
    val showSpinner: Boolean
    val clickable: Boolean
    val stateIcon: ImageVector

    when (state) {
        is SyncState.Running -> {
            container = if (isDark) SyncRunningBgDark else SyncRunningBg
            onContainer = if (isDark) SyncRunningFgDark else SyncRunningFg
            label = "Syncing"
            showSpinner = true
            clickable = false
            stateIcon = Icons.Outlined.CloudDone // unused when showSpinner=true
        }
        is SyncState.Error -> {
            container = if (isDark) SyncErrorBgDark else SyncErrorBg
            onContainer = if (isDark) SyncErrorFgDark else SyncErrorFg
            label = "Sync failed"
            showSpinner = false
            clickable = true
            stateIcon = Icons.Outlined.CloudOff
        }
        else -> {
            container = if (isDark) SyncSyncedBgDark else SyncSyncedBg
            onContainer = if (isDark) SyncSyncedFgDark else SyncSyncedFg
            label = "Synced"
            showSpinner = false
            clickable = false
            stateIcon = Icons.Outlined.CloudDone
        }
    }

    Surface(
        shape = CircleShape,
        color = container,
        modifier = Modifier
            .height(28.dp)
            .let { if (clickable) it.clickable(onClick = onRetry) else it },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = onContainer,
                )
            } else {
                Icon(
                    imageVector = stateIcon,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                color = onContainer,
            )
        }
    }
}
