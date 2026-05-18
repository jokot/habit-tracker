package com.habittracker.data.local

actual class LocalUserIdStore {
    actual fun getOrCreate(): String =
        throw UnsupportedOperationException("LocalUserIdStore not supported on JVM")
}
