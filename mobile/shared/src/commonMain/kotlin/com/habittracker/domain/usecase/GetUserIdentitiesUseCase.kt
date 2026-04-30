package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository
import com.habittracker.domain.model.Identity
import kotlinx.coroutines.flow.Flow

class GetUserIdentitiesUseCase(private val identityRepository: IdentityRepository) {
    fun execute(userId: String): Flow<List<Identity>> =
        identityRepository.observeUserIdentities(userId)
}
