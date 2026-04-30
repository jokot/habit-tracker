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
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PostgrestSupabaseSyncClient(
    private val supabase: SupabaseClient,
) : SupabaseSyncClient {

    override suspend fun upsertHabit(row: Habit) {
        supabase.postgrest.from("habits").upsert(row.toDto())
    }

    override suspend fun upsertWantActivity(row: WantActivity, ownerUserId: String) {
        supabase.postgrest.from("want_activities").upsert(row.toDto(ownerUserId))
    }

    override suspend fun upsertHabitLog(row: HabitLog) {
        supabase.postgrest.from("habit_logs").upsert(row.toDto())
    }

    override suspend fun upsertWantLog(row: WantLog) {
        supabase.postgrest.from("want_logs").upsert(row.toDto())
    }

    override suspend fun fetchHabitsSince(userId: String, sinceMs: Long): List<Habit> =
        supabase.postgrest.from("habits")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("updated_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("updated_at", Order.ASCENDING)
            }
            .decodeList<HabitDto>()
            .map { it.toDomain() }

    override suspend fun fetchWantActivitiesSince(userId: String, sinceMs: Long): List<WantActivity> =
        supabase.postgrest.from("want_activities")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("updated_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("updated_at", Order.ASCENDING)
            }
            .decodeList<WantActivityDto>()
            .map { it.toDomain() }

    override suspend fun fetchHabitLogsSince(userId: String, sinceMs: Long): List<HabitLog> =
        supabase.postgrest.from("habit_logs")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("synced_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("synced_at", Order.ASCENDING)
            }
            .decodeList<HabitLogDto>()
            .map { it.toDomain() }

    override suspend fun fetchWantLogsSince(userId: String, sinceMs: Long): List<WantLog> =
        supabase.postgrest.from("want_logs")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("synced_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("synced_at", Order.ASCENDING)
            }
            .decodeList<WantLogDto>()
            .map { it.toDomain() }

    override suspend fun upsertUserIdentity(row: UserIdentityRow) {
        supabase.postgrest.from("user_identities").upsert(row.toDto())
    }

    override suspend fun upsertHabitIdentity(row: HabitIdentityRow) {
        supabase.postgrest.from("habit_identities").upsert(row.toDto())
    }

    override suspend fun fetchUserIdentitiesSince(userId: String, sinceMs: Long): List<UserIdentityRow> =
        supabase.postgrest.from("user_identities")
            .select {
                filter {
                    eq("user_id", userId)
                    gt("added_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("added_at", Order.ASCENDING)
            }
            .decodeList<UserIdentityDto>()
            .map { it.toDomain() }

    override suspend fun fetchHabitIdentitiesSince(userId: String, sinceMs: Long): List<HabitIdentityRow> {
        // RLS scopes to habits owned by current user; client-side userId arg is for parity
        @Suppress("UNUSED_PARAMETER") val _u = userId
        return supabase.postgrest.from("habit_identities")
            .select {
                filter {
                    gt("added_at", Instant.fromEpochMilliseconds(sinceMs).toString())
                }
                order("added_at", Order.ASCENDING)
            }
            .decodeList<HabitIdentityDto>()
            .map { it.toDomain() }
    }
}

// ---- DTOs ---------------------------------------------------------------

@Serializable
private data class HabitDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("template_id") val templateId: String,
    val name: String,
    val unit: String,
    @SerialName("threshold_per_point") val thresholdPerPoint: Double,
    @SerialName("daily_target") val dailyTarget: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
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
)

@Serializable
private data class WantActivityDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val unit: String,
    @SerialName("cost_per_unit") val costPerUnit: Double,
    @SerialName("is_custom") val isCustom: Boolean,
    @SerialName("updated_at") val updatedAt: String,
)

private fun WantActivity.toDto(ownerUserId: String) = WantActivityDto(
    id = id,
    userId = ownerUserId,
    name = name,
    unit = unit,
    costPerUnit = costPerUnit,
    isCustom = isCustom,
    updatedAt = updatedAt.toString(),
)

private fun WantActivityDto.toDomain() = WantActivity(
    id = id,
    name = name,
    unit = unit,
    costPerUnit = costPerUnit,
    isCustom = isCustom,
    createdByUserId = userId,
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

@Serializable
private data class WantLogDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("activity_id") val activityId: String,
    val quantity: Double,
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
    deviceMode = when (deviceMode) {
        "this_device" -> DeviceMode.THIS_DEVICE
        else -> DeviceMode.OTHER
    },
    loggedAt = Instant.parse(loggedAt),
    deletedAt = deletedAt?.let { Instant.parse(it) },
    syncedAt = syncedAt?.let { Instant.parse(it) },
)

@Serializable
private data class UserIdentityDto(
    @SerialName("user_id") val userId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
)

private fun UserIdentityRow.toDto() = UserIdentityDto(
    userId = userId,
    identityId = identityId,
    addedAt = addedAt.toString(),
)

private fun UserIdentityDto.toDomain() = UserIdentityRow(
    userId = userId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    syncedAt = Instant.parse(addedAt),
)

@Serializable
private data class HabitIdentityDto(
    @SerialName("habit_id") val habitId: String,
    @SerialName("identity_id") val identityId: String,
    @SerialName("added_at") val addedAt: String,
)

private fun HabitIdentityRow.toDto() = HabitIdentityDto(
    habitId = habitId,
    identityId = identityId,
    addedAt = addedAt.toString(),
)

private fun HabitIdentityDto.toDomain() = HabitIdentityRow(
    habitId = habitId,
    identityId = identityId,
    addedAt = Instant.parse(addedAt),
    syncedAt = Instant.parse(addedAt),
)
