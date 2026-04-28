package com.jktdeveloper.habitto.ui.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LogoutDialog(
    unsyncedCount: Int,
    onConfirm: (forceWhenUnsynced: Boolean) -> Unit,
    onDismiss: () -> Unit,
    isProcessing: Boolean = false,
) {
    val hasUnsynced = unsyncedCount > 0
    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = { Text(if (hasUnsynced) "Sign out with unsynced data?" else "Sign out?") },
        text = {
            Text(
                if (isProcessing) {
                    "Signing out…"
                } else if (hasUnsynced) {
                    "$unsyncedCount logs haven't synced. They'll be lost on this device if you sign out now."
                } else {
                    "Local data on this device will be cleared. Cloud data stays."
                }
            )
        },
        confirmButton = {
            if (isProcessing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Signing out…")
                }
            } else {
                TextButton(onClick = { onConfirm(hasUnsynced) }) {
                    Text(if (hasUnsynced) "Sign out anyway" else "Sign out")
                }
            }
        },
        dismissButton = {
            if (!isProcessing) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
