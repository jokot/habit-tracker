package com.habittracker.data.repository

import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.Identity

class LocalIdentityRepository(
    private val db: HabitTrackerDatabase,
) : IdentityRepository {

    override suspend fun getAllIdentities(): List<Identity> =
        db.habitTrackerDatabaseQueries
            .getAllIdentities()
            .executeAsList()
            .map { Identity(id = it.id, name = it.name, description = it.description, icon = it.icon) }

    override suspend fun upsertIdentities(identities: List<Identity>) {
        identities.forEach { identity ->
            db.habitTrackerDatabaseQueries.upsertIdentity(
                id = identity.id,
                name = identity.name,
                description = identity.description,
                icon = identity.icon,
            )
        }
    }
}
