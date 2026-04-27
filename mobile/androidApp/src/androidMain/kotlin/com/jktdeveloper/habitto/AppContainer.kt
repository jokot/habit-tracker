package com.jktdeveloper.habitto

import android.content.Context
import com.habittracker.data.local.DatabaseDriverFactory
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalUserIdStore
import com.habittracker.data.local.SeedData
import com.habittracker.data.local.SyncPreferences
import com.habittracker.data.local.SyncWatermarkStore
import com.habittracker.data.remote.GoogleSignInLauncher
import com.habittracker.data.remote.SupabaseClientFactory
import com.habittracker.data.repository.LocalHabitLogRepository
import com.habittracker.data.repository.LocalHabitRepository
import com.habittracker.data.repository.LocalIdentityRepository
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.data.repository.SupabaseAuthRepository
import com.habittracker.data.sync.PostgrestSupabaseSyncClient
import com.habittracker.data.sync.SupabaseSyncClient
import com.habittracker.data.sync.SyncEngine
import com.habittracker.data.sync.SyncIdentity
import com.habittracker.domain.UserIdentityProvider
import com.habittracker.domain.usecase.ComputeStreakUseCase
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentityUseCase
import com.habittracker.domain.usecase.GetPointBalanceUseCase
import com.habittracker.domain.usecase.IsOnboardedUseCase
import com.habittracker.domain.usecase.LogHabitUseCase
import com.habittracker.domain.usecase.LogWantUseCase
import com.habittracker.domain.usecase.SetupUserHabitsUseCase
import com.habittracker.domain.usecase.SetupUserWantActivitiesUseCase
import com.habittracker.domain.usecase.UndoHabitLogUseCase
import com.habittracker.domain.usecase.UndoWantLogUseCase
import com.jktdeveloper.habitto.notifications.NotificationFiringDateStore
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.habittracker.data.sync.SyncReason
import com.jktdeveloper.habitto.notifications.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable snapshot of who the app is currently acting as. */
data class AuthState(val userId: String, val isAuthenticated: Boolean)

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

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

    val notificationPreferences = NotificationPreferences(appContext)
    val notificationFiringDateStore = NotificationFiringDateStore(appContext)
    val computeStreakUseCase = ComputeStreakUseCase(habitLogRepository)
    val notificationScheduler = NotificationScheduler(appContext, notificationPreferences)

    private val syncPreferences = SyncPreferences(appContext)
    private val watermarks = SyncWatermarkStore(syncPreferences)
    private val supabaseSyncClient: SupabaseSyncClient = PostgrestSupabaseSyncClient(supabase)

    private val syncIdentity = object : SyncIdentity {
        override fun currentUserId(): String = this@AppContainer.currentUserId()
        override fun isAuthenticated(): Boolean = this@AppContainer.isAuthenticated()
    }

    val syncEngine = SyncEngine(
        habitRepository,
        habitLogRepository,
        wantActivityRepository,
        wantLogRepository,
        supabaseSyncClient,
        watermarks,
        syncIdentity,
    )

    val googleSignInLauncher = GoogleSignInLauncher(
        context = appContext,
        webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
    )

    val userIdentityProvider = UserIdentityProvider(authRepository, localUserIdStore)

    val getPointBalanceUseCase = GetPointBalanceUseCase(habitLogRepository, wantLogRepository, habitRepository, wantActivityRepository)
    val logHabitUseCase = LogHabitUseCase(habitLogRepository, habitRepository)
    val logWantUseCase = LogWantUseCase(wantLogRepository, wantActivityRepository, getPointBalanceUseCase)
    val undoHabitLogUseCase = UndoHabitLogUseCase(habitLogRepository)
    val undoWantLogUseCase = UndoWantLogUseCase(wantLogRepository)
    val isOnboardedUseCase = IsOnboardedUseCase(habitRepository)
    val getHabitTemplatesForIdentityUseCase = GetHabitTemplatesForIdentityUseCase()
    val setupUserHabitsUseCase = SetupUserHabitsUseCase(habitRepository)
    val setupUserWantActivitiesUseCase = SetupUserWantActivitiesUseCase(wantActivityRepository)

    private val _authState = MutableStateFlow(snapshotAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun currentUserId(): String = _authState.value.userId
    fun isAuthenticated(): Boolean = _authState.value.isAuthenticated

    /** Re-reads auth session and publishes the new AuthState. Call after sign-in/out. */
    fun refreshAuthState() {
        _authState.value = snapshotAuthState()
    }

    private fun snapshotAuthState(): AuthState = AuthState(
        userId = userIdentityProvider.currentUserId(),
        isAuthenticated = userIdentityProvider.isAuthenticated(),
    )

    suspend fun seedLocalDataIfEmpty() {
        if (identityRepository.getAllIdentities().isEmpty()) {
            identityRepository.upsertIdentities(SeedData.identities)
        }
        // Want activities are NOT auto-seeded — user picks their subset during
        // onboarding's WantsStep, which writes only the selected rows scoped to
        // currentUserId(). Home shows exactly what the user selected.
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

    /**
     * Settings-screen sign-out helper. Pushes pending data, signs out, then wipes local DB.
     * Caller should navigate after this returns.
     */
    suspend fun signOutFromSettings(): Result<Unit> = runCatching {
        // Best-effort push of unsynced rows before clearing.
        val userId = currentUserId()
        if (isAuthenticated()) {
            kotlinx.coroutines.withTimeoutOrNull(5_000) {
                syncEngine.sync(SyncReason.MANUAL)
            }
            authRepository.signOut()
            clearAuthenticatedUserData(userId)
        }
    }

    suspend fun clearAuthenticatedUserData(authUserId: String) {
        db.habitTrackerDatabaseQueries.transaction {
            db.habitTrackerDatabaseQueries.clearHabitsForUser(authUserId)
            db.habitTrackerDatabaseQueries.clearHabitLogsForUser(authUserId)
            db.habitTrackerDatabaseQueries.clearWantLogsForUser(authUserId)
            db.habitTrackerDatabaseQueries.clearCustomWantActivitiesForUser(authUserId)
        }
        // Reset pull watermarks so the next sign-in pulls everything from
        // the cloud instead of skipping rows older than the cached watermark.
        watermarks.reset()
    }
}
