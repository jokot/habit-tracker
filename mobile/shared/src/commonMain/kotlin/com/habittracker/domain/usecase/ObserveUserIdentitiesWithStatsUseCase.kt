package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.IdentityWithStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveUserIdentitiesWithStatsUseCase(
    private val identityRepo: IdentityRepository,
    private val statsUseCase: ComputeIdentityStatsUseCase,
) {
    fun execute(userId: String): Flow<List<IdentityWithStats>> =
        identityRepo.observeUserIdentities(userId).map { identities ->
            identities.map { identity ->
                IdentityWithStats(
                    identity = identity,
                    stats = statsUseCase.computeNow(userId, identity.id),
                )
            }
        }
}
