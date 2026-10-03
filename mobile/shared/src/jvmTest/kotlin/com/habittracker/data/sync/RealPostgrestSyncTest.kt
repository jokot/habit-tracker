package com.habittracker.data.sync

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
            sync.upsertHabit(habit(habitId, userId))
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

            val watermark = pulled.maxOf { checkNotNull(it.syncedAt).toEpochMilliseconds() }
            assertTrue(sync.fetchHabitLogsSince(userId, watermark).isEmpty())
        } finally {
            // Deleting the user cascades to its habits and habit logs.
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
