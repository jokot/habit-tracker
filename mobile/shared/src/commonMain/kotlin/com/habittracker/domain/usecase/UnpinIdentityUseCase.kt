package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class UnpinIdentityUseCase(private val repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String) {
        repo.setPinForIdentity(userId, identityId, isPinned = false)
    }
}
