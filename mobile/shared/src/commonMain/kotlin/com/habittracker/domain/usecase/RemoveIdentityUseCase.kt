package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository
import kotlinx.datetime.Clock

class RemoveIdentityUseCase(
    private val repo: IdentityRepository,
    private val clock: Clock = Clock.System,
) {
    suspend fun execute(userId: String, identityId: String) {
        repo.markUserIdentityRemoved(userId, identityId, removedAt = clock.now())
    }
}
