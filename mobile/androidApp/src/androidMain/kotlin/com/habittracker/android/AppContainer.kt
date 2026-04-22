package com.habittracker.android

import android.content.Context
import com.habittracker.data.local.DatabaseDriverFactory
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalUserIdStore
import com.habittracker.data.local.SeedData
import com.habittracker.data.remote.SupabaseClientFactory
import com.habittracker.data.repository.LocalHabitLogRepository
import com.habittracker.data.repository.LocalHabitRepository
import com.habittracker.data.repository.LocalIdentityRepository
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.data.repository.SupabaseAuthRepository
import com.habittracker.domain.UserIdentityProvider
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentityUseCase
import com.habittracker.domain.usecase.GetPointBalanceUseCase
import com.habittracker.domain.usecase.IsOnboardedUseCase
import com.habittracker.domain.usecase.LogHabitUseCase
import com.habittracker.domain.usecase.LogWantUseCase
import com.habittracker.domain.usecase.SetupUserHabitsUseCase
import com.habittracker.domain.usecase.SetupUserWantActivitiesUseCase
import com.habittracker.domain.usecase.UndoHabitLogUseCase
import com.habittracker.domain.usecase.UndoWantLogUseCase

class AppContainer(context: Context) {

    private val supabase = SupabaseClientFactory.create(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
    )

    private val db = HabitTrackerDatabase(DatabaseDriverFactory(context).createDriver())
    private val localUserIdStore = LocalUserIdStore(context)

    val authRepository = SupabaseAuthRepository(supabase)
    val identityRepository = LocalIdentityRepository(db)
    val habitRepository = LocalHabitRepository(db)
    val habitLogRepository = LocalHabitLogRepository(db)
    val wantActivityRepository = LocalWantActivityRepository(db)
    val wantLogRepository = LocalWantLogRepository(db)

    val userIdentityProvider = UserIdentityProvider(authRepository, localUserIdStore)

    val logHabitUseCase = LogHabitUseCase(habitLogRepository, habitRepository)
    val logWantUseCase = LogWantUseCase(wantLogRepository, wantActivityRepository)
    val undoHabitLogUseCase = UndoHabitLogUseCase(habitLogRepository)
    val undoWantLogUseCase = UndoWantLogUseCase(wantLogRepository)
    val getPointBalanceUseCase = GetPointBalanceUseCase(habitLogRepository, wantLogRepository, habitRepository, wantActivityRepository)
    val isOnboardedUseCase = IsOnboardedUseCase(habitRepository)
    val getHabitTemplatesForIdentityUseCase = GetHabitTemplatesForIdentityUseCase()
    val setupUserHabitsUseCase = SetupUserHabitsUseCase(habitRepository)
    val setupUserWantActivitiesUseCase = SetupUserWantActivitiesUseCase(wantActivityRepository)

    fun currentUserId(): String = userIdentityProvider.currentUserId()
    fun isAuthenticated(): Boolean = userIdentityProvider.isAuthenticated()

    suspend fun seedLocalDataIfEmpty() {
        if (identityRepository.getAllIdentities().isEmpty()) {
            identityRepository.upsertIdentities(SeedData.identities)
        }
        val userId = currentUserId()
        if (wantActivityRepository.getWantActivities(userId).isEmpty()) {
            SeedData.wantActivities.forEach { activity ->
                wantActivityRepository.saveWantActivity(activity, userId)
            }
        }
    }

    suspend fun migrateLocalToAuthenticated(authUserId: String) {
        val localId = userIdentityProvider.localUserId()
        if (localId == authUserId) return
        db.habitTrackerDatabaseQueries.transaction {
            db.habitTrackerDatabaseQueries.migrateHabitsUserId(authUserId, localId)
            db.habitTrackerDatabaseQueries.migrateHabitLogsUserId(authUserId, localId)
            db.habitTrackerDatabaseQueries.migrateWantLogsUserId(authUserId, localId)
            db.habitTrackerDatabaseQueries.migrateWantActivitiesUserId(authUserId, localId)
        }
    }

    suspend fun clearAuthenticatedUserData(authUserId: String) {
        db.habitTrackerDatabaseQueries.transaction {
            db.habitTrackerDatabaseQueries.clearHabitsForUser(authUserId)
            db.habitTrackerDatabaseQueries.clearHabitLogsForUser(authUserId)
            db.habitTrackerDatabaseQueries.clearWantLogsForUser(authUserId)
            db.habitTrackerDatabaseQueries.clearCustomWantActivitiesForUser(authUserId)
        }
    }
}
