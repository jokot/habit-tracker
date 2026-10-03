package com.habittracker.data.sync

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.microseconds

/**
 * Runs the six real pull queries against [FakePostgrest]. The server cap is far
 * below [SYNC_PAGE_SIZE], so every pull crosses several page seams, and three
 * rows share each timestamp, so the seams fall inside runs of equal timestamps.
 * A fetcher that drops its tie-break, its key, or its order loses or repeats rows
 * here.
 */
class PostgrestSupabaseSyncClientTest {

    private val server = FakePostgrest(cap = 4)
    private val client = PostgrestSupabaseSyncClient(server.client)

    @Test
    fun `habits pull every row after the watermark`() = runTest {
        server.seed("habits", rows(ROWS) { i, time ->
            put("id", "habit-$i")
            put("user_id", if (i % 5 == 0) OTHER_USER else USER)
            put("template_id", null as String?)
            put("name", "Habit $i")
            put("unit", "min")
            put("threshold_per_point", 10.0)
            put("daily_target", 1)
            put("created_at", time)
            put("updated_at", time)
        })

        val pulled = client.fetchHabitsSince(USER, SINCE_MS).map { it.id }

        assertEquals(expected("habit-") { it % 5 != 0 }, pulled.sorted())
    }

    @Test
    fun `want activities pull every row after the watermark`() = runTest {
        server.seed("want_activities", rows(ROWS) { i, time ->
            put("id", "want-$i")
            put("user_id", if (i % 5 == 0) OTHER_USER else USER)
            put("name", "Want $i")
            put("unit", "min")
            put("units_per_point", 30)
            put("is_custom", true)
            put("updated_at", time)
        })

        val pulled = client.fetchWantActivitiesSince(USER, SINCE_MS).map { it.id }

        assertEquals(expected("want-") { it % 5 != 0 }, pulled.sorted())
    }

    @Test
    fun `habit logs pull every row after the watermark`() = runTest {
        server.seed("habit_logs", rows(ROWS) { i, time ->
            put("id", "log-$i")
            put("user_id", if (i % 5 == 0) OTHER_USER else USER)
            put("habit_id", "habit-1")
            put("quantity", 1.0)
            put("logged_at", time)
            put("deleted_at", null as String?)
            put("synced_at", time)
        })

        val pulled = client.fetchHabitLogsSince(USER, SINCE_MS).map { it.id }

        assertEquals(expected("log-") { it % 5 != 0 }, pulled.sorted())
    }

    @Test
    fun `want logs pull every row after the watermark`() = runTest {
        server.seed("want_logs", rows(ROWS) { i, time ->
            put("id", "wlog-$i")
            put("user_id", if (i % 5 == 0) OTHER_USER else USER)
            put("activity_id", "want-1")
            put("quantity", 1.0)
            put("points_spent", 1)
            put("device_mode", "this_device")
            put("logged_at", time)
            put("deleted_at", null as String?)
            put("synced_at", time)
        })

        val pulled = client.fetchWantLogsSince(USER, SINCE_MS).map { it.id }

        assertEquals(expected("wlog-") { it % 5 != 0 }, pulled.sorted())
    }

    @Test
    fun `recent habit logs pull every row logged from the given time, across pages`() = runTest {
        val atFrom = buildJsonObject {
            put("id", "log-at")
            put("user_id", USER)
            put("habit_id", "habit-1")
            put("quantity", 1.0)
            put("logged_at", SINCE.toString())
            put("deleted_at", null as String?)
            put("synced_at", OLD_SYNC)
        }
        server.seed("habit_logs", rows(ROWS) { i, time ->
            put("id", "log-$i")
            put("user_id", if (i % 5 == 0) OTHER_USER else USER)
            put("habit_id", "habit-1")
            put("quantity", 1.0)
            put("logged_at", time)
            put("deleted_at", null as String?)
            put("synced_at", OLD_SYNC)
        } + atFrom)

        val pulled = client.fetchHabitLogsLoggedFrom(USER, SINCE_MS).map { it.id }

        assertEquals((expected("log-") { it % 5 != 0 } + "log-at").sorted(), pulled.sorted())
    }

