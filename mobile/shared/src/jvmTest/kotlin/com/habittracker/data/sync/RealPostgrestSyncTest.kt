package com.habittracker.data.sync

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import com.habittracker.domain.model.Habit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Pulls habit logs from a real Postgrest, so the page queries meet the real
 * filter parser and the real `max_rows` cap. [FakePostgrest] only copies them.
 *
 * The test needs a local Supabase. `supabase/run-sync-it.sh` starts the test
 * with `SUPABASE_IT_URL` and `SUPABASE_IT_SERVICE_KEY` set. Without both
 * variables, the test is skipped.
 */
@OptIn(ExperimentalUuidApi::class)
class RealPostgrestSyncTest {

    private val url = System.getenv("SUPABASE_IT_URL")
    private val serviceKey = System.getenv("SUPABASE_IT_SERVICE_KEY")

    @Test
    fun `a pull gets every habit log once, in order, across page seams`() = runBlocking {
        assumeTrue("SUPABASE_IT_URL and SUPABASE_IT_SERVICE_KEY are not set", url != null && serviceKey != null)
        val supabase = adminClient()
        val sync = PostgrestSupabaseSyncClient(supabase)
        val userId = supabase.auth.admin.createUserWithEmail {
            email = "sync-it-${Uuid.random()}@example.com"
            password = Uuid.random().toString()
            autoConfirm = true
        }.id
        try {
            val habitId = Uuid.random().toString()
            sync.upsertHabits(listOf(habit(habitId, userId)))
            // 500 rows share each synced_at, so every page seam falls inside a
            // run of equal timestamps. Only the id tie-break keeps those rows
            // from being lost or read twice.
            val seeded = (0 until LOG_COUNT).map { i ->
                Uuid.random().toString() to BASE + (i / 500).seconds
            }
            seeded.chunked(500).forEach { chunk ->
                supabase.postgrest.from("habit_logs").insert(chunk.map { (id, syncedAt) ->
                    buildJsonObject {
                        put("id", id)
                        put("user_id", userId)
                        put("habit_id", habitId)
                        put("quantity", 1.0)
                        put("logged_at", syncedAt.toString())
                        put("synced_at", syncedAt.toString())
                    }
                })
            }

            val pulled = sync.fetchHabitLogsSince(userId, 0)

            assertEquals(seeded.map { it.first }.toSet(), pulled.map { it.id }.toSet())
            assertEquals(LOG_COUNT, pulled.size)
            val keys = pulled.map { checkNotNull(it.syncedAt) to it.id }
            assertEquals(keys.sortedWith(compareBy({ it.first }, { it.second })), keys)

            // The server stamps synced_at in microseconds, and the watermark is in milliseconds.
            // It rounds down, so the rows of its last millisecond come again, and no row is lost.
            val watermark = pulled.maxOf { checkNotNull(it.syncedAt).toEpochMilliseconds() }
            val again = sync.fetchHabitLogsSince(userId, watermark)
            assertTrue(again.all { checkNotNull(it.syncedAt).toEpochMilliseconds() == watermark })
            assertTrue(sync.fetchHabitLogsSince(userId, watermark + 1).isEmpty())
        } finally {
            // Deleting the user cascades to its habits and habit logs.
            supabase.auth.admin.deleteUser(userId)
        }
    }

    @Test
    fun `the server sets synced_at and updated_at, not the phone`() = withUser { supabase, sync, userId ->
        // A phone with a slow clock wrote a time in the past, and other devices skipped the row (#35).
        val hourAgo = Clock.System.now() - 1.hours
        val habitId = Uuid.random().toString()
        sync.upsertHabits(listOf(habit(habitId, userId).copy(updatedAt = hourAgo)))
        sync.upsertHabitLogs(listOf(HabitLog(Uuid.random().toString(), userId, habitId, 1.0, hourAgo, syncedAt = hourAgo)))

        val now = Clock.System.now()
        val log = sync.fetchHabitLogsSince(userId, 0).single()
        assertTrue((now - checkNotNull(log.syncedAt)).absoluteValue < 1.minutes, "synced_at = ${log.syncedAt}")
        val pulledHabit = sync.fetchHabitsSince(userId, 0).single()
        assertTrue((now - pulledHabit.updatedAt).absoluteValue < 1.minutes, "updated_at = ${pulledHabit.updatedAt}")
    }

