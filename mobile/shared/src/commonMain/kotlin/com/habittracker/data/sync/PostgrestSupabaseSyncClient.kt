package com.habittracker.data.sync

import com.habittracker.data.repository.HabitIdentityRow
import com.habittracker.data.repository.UserIdentityRow
import com.habittracker.domain.model.DeviceMode
import com.habittracker.domain.model.Habit
import com.habittracker.domain.model.HabitLog
import com.habittracker.domain.model.WantActivity
import com.habittracker.domain.model.WantLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PostgrestSupabaseSyncClient(
    private val supabase: SupabaseClient,
) : SupabaseSyncClient {

    override suspend fun upsertHabits(rows: List<Habit>) =
        upsertAll("habits", rows.map { it.toDto() })

    override suspend fun upsertWantActivities(rows: List<WantActivity>, ownerUserId: String) =
        upsertAll("want_activities", rows.map { it.toDto(ownerUserId) })

    override suspend fun upsertHabitLogs(rows: List<HabitLog>) =
        upsertAll("habit_logs", rows.map { it.toDto() })

    override suspend fun upsertWantLogs(rows: List<WantLog>) =
        upsertAll("want_logs", rows.map { it.toDto() })

    /**
     * A list upsert sends one `columns` list, the keys of all its rows. A row without a key
     * gets NULL in that column, so each DTO field with a default has @EncodeDefault.
     */
    private suspend inline fun <reified D : Any> upsertAll(table: String, rows: List<D>) {
        if (rows.isEmpty()) return
        supabase.postgrest.from(table).upsert(rows)
    }

    override suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit> =
        fetchPaged<HabitDto>(
            "habits",
            orderBy = listOf("updated_at", "id"),
            keyOf = { listOf(it.updatedAt.asCursorTime(), it.id) },
        ) {
            eq("user_id", userId)
            gt("updated_at", Instant.fromEpochMilliseconds(sinceMs).toString())
        }.map { it.toDomain() }

    override suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity> =
        fetchPaged<WantActivityDto>(
            "want_activities",
            orderBy = listOf("updated_at", "id"),
            keyOf = { listOf(it.updatedAt.asCursorTime(), it.id) },
        ) {
            eq("user_id", userId)
            gt("updated_at", Instant.fromEpochMilliseconds(sinceMs).toString())
        }.map { it.toDomain() }

    override suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog> =
        fetchPaged<HabitLogDto>(
            "habit_logs",
            orderBy = listOf("synced_at", "id"),
            keyOf = { listOf(checkNotNull(it.syncedAt).asCursorTime(), it.id) },
        ) {
            eq("user_id", userId)
            gt("synced_at", Instant.fromEpochMilliseconds(sinceMs).toString())
        }.map { it.toDomain() }

    override suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog> =
        fetchPaged<WantLogDto>(
            "want_logs",
            orderBy = listOf("synced_at", "id"),
            keyOf = { listOf(checkNotNull(it.syncedAt).asCursorTime(), it.id) },
        ) {
            eq("user_id", userId)
            gt("synced_at", Instant.fromEpochMilliseconds(sinceMs).toString())
        }.map { it.toDomain() }

    override suspend fun fetchHabitLogsLoggedFrom(userId: String, fromMs: Long): List<HabitLog> =
        fetchPaged<HabitLogDto>(
            "habit_logs",
            orderBy = listOf("logged_at", "id"),
            keyOf = { listOf(it.loggedAt.asCursorTime(), it.id) },
        ) {
            eq("user_id", userId)
            gte("logged_at", Instant.fromEpochMilliseconds(fromMs).toString())
        }.map { it.toDomain() }

    override suspend fun fetchWantLogsLoggedFrom(userId: String, fromMs: Long): List<WantLog> =
        fetchPaged<WantLogDto>(
            "want_logs",
            orderBy = listOf("logged_at", "id"),
            keyOf = { listOf(it.loggedAt.asCursorTime(), it.id) },
        ) {
            eq("user_id", userId)
            gte("logged_at", Instant.fromEpochMilliseconds(fromMs).toString())
        }.map { it.toDomain() }

    override suspend fun upsertUserIdentities(rows: List<UserIdentityRow>) =
        upsertAll("user_identities", rows.map { it.toDto() })

    override suspend fun upsertHabitIdentities(rows: List<HabitIdentityRow>) =
        upsertAll("habit_identities", rows.map { it.toDto() })

    override suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow> {
        // user_identities mutate via UPDATE (pin / why / soft-remove) without
        // changing added_at. Watermark by added_at would miss those updates.
        // Volume per user is small (≤10 rows) — fetch all rows for the user.
        @Suppress("UNUSED_PARAMETER") val _s = sinceMs
        return fetchPaged<UserIdentityDto>(
            "user_identities",
            orderBy = listOf("added_at", "identity_id"),
            keyOf = { listOf(it.addedAt.asCursorTime(), it.identityId) },
        ) {
            eq("user_id", userId)
        }.map { it.toDomain() }
    }

    override suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow> {
        // RLS scopes to habits owned by current user; client-side userId arg is for parity
        @Suppress("UNUSED_PARAMETER") val _u = userId
        return fetchPaged<HabitIdentityDto>(
            "habit_identities",
            orderBy = listOf("updated_at", "habit_id", "identity_id"),
            keyOf = { listOf(it.updatedAt.asCursorTime(), it.habitId, it.identityId) },
        ) {
            gt("updated_at", Instant.fromEpochMilliseconds(sinceMs).toString())
        }.map { it.toDomain() }
    }

    /**
     * Pull every row of [table] that matches [where], one page at a time. Rows
     * are sorted ascending by each column of [orderBy], in order, and each page
     * asks for the rows after the last row of the page before.
     *
     * [orderBy] must start with the watermark column and end with columns that
     * are unique per row. Rows that share a timestamp have no defined order
     * between two requests: without the tie-break a row can land on both sides
     * of a page seam, or on neither. For the same reason, [keyOf] must return
     * the value of each [orderBy] column, in the same order.
     */
    private suspend inline fun <reified D : Any> fetchPaged(
        table: String,
        orderBy: List<String>,
        crossinline keyOf: (D) -> List<String>,
        crossinline where: PostgrestFilterBuilder.() -> Unit,
    ): List<D> = fetchAllPages(keyOf = { keyOf(it) }) { after, pageSize ->
        supabase.postgrest.from(table)
            .select {
                filter {
                    where()
                    if (after != null) rowsAfter(orderBy, after)
                }
                orderBy.forEach { order(it, Order.ASCENDING) }
                limit(pageSize)
            }
            .decodeList<D>()
    }
}

