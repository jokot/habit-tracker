package com.habittracker.data.repository

import com.habittracker.domain.model.Identity

interface IdentityRepository {
    suspend fun getAllIdentities(): List<Identity>
    suspend fun upsertIdentities(identities: List<Identity>)
}
