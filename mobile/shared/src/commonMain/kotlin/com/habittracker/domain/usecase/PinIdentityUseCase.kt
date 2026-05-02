package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class PinIdentityUseCase(private val repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String) {
        repo.setPinAtomically(userId, identityId)
    }
}
