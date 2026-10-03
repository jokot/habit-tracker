package com.habittracker.data.sync

import io.github.jan.supabase.postgrest.PropertyConversionMethod
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The fake sync client returns whole lists, so it can never reproduce the bug
 * these tests cover: Postgrest silently truncates a response at its row cap.
 * Drive the paging loop directly against a server that enforces one.
 */
class FetchAllPagesTest {

    /**
     * A server holding the rows `0 until rowCount`, sorted ascending. Each request
     * asks for the rows after a key, but like Postgrest's `max_rows` the server
     * never returns more than [cap] rows in one response.
     */
    private class PagedServer(rowCount: Int, private val cap: Int = Int.MAX_VALUE) {
        val rows = MutableList(rowCount) { it }
        var requests = 0
            private set

        fun page(after: Int?, limit: Long): List<Int> {
            requests++
            return rows.sorted()
                .filter { after == null || it > after }
                .take(limit.toInt())
                .take(cap)
        }
    }

    private suspend fun PagedServer.pullAll(pageSize: Long = 3) =
        fetchAllPages<Int, Int>(pageSize, keyOf = { it }) { after, limit -> page(after, limit) }

    @Test
    fun `empty table costs one request and returns nothing`() = runTest {
        val server = PagedServer(rowCount = 0)

        val all = server.pullAll()

        assertEquals(emptyList(), all)
        assertEquals(1, server.requests)
    }

    @Test
    fun `a short first page still needs the empty page to stop`() = runTest {
        val server = PagedServer(rowCount = 2)

        val all = server.pullAll()

        assertEquals(listOf(0, 1), all)
        assertEquals(2, server.requests)
    }

    @Test
    fun `an exact multiple of the page size still needs the empty page to stop`() = runTest {
        val server = PagedServer(rowCount = 6)

        val all = server.pullAll()

        assertEquals(List(6) { it }, all)
        assertEquals(3, server.requests)
    }

    @Test
    fun `rows past the cap are pulled, in order and without duplicates`() = runTest {
        // The bug: this used to come back as the first 3 only.
        val server = PagedServer(rowCount = 8)

        val all = server.pullAll()

        assertEquals(List(8) { it }, all)
        assertEquals(4, server.requests)
    }

    @Test
    fun `a server cap below the page size still pulls every row`() = runTest {
        // Supabase max_rows is set per project. A short page is not the last page.
        val server = PagedServer(rowCount = 8, cap = 2)

        val all = server.pullAll()

        assertEquals(List(8) { it }, all)
    }

    @Test
    fun `a row edited mid-walk does not hide the row after the page seam`() = runTest {
        // Another device edits row 1 after page 1 is read. Its new timestamp sorts
        // it last (here: 100). With offsets, every later row moved back one place
        // and row 3 was never read.
        val server = PagedServer(rowCount = 8)
        var pages = 0

        val all = fetchAllPages<Int, Int>(pageSize = 3, keyOf = { it }) { after, limit ->
            server.page(after, limit).also {
                if (++pages == 1) {
                    server.rows.remove(1)
                    server.rows.add(100)
                }
            }
        }

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 100), all)
    }

    @Test
    fun `a server ignoring the key fails instead of hanging or truncating`() = runTest {
        var requests = 0

        assertFailsWith<IllegalStateException> {
            fetchAllPages<Int, Int>(pageSize = 2, keyOf = { it }) { _, _ ->
                requests++
                listOf(0, 1) // always a full page, never advances
            }
        }

        assertEquals(MAX_SYNC_PAGES, requests)
    }

    @Test
    fun `rowsAfter writes the row comparison out as an or tree with quoted values`() {
        val filter = PostgrestFilterBuilder(PropertyConversionMethod.NONE).apply {
            rowsAfter(
                listOf("updated_at", "id"),
                listOf("2026-08-11T14:14:24.123456Z", "h1"),
            )
        }

        assertEquals(
            mapOf(
                "or" to listOf(
                    "(updated_at.gt.\"2026-08-11T14:14:24.123456Z\"," +
                        "and(updated_at.eq.\"2026-08-11T14:14:24.123456Z\",id.gt.\"h1\"))",
                ),
            ),
            filter.params,
        )
    }
}