// ---- Paging -------------------------------------------------------------

/** Rows asked for per page. Postgrest's default `max_rows` is also 1000. */
internal const val SYNC_PAGE_SIZE = 1000L

/**
 * Fail after this many pages. A server that ignores the page key would otherwise
 * hand back the same full page forever and hang the pull. Failing — rather than
 * returning what arrived — keeps the watermark where it was, so the rows past
 * the cap are pulled on the next sync instead of being skipped for good.
 */
internal const val MAX_SYNC_PAGES = 1000

/**
 * Walk pages until one comes back empty. Each page starts after the key of the
 * last row received ([keyOf]), not at a row offset.
 *
 * An offset breaks when a row moves during the walk. Another device edits a row
 * that is already read, its timestamp moves it to the end, every later row moves
 * back one place, and one row at the next page seam is never read. The moved row
 * then raises the watermark past the row that was skipped, so the skip is
 * permanent. A key has no such gap: the next page holds every row that sorts
 * after the last row read, wherever the other rows moved.
 *
 * A short page is not the last page. The server may cap a response below
 * [pageSize] (`max_rows` is set per project).
 */
internal suspend fun <T, K : Any> fetchAllPages(
    pageSize: Long = SYNC_PAGE_SIZE,
    keyOf: (T) -> K,
    fetchPage: suspend (after: K?, pageSize: Long) -> List<T>,
): List<T> {
    val all = mutableListOf<T>()
    var after: K? = null
    repeat(MAX_SYNC_PAGES) {
        val page = fetchPage(after, pageSize)
        if (page.isEmpty()) return all
        all += page
        after = keyOf(page.last())
    }
    error("Sync pull exceeded $MAX_SYNC_PAGES pages of $pageSize rows")
}

