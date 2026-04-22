package com.habittracker.domain

import com.habittracker.data.local.LocalUserIdStore
import com.habittracker.data.repository.AuthRepository

class UserIdentityProvider(
    private val authRepository: AuthRepository,
    private val localUserIdStore: LocalUserIdStore,
) {
    fun currentUserId(): String =
        authRepository.currentUserId() ?: localUserIdStore.getOrCreate()

    fun localUserId(): String = localUserIdStore.getOrCreate()

    fun isAuthenticated(): Boolean = authRepository.isLoggedIn()
}
