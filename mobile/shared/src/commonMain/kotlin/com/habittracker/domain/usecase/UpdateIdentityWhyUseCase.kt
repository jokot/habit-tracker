package com.habittracker.domain.usecase

import com.habittracker.data.repository.IdentityRepository

class UpdateIdentityWhyUseCase(private val repo: IdentityRepository) {
    suspend fun execute(userId: String, identityId: String, whyText: String?) {
        val normalized = whyText?.trim()?.takeIf { it.isNotEmpty() }
        repo.updateWhyText(userId, identityId, normalized)
    }
}