/**
 * Keep the rows that sort after [values] on [columns], ascending:
 * `(c1, c2, c3) > (v1, v2, v3)`. Postgrest has no row comparison, so this
 * writes it out as `c1 > v1 OR (c1 = v1 AND c2 > v2) OR (c1 = v1 AND c2 = v2
 * AND c3 > v3)`.
 *
 * Each value is in double quotes. A timestamp holds `:` and `.`, and Postgrest
 * reserves those characters inside an `or` / `and` tree.
 */
internal fun PostgrestFilterBuilder.rowsAfter(columns: List<String>, values: List<String>) {
    require(columns.size == values.size) { "${columns.size} columns but ${values.size} values" }
    val quoted = values.map { "\"$it\"" }
    or {
        gt(columns[0], quoted[0])
        for (i in 1 until columns.size) {
            and {
                for (j in 0 until i) eq(columns[j], quoted[j])
                gt(columns[i], quoted[i])
            }
        }
    }
}

/**
 * A Postgrest timestamp (`2026-08-11T14:14:24.123456+00:00`) in the UTC form
 * used in a page cursor. The microseconds are kept, so `eq` still matches the
 * stored value exactly.
 */
private fun String.asCursorTime(): String = Instant.parse(this).toString()

// ---- DTOs ---------------------------------------------------------------

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class HabitDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("template_id") val templateId: String?,
    val name: String,
    val unit: String,
    @SerialName("threshold_per_point") val thresholdPerPoint: Double,
    @SerialName("daily_target") val dailyTarget: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_from") val effectiveFrom: String? = null,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_to") val effectiveTo: String? = null,
)

private fun Habit.toDto() = HabitDto(
    id = id,
    userId = userId,
    templateId = templateId,
    name = name,
    unit = unit,
    thresholdPerPoint = thresholdPerPoint,
    dailyTarget = dailyTarget,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    effectiveFrom = effectiveFrom?.toString(),
    effectiveTo = effectiveTo?.toString(),
)

private fun HabitDto.toDomain(): Habit = Habit(
    id = id,
    userId = userId,
    templateId = templateId,
    name = name,
    unit = unit,
    thresholdPerPoint = thresholdPerPoint,
    dailyTarget = dailyTarget,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    syncedAt = Instant.parse(updatedAt),
    effectiveFrom = effectiveFrom?.let { Instant.parse(it) },
    effectiveTo = effectiveTo?.let { Instant.parse(it) },
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class WantActivityDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val unit: String,
    @SerialName("units_per_point") val unitsPerPoint: Int,
    @SerialName("is_custom") val isCustom: Boolean,
    @SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.EncodeDefault @SerialName("icon_key") val iconKey: String? = null,
    @kotlinx.serialization.EncodeDefault @SerialName("hidden_at") val hiddenAt: String? = null,
)

private fun WantActivity.toDto(ownerUserId: String) = WantActivityDto(
    id = id,
    userId = ownerUserId,
    name = name,
    unit = unit,
    unitsPerPoint = unitsPerPoint,
    isCustom = isCustom,
    updatedAt = updatedAt.toString(),
    iconKey = iconKey,
    hiddenAt = hiddenAt?.toString(),
)

private fun WantActivityDto.toDomain() = WantActivity(
    id = id,
    name = name,
    unit = unit,
    unitsPerPoint = unitsPerPoint,
    isCustom = isCustom,
    createdByUserId = userId,
    iconKey = iconKey,
    hiddenAt = hiddenAt?.let { Instant.parse(it) },
    updatedAt = Instant.parse(updatedAt),
    syncedAt = Instant.parse(updatedAt),
)

@Serializable
private data class HabitLogDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("habit_id") val habitId: String,
    val quantity: Double,
    @SerialName("logged_at") val loggedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("synced_at") val syncedAt: String?,
)