    @Test
    fun `recent want logs pull every row logged from the given time, across pages`() = runTest {
        server.seed("want_logs", rows(ROWS) { i, time ->
            put("id", "wlog-$i")
            put("user_id", if (i % 5 == 0) OTHER_USER else USER)
            put("activity_id", "want-1")
            put("quantity", 1.0)
            put("points_spent", 1)
            put("device_mode", "this_device")
            put("logged_at", time)
            put("deleted_at", null as String?)
            put("synced_at", OLD_SYNC)
        })

        val pulled = client.fetchWantLogsLoggedFrom(USER, SINCE_MS).map { it.id }

        assertEquals(expected("wlog-") { it % 5 != 0 }, pulled.sorted())
    }

    @Test
    fun `user identities pull every row of the user, old ones too`() = runTest {
        // No watermark here: a pin or a why-text edit does not move added_at.
        server.seed("user_identities", rows(ROWS) { i, time ->
            put("user_id", if (i % 5 == 0) OTHER_USER else USER)
            put("identity_id", "identity-$i")
            put("added_at", time)
        })

        val pulled = client.fetchUserIdentitiesSince(USER, SINCE_MS).map { it.identityId }

        assertEquals(expected("identity-", includeOld = true) { it % 5 != 0 }, pulled.sorted())
    }

    @Test
    fun `habit identities pull every row after the watermark`() = runTest {
        // The key is (updated_at, habit_id, identity_id). Several rows share a
        // habit, and several share an identity, so both tie-breaks matter.
        server.seed("habit_identities", rows(ROWS) { i, time ->
            put("habit_id", "habit-${i / 4}")
            put("identity_id", "identity-${i % 4}")
            put("added_at", time)
            put("updated_at", time)
        })

        val pulled = client.fetchHabitIdentitiesSince(USER, SINCE_MS)
            .map { "${it.habitId}/${it.identityId}" }

        val expected = (OLD_ROWS until ROWS).map { "habit-${it / 4}/identity-${it % 4}" }
        assertEquals(expected.sorted(), pulled.sorted())
    }

    @Test
    fun `a first sync of 1500 habit logs pulls all of them through the default 1000-row cap`() = runTest {
        // Issue #20: 3 habits × 500 days. Before paging, a fresh install got 1000.
        val server = FakePostgrest(cap = 1000)
        server.seed("habit_logs", rows(1500) { i, time ->
            put("id", "log-$i")
            put("user_id", USER)
            put("habit_id", "habit-${i % 3}")
            put("quantity", 1.0)
            put("logged_at", time)
            put("deleted_at", null as String?)
            put("synced_at", time)
        })

        val pulled = PostgrestSupabaseSyncClient(server.client)
            .fetchHabitLogsSince(USER, sinceMs = 0)
            .map { it.id }

        assertEquals(List(1500) { "log-$it" }.sorted(), pulled.sorted())
        assertEquals(3, server.requests["habit_logs"]) // 1000, 500, then the empty page
    }

    private companion object {
        const val USER = "user-1"
        const val OTHER_USER = "user-2"

        /** Rows per table. The first [OLD_ROWS] are at or before the watermark. */
        const val ROWS = 40
        const val OLD_ROWS = 6

        val SINCE: Instant = Instant.parse("2026-08-11T14:00:00Z")
        val SINCE_MS = SINCE.toEpochMilliseconds()

        /** A sync time before every row, so the recent-log tests filter by `logged_at` alone. */
        const val OLD_SYNC = "2026-01-01T00:00:00+00:00"

        /**
         * [count] rows, three to a timestamp, one microsecond apart. Row
         * [OLD_ROWS] is the first one after the watermark. Times use the format
         * Postgrest returns (`+00:00`, not `Z`).
         */
        fun rows(count: Int, build: JsonObjectBuilder.(Int, String) -> Unit) =
            List(count) { i ->
                val micros = (i - OLD_ROWS) / 3 + (if (i < OLD_ROWS) -1 else 1)
                val time = SINCE + micros.microseconds
                buildJsonObject { build(i, time.toString().replace("Z", "+00:00")) }
            }

        fun expected(prefix: String, includeOld: Boolean = false, mine: (Int) -> Boolean) =
            (0 until ROWS)
                .filter { (includeOld || it >= OLD_ROWS) && mine(it) }
                .map { "$prefix$it" }
                .sorted()
    }
}
