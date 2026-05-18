package com.habittracker.data.local

actual class LocalUserIdStore {
    actual fun getOrCreate(): String = "jvm-test-user"
}
