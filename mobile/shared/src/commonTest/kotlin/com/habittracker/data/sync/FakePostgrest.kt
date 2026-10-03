package com.habittracker.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random

/**
 * Just enough of Postgrest to serve the six sync pulls: `eq` / `gt` filters, an
 * `or` tree of them, `order`, `limit`, and a `max_rows` [cap] that truncates
 * every response without telling the client.
 *
 * Rows are stored in a shuffled order, so a query that forgets an `order` gets
 * rows in no useful order. Any query parameter it does not know fails the
 * request. Timestamps compare as instants, every other value as text.
 */
internal class FakePostgrest(private val cap: Int = 1000) {

    private val tables = mutableMapOf<String, MutableList<JsonObject>>()
    private val random = Random(20)

    /** Requests served, per table. */
    val requests = mutableMapOf<String, Int>()

    fun seed(table: String, rows: List<JsonObject>) {
        tables.getOrPut(table) { mutableListOf() } += rows
        tables.getValue(table).shuffle(random)
    }

    val client: SupabaseClient = createSupabaseClient("https://fake.supabase.co", "anon-key") {
        httpEngine = MockEngine { request ->
            val table = request.url.encodedPath.substringAfterLast('/')
            requests[table] = (requests[table] ?: 0) + 1
            respond(
                content = JsonArray(select(table, request.url.parameters.entries())).toString(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        install(Postgrest)
    }

    private fun select(table: String, params: Set<Map.Entry<String, List<String>>>): List<JsonObject> {
        var rows: List<JsonObject> = tables[table].orEmpty()
        var limit = Int.MAX_VALUE
        var order = emptyList<String>()
        for ((name, values) in params) {
            val value = values.single()
            when (name) {
                "select" -> check(value == "*") { "select=$value" }
                "limit" -> limit = value.toInt()
                "order" -> order = value.split(',').map { orderBy ->
                    check(".asc" in orderBy) { "order=$orderBy" }
                    orderBy.substringBefore('.')
                }
                "or" -> {
                    val anyOf = FilterParser(value).list()
                    rows = rows.filter { row -> anyOf.any { it(row) } }
                }
                else -> {
                    val condition = condition(name, value.substringBefore('.'), value.substringAfter('.'))
                    rows = rows.filter(condition)
                }
            }
        }
        val comparator = order.fold(Comparator<JsonObject> { _, _ -> 0 }) { acc, column ->
            acc.thenComparator { a, b -> compareField(a.text(column), b.text(column)) }
        }
        return rows.sortedWith(comparator).take(minOf(limit, cap))
    }

    /** Parses an `or` / `and` tree: `(a.gt."v",and(a.eq."v",b.gt.w))`. */
    private class FilterParser(private val s: String) {
        private var i = 0

        fun list(): List<(JsonObject) -> Boolean> {
            expect('(')
            val conditions = mutableListOf(next())
            while (s[i] == ',') {
                i++
                conditions += next()
            }
            expect(')')
            return conditions
        }

        private fun next(): (JsonObject) -> Boolean {
            if (s.startsWith("and(", i)) {
                i += 3
                val allOf = list()
                return { row -> allOf.all { it(row) } }
            }
            if (s.startsWith("or(", i)) {
                i += 2
                val anyOf = list()
                return { row -> anyOf.any { it(row) } }
            }
            val column = readUntil(".")
            i++
            val op = readUntil(".")
            i++
            val value = if (s[i] == '"') {
                i++
                readUntil("\"").also { i++ }
            } else {
                // Postgrest reads `:` and `.` as syntax here, so an unquoted
                // timestamp does not mean what the client meant.
                readUntil(",)").also { check(':' !in it && '.' !in it) { "unquoted value $it in $s" } }
            }
            return condition(column, op, value)
        }

        private fun readUntil(stops: String): String {
            val start = i
            while (s[i] !in stops) i++
            return s.substring(start, i)
        }

        private fun expect(c: Char) {
            check(s[i] == c) { "expected '$c' at $i in $s" }
            i++
        }
    }

    private companion object {
        fun JsonObject.text(column: String): String? = get(column)?.jsonPrimitive?.contentOrNull

        /** Like SQL, a null never passes `eq` or `gt`. */
        fun condition(column: String, op: String, value: String): (JsonObject) -> Boolean = { row ->
            val field = row.text(column)
            field != null && when (op) {
                "eq" -> compareField(field, value) == 0
                "gt" -> compareField(field, value) > 0
                else -> error("unsupported operator $op")
            }
        }

        /** Nulls sort last, as with Postgres `asc`. */
        fun compareField(a: String?, b: String?): Int {
            if (a == null || b == null) return compareValues(a == null, b == null)
            val instants = runCatching { Instant.parse(a) to Instant.parse(b) }.getOrNull()
            return if (instants != null) instants.first.compareTo(instants.second) else a.compareTo(b)
        }
    }
}
