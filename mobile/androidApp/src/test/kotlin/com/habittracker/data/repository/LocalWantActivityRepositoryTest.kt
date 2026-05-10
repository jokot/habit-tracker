package com.habittracker.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.domain.model.WantActivity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class LocalWantActivityRepositoryTest {
    private val userId = "u1"

    private fun newRepo(): LocalWantActivityRepository {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // name=null → in-memory database (per AndroidSqliteDriver contract).
        val driver = AndroidSqliteDriver(HabitTrackerDatabase.Schema, context, name = null)
        return LocalWantActivityRepository(HabitTrackerDatabase(driver))
    }

    private fun seed(id: String, iconKey: String? = null, hiddenAt: Instant? = null) =
        WantActivity(
            id = id,
            name = "n-$id",
            unit = "minutes",
            unitsPerPoint = 1,
            isCustom = false,
            updatedAt = Instant.fromEpochMilliseconds(1_000),
            syncedAt = null,
            iconKey = iconKey,
            hiddenAt = hiddenAt,
        )

    @Test
    fun `getWantActivities returns only un-hidden rows`() = runTest {
        val repo = newRepo()
        repo.saveWantActivity(seed("a"), userId)
        repo.saveWantActivity(seed("b"), userId)
        repo.hideWantActivity("a", userId, Instant.fromEpochMilliseconds(2_000))

        val visible = repo.getWantActivities(userId)
        assertEquals(listOf("b"), visible.map { it.id })

        val all = repo.getAllWantActivitiesForUser(userId)
        assertEquals(2, all.size)
        assertEquals(setOf("a", "b"), all.map { it.id }.toSet())
    }

    @Test
    fun `hideWantActivity sets hiddenAt and clears syncedAt`() = runTest {
        val repo = newRepo()
        repo.saveWantActivity(seed("a"), userId)
        repo.markSynced("a", Instant.fromEpochMilliseconds(1_500))
        repo.hideWantActivity("a", userId, Instant.fromEpochMilliseconds(2_000))

        val hidden = repo.getAllWantActivitiesForUser(userId).single()
        assertNotNull(hidden.hiddenAt)
        assertNull(hidden.syncedAt)
    }

    @Test
    fun `unhideWantActivity clears hiddenAt and syncedAt`() = runTest {
        val repo = newRepo()
        repo.saveWantActivity(seed("a", hiddenAt = Instant.fromEpochMilliseconds(2_000)), userId)
        repo.markSynced("a", Instant.fromEpochMilliseconds(2_500))
        repo.unhideWantActivity("a", userId)

        val visible = repo.getWantActivities(userId)
        assertEquals(1, visible.size)
        assertNull(visible.single().hiddenAt)
        assertNull(visible.single().syncedAt)
    }

    @Test
    fun `saveWantActivity persists iconKey`() = runTest {
        val repo = newRepo()
        repo.saveWantActivity(seed("a", iconKey = "play_circle"), userId)
        val row = repo.getWantActivities(userId).single()
        assertEquals("play_circle", row.iconKey)
    }
}
