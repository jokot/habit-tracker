package com.jktdeveloper.habitto.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jktdeveloper.habitto.notifications.PermissionUtils

/**
 * Whether POST_NOTIFICATIONS is granted, re-checked on every resume.
 *
 * The grant is made outside the app — in system settings — so nothing in Compose
 * invalidates when it changes. Only ON_RESUME tells us to look again.
 */
@Composable
fun rememberNotificationPermissionGranted(): Boolean {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = PermissionUtils.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}
