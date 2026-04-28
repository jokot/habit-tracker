package com.jktdeveloper.habitto.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LogoutDialog(
    unsyncedCount: Int,
    onConfirm: (forceWhenUnsynced: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasUnsynced = unsyncedCount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasUnsynced) "Sign out with unsynced data?" else "Sign out?") },
        text = {
            Text(
                if (hasUnsynced) {
                    "$unsyncedCount logs haven't synced. They'll be lost on this device if you sign out now."
                } else {
                    "Local data on this device will be cleared. Cloud data stays."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hasUnsynced) }) {
                Text(if (hasUnsynced) "Sign out anyway" else "Sign out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