private fun HabitLog.toDto() = HabitLogDto(
    id = id,
    userId = userId,
    habitId = habitId,
    quantity = quantity,
    loggedAt = loggedAt.toString(),
    deletedAt = deletedAt?.toString(),
    syncedAt = syncedAt?.toString(),
)

private fun HabitLogDto.toDomain() = HabitLog(
    id = id,
    userId = userId,
    habitId = habitId,
    quantity = quantity,
    loggedAt = Instant.parse(loggedAt),
    deletedAt = deletedAt?.let { Instant.parse(it) },
    syncedAt = syncedAt?.let { Instant.parse(it) },
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class WantLogDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("activity_id") val activityId: String,
    val quantity: Double,
    @kotlinx.serialization.EncodeDefault @SerialName("points_spent") val pointsSpent: Int = 1,
    @SerialName("device_mode") val deviceMode: String,
    @SerialName("logged_at") val loggedAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
    @SerialName("synced_at") val syncedAt: String?,
)

private fun WantLog.toDto() = WantLogDto(
    id = id,
    userId = userId,
    activityId = activityId,
    quantity = quantity,
    pointsSpent = pointsSpent,
    deviceMode = when (deviceMode) {
        DeviceMode.THIS_DEVICE -> "this_device"
        DeviceMode.OTHER -> "other"
    },
    loggedAt = loggedAt.toString(),
    deletedAt = deletedAt?.toString(),
    syncedAt = syncedAt?.toString(),
)

private fun WantLogDto.toDomain() = WantLog(
    id = id,
    userId = userId,
    activityId = activityId,
    quantity = quantity,
    pointsSpent = pointsSpent,
    deviceMode = when (deviceMode) {
        "this_device" -> DeviceMode.THIS_DEVICE
        else -> DeviceMode.OTHER
    },
    loggedAt = Instant.parse(loggedAt),
    deletedAt = deletedAt?.let { Instant.parse(it) },
    syncedAt = syncedAt?.let { Instant.parse(it) },
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class UserIdentityDto(
    @SerialName("user_id") val userId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
    // @EncodeDefault forces these fields into the JSON payload even when they
    // equal their default. Without it, kotlinx-serialization omits default-valued
    // fields, and Postgrest upsert preserves the stale server value (e.g. unpin
    // would never propagate because is_pinned=false matched the default).
    @kotlinx.serialization.EncodeDefault @SerialName("is_pinned") val isPinned: Boolean = false,
    @kotlinx.serialization.EncodeDefault @SerialName("why_text") val whyText: String? = null,
    @kotlinx.serialization.EncodeDefault @SerialName("removed_at") val removedAt: String? = null,
)

private fun UserIdentityRow.toDto() = UserIdentityDto(
    userId = userId,
    identityId = identityId,
    addedAt = addedAt.toString(),
    isPinned = isPinned,
    whyText = whyText,
    removedAt = removedAt?.toString(),
)

private fun UserIdentityDto.toDomain() = UserIdentityRow(
    userId = userId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    syncedAt = Instant.parse(addedAt), // server-derived; existing convention
    isPinned = isPinned,
    whyText = whyText,
    removedAt = removedAt?.let { Instant.parse(it) },
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
private data class HabitIdentityDto(
    @SerialName("habit_id") val habitId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_from") val effectiveFrom: String? = null,
    @kotlinx.serialization.EncodeDefault @SerialName("effective_to") val effectiveTo: String? = null,
)

private fun HabitIdentityRow.toDto() = HabitIdentityDto(
    habitId = habitId,
    identityId = identityId,
    addedAt = addedAt.toString(),
    updatedAt = updatedAt.toString(),
    effectiveFrom = effectiveFrom?.toString(),
    effectiveTo = effectiveTo?.toString(),
)

private fun HabitIdentityDto.toDomain() = HabitIdentityRow(
    habitId = habitId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    updatedAt = Instant.parse(updatedAt),
    syncedAt = Instant.parse(updatedAt),
    effectiveFrom = effectiveFrom?.let { Instant.parse(it) },
    effectiveTo = effectiveTo?.let { Instant.parse(it) },
)
