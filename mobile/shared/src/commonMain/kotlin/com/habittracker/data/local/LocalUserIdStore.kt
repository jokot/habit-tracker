package com.habittracker.data.local

expect class LocalUserIdStore {
    fun getOrCreate(): String
}
