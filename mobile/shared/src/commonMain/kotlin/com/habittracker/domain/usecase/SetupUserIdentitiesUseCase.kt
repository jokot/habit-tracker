package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class SetupUserIdentitiesUseCase(private val identityRepository: IdentityRepository) {
    suspend fun execute(userId: String, identityIds: Set<String>): Result<Unit> =
        runCatching {
            identityRepository.setUserIdentities(userId, identityIds)
        }
}
