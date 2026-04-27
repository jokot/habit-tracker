package com.jktdeveloper.habitto.notifications

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {
    /** Returns true when POST_NOTIFICATIONS is granted, or unnecessary on this device (< Android 13). */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** System-settings intent for the app's notification screen. Caller adds FLAG_ACTIVITY_NEW_TASK. */
    fun appNotificationSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            data = Uri.parse("package:$packageName")
        }

    const val PERMISSION_NAME = Manifest.permission.POST_NOTIFICATIONS
}