    @Test
    fun `one request saves a batch of 600 logs`() = withUser { _, sync, userId ->
        val habitId = Uuid.random().toString()
        sync.upsertHabits(listOf(habit(habitId, userId)))
        val logs = (0 until 600).map { HabitLog(Uuid.random().toString(), userId, habitId, 1.0, BASE) }
        sync.upsertHabitLogs(logs)
        assertEquals(logs.map { it.id }.toSet(), sync.fetchHabitLogsSince(userId, 0).map { it.id }.toSet())
    }

    @Test
    fun `a batch keeps the points of each want log`() = withUser { _, sync, userId ->
        // points_spent = 1 is the default. A row that left it out got NULL in a list upsert.
        val activity = WantActivity(Uuid.random().toString(), "Scroll", "min", 1, isCustom = true, updatedAt = BASE)
        sync.upsertWantActivities(listOf(activity), ownerUserId = userId)
        val logs = listOf(1, 3).map { points ->
            WantLog(Uuid.random().toString(), userId, activity.id, 1.0, points, DeviceMode.OTHER, BASE)
        }
        sync.upsertWantLogs(logs)
        assertEquals(setOf(1, 3), sync.fetchWantLogsSince(userId, 0).map { it.pointsSpent }.toSet())
    }

    @Test
    fun `an un-hidden want reaches the server`() = withUser { _, sync, userId ->
        val activity = WantActivity(Uuid.random().toString(), "Scroll", "min", 1, isCustom = true, updatedAt = BASE)
        sync.upsertWantActivities(listOf(activity.copy(hiddenAt = BASE)), ownerUserId = userId)
        sync.upsertWantActivities(listOf(activity), ownerUserId = userId)
        val pulled = sync.fetchWantActivitiesSince(userId, 0).single { it.id == activity.id }
        assertEquals(null, pulled.hiddenAt)
    }

    /** Runs [block] with a new user, and deletes the user and all its rows after it. */
    private fun withUser(block: suspend (SupabaseClient, PostgrestSupabaseSyncClient, String) -> Unit) = runBlocking {
        assumeTrue("SUPABASE_IT_URL and SUPABASE_IT_SERVICE_KEY are not set", url != null && serviceKey != null)
        val supabase = adminClient()
        val userId = supabase.auth.admin.createUserWithEmail {
            email = "sync-it-${Uuid.random()}@example.com"
            password = Uuid.random().toString()
            autoConfirm = true
        }.id
        try {
            block(supabase, PostgrestSupabaseSyncClient(supabase), userId)
        } finally {
            supabase.auth.admin.deleteUser(userId)
        }
    }

    private fun adminClient(): SupabaseClient =
        createSupabaseClient(supabaseUrl = checkNotNull(url), supabaseKey = checkNotNull(serviceKey)) {
            install(Auth) {
                autoLoadFromStorage = false
                autoSaveToStorage = false
                alwaysAutoRefresh = false
            }
            install(Postgrest)
        }

    private fun habit(id: String, userId: String) = Habit(
        id = id,
        userId = userId,
        templateId = TEMPLATE_ID,
        name = "Read",
        unit = "pages",
        thresholdPerPoint = 1.0,
        dailyTarget = 1,
        createdAt = BASE,
        updatedAt = BASE,
    )

    private companion object {
        /** Two and a half pages at the default `max_rows` of 1000. */
        const val LOG_COUNT = 2500

        /** A template from the Phase 8 catalog migration. */
        const val TEMPLATE_ID = "10000000-0000-0000-0000-000000000001"

        val BASE = Instant.parse("2026-10-03T12:00:00.123Z")
    }
}
