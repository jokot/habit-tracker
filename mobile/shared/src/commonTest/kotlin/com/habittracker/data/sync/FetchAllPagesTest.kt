package com.habittracker.data.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fake sync client returns whole lists, so it can never reproduce the bug
 * these tests cover: Postgrest silently truncates a response at its row cap.
 * Drive the paging loop directly against a server that enforces one.
 */
class FetchAllPagesTest {

    /** A server holding [rowCount] rows that honours the requested range. */
    private class PagedServer(rowCount: Int) {
        val rows = List(rowCount) { it }
        var requests = 0
            private set

        fun page(range: LongRange): List<Int> {
            requests++
            val from = range.first.toInt().coerceAtMost(rows.size)
            val to = (range.last.toInt() + 1).coerceAtMost(rows.size)
            return rows.subList(from, to)
        }
    }

    @Test
    fun `empty table costs one request and returns nothing`() = runTest {
        val server = PagedServer(rowCount = 0)

        val all = fetchAllPages(pageSize = 3) { server.page(it) }

        assertEquals(emptyList(), all)
        assertEquals(1, server.requests)
    }

    @Test
    fun `a short first page ends the walk`() = runTest {
        val server = PagedServer(rowCount = 2)

        val all = fetchAllPages(pageSize = 3) { server.page(it) }

        assertEquals(listOf(0, 1), all)
        assertEquals(1, server.requests)
    }

    @Test
    fun `an exact multiple of the page size still needs the empty page to stop`() = runTest {
        val server = PagedServer(rowCount = 6)

        val all = fetchAllPages(pageSize = 3) { server.page(it) }

        assertEquals(List(6) { it }, all)
        assertEquals(3, server.requests)
    }

    @Test
    fun `rows past the cap are pulled, in order and without duplicates`() = runTest {
        // The bug: this used to come back as the first 3 only.
        val server = PagedServer(rowCount = 8)

        val all = fetchAllPages(pageSize = 3) { server.page(it) }

        assertEquals(List(8) { it }, all)
        assertEquals(all.distinct(), all)
        assertEquals(3, server.requests)
    }

    @Test
    fun `a server ignoring the range terminates instead of hanging`() = runTest {
        var requests = 0

        val all = fetchAllPages(pageSize = 2) {
            requests++
            listOf(0, 1) // always a full page, never advances
        }

        assertEquals(MAX_SYNC_PAGES, requests)
        assertTrue(all.size == MAX_SYNC_PAGES * 2)
    }
}
