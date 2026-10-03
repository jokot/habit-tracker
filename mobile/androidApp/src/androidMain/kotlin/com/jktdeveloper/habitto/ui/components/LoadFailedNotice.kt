package com.jktdeveloper.habitto.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jktdeveloper.habitto.ui.theme.OnWarnContainer
import com.jktdeveloper.habitto.ui.theme.OnWarnContainerDark
import com.jktdeveloper.habitto.ui.theme.Spacing
import com.jktdeveloper.habitto.ui.theme.WarnContainer
import com.jktdeveloper.habitto.ui.theme.WarnContainerDark

/** Shown while a sync has failed and a screen still has skeletons. */
@Composable
fun LoadFailedNotice(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = "Couldn't finish loading. Pull down to try again.",
) {
    val dark = isSystemInDarkTheme()
    val onContainer = if (dark) OnWarnContainerDark else OnWarnContainer
    Row(
        modifier = modifier
            .padding(horizontal = Spacing.xl)
            .padding(bottom = Spacing.md)
            .fillMaxWidth()
            .background(
                if (dark) WarnContainerDark else WarnContainer,
                RoundedCornerShape(14.dp),
            )
            .padding(start = Spacing.lg, end = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.CloudOff,
            contentDescription = null,
            tint = onContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = onContainer,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("Retry", color = onContainer) }
    }
}
