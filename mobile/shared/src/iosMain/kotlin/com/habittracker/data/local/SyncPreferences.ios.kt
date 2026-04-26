package com.habittracker.data.local

import platform.Foundation.NSUserDefaults

actual class SyncPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getLong(key: String): Long = defaults.integerForKey(key)
    actual fun putLong(key: String, value: Long) {
        defaults.setInteger(value, key)
    }
}
