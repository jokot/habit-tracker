package com.habittracker.data.repository

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MarkHabitIdentityRemovedTest {
    @Test
    fun `mark soft-unlink sets effectiveTo and clears syncedAt`() = runTest {
        val repo = FakeIdentityRepository()
        repo.linkHabitToIdentities("h1", setOf("i1"))
        // Force the link "synced" first so we can verify it gets cleared
        repo.markHabitIdentitySynced("h1", "i1", Instant.fromEpochSeconds(100))

        val cutoff = Instant.fromEpochSeconds(500)
        repo.markHabitIdentityRemoved("h1", "i1", cutoff)

        val rows = repo.getHabitIdentityLinksForUser("anyUser")
            .filter { it.habitId == "h1" && it.identityId == "i1" }
        val row = rows.singleOrNull()
        assertNotNull(row)
        assertEquals(cutoff, row.effectiveTo)
        assertNull(row.syncedAt)
    }
}
