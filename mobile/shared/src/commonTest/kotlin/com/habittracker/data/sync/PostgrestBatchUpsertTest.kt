package com.habittracker.data.sync

import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A list upsert sends one `columns` list: the keys of all its rows. A row without a key gets
 * NULL in that column. So every row of a batch must send every key.
 */
class PostgrestBatchUpsertTest {

    private class Request(val table: String, val rows: JsonArray, val columns: String?)

    private val requests = mutableListOf<Request>()

    private val sync = PostgrestSupabaseSyncClient(
        createSupabaseClient("https://fake.supabase.co", "anon-key") {
            httpEngine = MockEngine { request ->
                val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                requests += Request(
                    table = request.url.encodedPath.substringAfterLast('/'),
                    rows = Json.parseToJsonElement(body).jsonArray,
                    columns = request.url.parameters["columns"],
                )
                respond("[]", HttpStatusCode.Created, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            install(Postgrest)
        },
    )

    private val t = Instant.parse("2026-10-06T12:00:00Z")

    private fun wantLog(id: String, points: Int) =
        WantLog(id, "user-1", "a1", 1.0, pointsSpent = points, deviceMode = DeviceMode.OTHER, loggedAt = t)

    private fun JsonArray.keySets() = map { (it as JsonObject).keys }.toSet()

    @Test
    fun `a batch of want logs sends one request with the same keys in every row`() = runTest {
        sync.upsertWantLogs(listOf(wantLog("w1", points = 1), wantLog("w2", points = 3)))

        val request = requests.single()
        assertEquals("want_logs", request.table)
        assertEquals(1, request.rows.keySets().size, "rows send different keys: ${request.rows.keySets()}")
        // points_spent = 1 is the default value. Without it, the server would write NULL.
        assertEquals(listOf(1, 3), request.rows.map { it.jsonObject.getValue("points_spent").jsonPrimitive.int })
    }

    @Test
    fun `a want with no icon or hide time sends both keys as null`() = runTest {
        // Before, an un-hidden want left out hidden_at, and the server kept the old value.
        sync.upsertWantActivities(
            listOf(
                WantActivity("a1", "Read", "pages", 1, isCustom = true, updatedAt = t),
                WantActivity("a2", "Walk", "min", 1, isCustom = true, iconKey = "walk", hiddenAt = t, updatedAt = t),
            ),
            ownerUserId = "user-1",
        )

        val rows = requests.single().rows
        assertEquals(1, rows.keySets().size, "rows send different keys: ${rows.keySets()}")
        assertEquals(JsonNull, rows[0].jsonObject["hidden_at"])
        assertEquals(JsonNull, rows[0].jsonObject["icon_key"])
    }

    @Test
    fun `an empty batch sends no request`() = runTest {
        sync.upsertWantLogs(emptyList())
        assertEquals(0, requests.size)
    }
}
