package com.jktdeveloper.habitto

import android.content.Context
import com.habittracker.data.local.DatabaseDriverFactory
import com.habittracker.data.local.HabitTrackerDatabase
import com.habittracker.data.local.LocalUserIdStore
import com.habittracker.data.local.PullProgress
import com.habittracker.data.local.SeedData
import com.habittracker.data.local.SyncPreferences
import com.habittracker.data.local.SyncTable
import com.habittracker.data.local.SyncWatermarkStore
import com.habittracker.data.remote.GoogleSignInLauncher
import com.habittracker.data.remote.SupabaseClientFactory
import com.habittracker.data.repository.LocalHabitLogRepository
import com.habittracker.data.repository.LocalHabitRepository
import com.habittracker.data.repository.LocalIdentityRepository
import com.habittracker.data.repository.LocalWantActivityRepository
import com.habittracker.data.repository.LocalWantLogRepository
import com.habittracker.data.repository.LocalWantTimerRepository
import com.habittracker.data.repository.WantTimerRepository
import com.habittracker.data.repository.SupabaseAuthRepository
import com.habittracker.data.sync.PostgrestSupabaseSyncClient
import com.habittracker.data.sync.SupabaseSyncClient
import com.habittracker.data.sync.SyncEngine
import com.habittracker.data.sync.SyncIdentity
import com.habittracker.data.sync.SyncState
import com.habittracker.domain.UserIdentityProvider
import com.habittracker.domain.usecase.ComputeStreakUseCase
import com.habittracker.domain.usecase.ComputeIdentityStatsUseCase
import com.habittracker.domain.usecase.ComputePerHabitStreakUseCase
import com.habittracker.domain.usecase.GetHabitTemplatesForIdentitiesUseCase
import com.habittracker.domain.usecase.GetDayPointsUseCase
import com.habittracker.domain.usecase.GetTodayHabitsUseCase
import com.habittracker.domain.usecase.GetPointBalanceUseCase
import com.habittracker.domain.usecase.GetWidgetDataUseCase
import com.habittracker.domain.usecase.WidgetData
import com.habittracker.domain.usecase.GetUserIdentitiesUseCase
import com.habittracker.domain.usecase.GetUserStreakOnDayUseCase
import com.habittracker.domain.usecase.LinkOnboardingHabitsToIdentitiesUseCase
import com.habittracker.domain.usecase.ObserveUserIdentitiesWithStatsUseCase
import com.habittracker.domain.usecase.SetupUserIdentitiesUseCase
import com.habittracker.domain.usecase.IsOnboardedUseCase
import com.habittracker.domain.usecase.LogHabitUseCase
import com.habittracker.domain.usecase.LogWantUseCase
import com.habittracker.domain.usecase.SetupUserHabitsUseCase
import com.habittracker.domain.usecase.SetupUserWantActivitiesUseCase
import com.habittracker.domain.usecase.UndoHabitLogUseCase
import com.habittracker.domain.usecase.UndoWantLogUseCase
import com.habittracker.domain.usecase.PinIdentityUseCase
import com.habittracker.domain.usecase.UnpinIdentityUseCase
import com.habittracker.domain.usecase.RemoveIdentityUseCase
import com.habittracker.domain.usecase.UpdateIdentityWhyUseCase
import com.habittracker.domain.usecase.AddIdentityWithHabitsUseCase
import com.habittracker.domain.usecase.DeleteHabitUseCase
import com.habittracker.domain.usecase.SaveHabitUseCase
import com.jktdeveloper.habitto.notifications.NotificationTypeId
import com.jktdeveloper.habitto.notifications.PerIdentityReminderScheduler
import com.jktdeveloper.habitto.notifications.SyncFailureCounter
import com.jktdeveloper.habitto.notifications.NotificationChannels
import com.jktdeveloper.habitto.notifications.NotificationFiringDateStore
import com.jktdeveloper.habitto.notifications.NotificationPreferences
import com.jktdeveloper.habitto.notifications.PermissionUtils
import com.habittracker.data.sync.SyncReason
import com.jktdeveloper.habitto.notifications.NotificationScheduler
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.datetime.toLocalDateTime
import com.jktdeveloper.habitto.preferences.AppFlagsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import com.habittracker.domain.model.TodaySection
import com.habittracker.domain.model.readySections
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Observable snapshot of who the app is currently acting as. */
data class AuthState(val userId: String, val isAuthenticated: Boolean)

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    private val supabase = SupabaseClientFactory.create(
        url = BuildConfig.SUPABASE_URL,
        key = BuildConfig.SUPABASE_ANON_KEY,
    )

    private val driverFactory = DatabaseDriverFactory(context)
    private val db = HabitTrackerDatabase(driverFactory.createDriver())
    private val localUserIdStore = LocalUserIdStore(context)
    private val lastAuthUserStore = LastAuthUserStore(context)

    val authRepository = SupabaseAuthRepository(supabase)
    val identityRepository = LocalIdentityRepository(db)
    val habitRepository = LocalHabitRepository(db)
    val habitLogRepository = LocalHabitLogRepository(db)
    val wantActivityRepository = LocalWantActivityRepository(db)
    val wantLogRepository = LocalWantLogRepository(db)
    private val heldAccounts = HeldAccounts(
        store = HeldAccountStore(context),
        countUnsynced = { userId -> countUnsyncedChanges(userId) },
        deleteUserRows = { userId -> deleteUserRows(userId) },
    )
    val wantTimerRepository: WantTimerRepository = LocalWantTimerRepository(db)
    val perIdentityReminderScheduler = PerIdentityReminderScheduler(appContext)
    val syncFailureCounter = SyncFailureCounter(appContext)

    val notificationPreferences = NotificationPreferences(appContext)
    val notificationFiringDateStore = NotificationFiringDateStore(appContext)
    val appFlagsPreferences = AppFlagsPreferences(appContext)
    val computeStreakUseCase = ComputeStreakUseCase(habitLogRepository, habitRepository)
    val getTodayHabitsUseCase = GetTodayHabitsUseCase(habitRepository, habitLogRepository)
    val notificationScheduler = NotificationScheduler(appContext, notificationPreferences)

    private val syncPreferences = SyncPreferences(appContext)
    private val watermarks = SyncWatermarkStore(syncPreferences)
    private val supabaseSyncClient: SupabaseSyncClient = PostgrestSupabaseSyncClient(supabase)

    private val syncIdentity = object : SyncIdentity {
        override fun currentUserId(): String = this@AppContainer.currentUserId()
        override fun isAuthenticated(): Boolean = this@AppContainer.isAuthenticated()
        override fun hasLiveSession(): Boolean = authRepository.hasLiveSession()
    }

    val syncEngine = SyncEngine(
        habitRepository,
        habitLogRepository,
        wantActivityRepository,
        wantLogRepository,
        identityRepository,
        supabaseSyncClient,
        watermarks,
        syncIdentity,
    )

    /** Which tables the first sync on this device has pulled so far. */
    val pullProgress: StateFlow<PullProgress> = syncEngine.pullProgress

    val googleSignInLauncher = GoogleSignInLauncher(
        context = appContext,
        webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
    )

    val userIdentityProvider = UserIdentityProvider(authRepository, localUserIdStore)

    val getUserStreakOnDayUseCase = GetUserStreakOnDayUseCase(computeStreakUseCase)
    val getPointBalanceUseCase = GetPointBalanceUseCase(
        habitLogRepository, wantLogRepository, habitRepository, wantActivityRepository,
        getUserStreakOnDayUseCase = getUserStreakOnDayUseCase,
    )
    val getDayPointsUseCase = GetDayPointsUseCase(
        habitLogRepository, wantLogRepository, habitRepository, wantActivityRepository,
        getUserStreakOnDayUseCase = getUserStreakOnDayUseCase,
    )
    val logHabitUseCase = LogHabitUseCase(habitLogRepository, habitRepository)
    val logWantUseCase = LogWantUseCase(
        wantLogRepository = wantLogRepository,
        wantActivityRepository = wantActivityRepository,
        getPointBalanceUseCase = getPointBalanceUseCase,
        getUserStreakOnDayUseCase = getUserStreakOnDayUseCase,
    )
    val getWidgetDataUseCase = GetWidgetDataUseCase(
        getTodayHabitsUseCase = getTodayHabitsUseCase,
        wantActivityRepository = wantActivityRepository,
        getPointBalanceUseCase = getPointBalanceUseCase,
        computeStreakUseCase = computeStreakUseCase,
        habitRepository = habitRepository,
        habitLogRepository = habitLogRepository,
        wantLogRepository = wantLogRepository,
    )
    val wantTimerController = com.jktdeveloper.habitto.timer.WantTimerController(
        context = appContext,
        repository = wantTimerRepository,
        wantActivityRepository = wantActivityRepository,
        logWantUseCase = logWantUseCase,
        getPointBalanceUseCase = getPointBalanceUseCase,
        notificationPreferences = notificationPreferences,
    )
    val wantTimerRecovery = com.jktdeveloper.habitto.timer.WantTimerRecovery(
        context = appContext,
        timerRepo = wantTimerRepository,
        wantActivityRepo = wantActivityRepository,
        logWantUseCase = logWantUseCase,
        notificationPreferences = notificationPreferences,
    )
    val undoHabitLogUseCase = UndoHabitLogUseCase(habitLogRepository)
    val undoWantLogUseCase = UndoWantLogUseCase(wantLogRepository)
    val isOnboardedUseCase = IsOnboardedUseCase(habitRepository)
    val getUserIdentitiesUseCase = GetUserIdentitiesUseCase(identityRepository)
    val setupUserIdentitiesUseCase = SetupUserIdentitiesUseCase(identityRepository)
    val getHabitTemplatesForIdentitiesUseCase = GetHabitTemplatesForIdentitiesUseCase()
    val linkOnboardingHabitsToIdentitiesUseCase = LinkOnboardingHabitsToIdentitiesUseCase(identityRepository)
    val computeIdentityStatsUseCase = ComputeIdentityStatsUseCase(
        habitLogRepo = habitLogRepository,
        identityRepo = identityRepository,
    )
    val computePerHabitStreakUseCase = ComputePerHabitStreakUseCase(habitLogRepository, habitRepository)
    val observeUserIdentitiesWithStatsUseCase = ObserveUserIdentitiesWithStatsUseCase(
        identityRepo = identityRepository,
        statsUseCase = computeIdentityStatsUseCase,
    )
    val setupUserHabitsUseCase = SetupUserHabitsUseCase(habitRepository)
    val setupUserWantActivitiesUseCase = SetupUserWantActivitiesUseCase(
        wantActivityRepository,
        SeedData.wantActivities,
    )
    val pinIdentityUseCase = PinIdentityUseCase(identityRepository)
    val unpinIdentityUseCase = UnpinIdentityUseCase(identityRepository)
    val removeIdentityUseCase = RemoveIdentityUseCase(identityRepository)
    val updateIdentityWhyUseCase = UpdateIdentityWhyUseCase(identityRepository)
    val addIdentityWithHabitsUseCase = AddIdentityWithHabitsUseCase(
        habitRepo = habitRepository,
        identityRepo = identityRepository,
        templates = getHabitTemplatesForIdentitiesUseCase,
    )
    val saveHabitUseCase = SaveHabitUseCase(habitRepository, identityRepository)
    val deleteHabitUseCase = DeleteHabitUseCase(habitRepository)

    private val _authState = MutableStateFlow(snapshotAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * What every home-screen widget renders, shared by all of them.
     *
     * Widgets collect this inside their Glance composition, so a log repaints them
     * directly instead of each tap having to push an update at all seven widgets.
     *
     * Shared, not per-widget: seven live compositions must not mean seven sets of DB
     * observers and seven streak computations. `WhileSubscribed` also means no work at
     * all while nothing is collecting — the common case, since most users pin none.
     *
     * Slots are unbounded here because widgets slice to their own size at layout time,
     * and that size is only knowable inside the composition.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val widgetData: StateFlow<WidgetData?> = authState
        .map { it.userId }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            getWidgetDataUseCase.observe(userId, habitSlots = Int.MAX_VALUE, wantSlots = Int.MAX_VALUE)
        }
        .stateIn(applicationScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Today sections whose data is all local. Streak History also waits for [TodaySection.STREAK]. */
    val readySections: StateFlow<Set<TodaySection>> =
        combine(authState, pullProgress) { auth, progress -> progress.readySections(auth.isAuthenticated) }
            .stateIn(
                applicationScope,
                SharingStarted.Eagerly,
                pullProgress.value.readySections(isAuthenticated()),
            )

    // The session end stays pending until the UI consumes it. A session end at a cold
    // start can come before AppNavigation collects, and the start screen must know of it.
    private val _pendingSessionExpired = MutableStateFlow<SessionExpired?>(null)
    val pendingSessionExpired: StateFlow<SessionExpired?> = _pendingSessionExpired.asStateFlow()

    fun consumeSessionExpired() {
        _pendingSessionExpired.value = null
    }

    private val serverSessionEnd = ServerSessionEnd(lastAuthUserStore::rememberedUserId)
    private val sessionEndLock = Mutex()

    fun currentUserId(): String = _authState.value.userId
    fun isAuthenticated(): Boolean = _authState.value.isAuthenticated
    fun currentAccountEmail(): String? = authRepository.currentEmail()

    /** Re-reads auth session and publishes the new AuthState. Call after sign-in/out. */
    fun refreshAuthState() {
        _authState.value = snapshotAuthState()
    }

    private fun snapshotAuthState(): AuthState = AuthState(
        // Same fallback chain as UserIdentityProvider.currentUserId(), with the last
        // authenticated id in between: in a cold process — a widget update, a reminder
        // worker — supabase-kt hasn't restored the session yet, and dropping straight to
        // the guest id queries rows that sign-in migrated away. See LastAuthUserStore.
        userId = lastAuthUserStore.resolve(authRepository.currentUserId(), authRepository.currentEmail()) {
            userIdentityProvider.localUserId()
        },
        isAuthenticated = userIdentityProvider.isAuthenticated(),
    )

    suspend fun seedLocalDataIfEmpty() {
        if (identityRepository.getAllIdentities().isEmpty()) {
            identityRepository.upsertIdentities(SeedData.identities)
        }
        // Want activities: reconcile the canonical 14-item seed list. Name-match
        // against existing rows means already-pulled seeds are skipped. Brand-new
        // users (or guests pre-auth) with empty local state get the full 14
        // inserted with fresh per-user UUIDs.
        reconcileJob?.cancel()
        if (!isAuthenticated() || SyncTable.WANT_ACTIVITIES in pullProgress.value.tables) {
            reconcileWantActivities(currentUserId())
            return
        }
        // Signed in, and the user's wants are not pulled yet: reconcile after they are,
        // so it sees the seed wants the user already owns. Reconcile-before-pull would
        // push 14 random-UUID rows every install, accumulating duplicates server-side
        // across reinstalls. This waits in the background, so it never blocks app start.
        val userId = currentUserId()
        reconcileJob = applicationScope.launch {
            pullProgress.first { SyncTable.WANT_ACTIVITIES in it.tables }
            if (currentUserId() == userId) reconcileWantActivities(userId)
        }
    }

    /** The pending deferred reconcile. A newer call replaces it, so two never race. */
    private var reconcileJob: Job? = null

    private suspend fun reconcileWantActivities(userId: String) {
        runCatching { setupUserWantActivitiesUseCase.reconcile(userId) }
            .onFailure { e -> android.util.Log.w("AppContainer", "Want-activity reconcile failed", e) }
    }

    /** Starts a sync that outlives the caller's screen. Results arrive through the local flows. */
    fun syncInBackground(reason: SyncReason) {
        applicationScope.launch { syncEngine.sync(reason) }
    }

    /**
     * Reconciles the local guest dataset with the authenticated user's server dataset.
     *
     * - **New user** (server empty): migrate local guest rows up to the auth userId so the next
     *   sync push sends them. First-time sign-up flow.
     * - **Existing user** (server has habits): discard the local guest dataset; the next sync
     *   pull will populate from the server. Without this branch, the guest's locally-created
     *   rows would be pushed under the existing user's id, producing duplicates.
     */
    suspend fun migrateLocalToAuthenticated(authUserId: String) {
        // Before the guest migration and the first sync: rows of another held account
        // must go before this account pulls. See HeldAccounts.
        // Kept rows make this an existing user, even when the server has no habits yet.
        // Moving guest rows onto them could clash on the LocalUserIdentity key.
        val keptHeldRows = heldAccounts.onSignedIn(authUserId)
        val localId = userIdentityProvider.localUserId()
        if (localId == authUserId) return
        val serverHasData = keptHeldRows || runCatching {
            supabaseSyncClient.fetchHabitsSince(authUserId, 0L).isNotEmpty()
        }.getOrDefault(false)
        if (serverHasData) {
            // Existing user — drop local guest data, server is the source of truth.
            db.habitTrackerDatabaseQueries.transaction {
                db.habitTrackerDatabaseQueries.clearHabitIdentitiesForUser(localId)
                db.habitTrackerDatabaseQueries.deleteAllUserIdentitiesForUser(localId)
                db.habitTrackerDatabaseQueries.clearHabitsForUser(localId)
                db.habitTrackerDatabaseQueries.clearHabitLogsForUser(localId)
                db.habitTrackerDatabaseQueries.clearWantLogsForUser(localId)
                db.habitTrackerDatabaseQueries.clearCustomWantActivitiesForUser(localId)
            }
            watermarks.reset()
            return
        }
        // New user — migrate local guest rows up.
        db.habitTrackerDatabaseQueries.transaction {
            db.habitTrackerDatabaseQueries.migrateHabitsUserId(authUserId, localId)
            db.habitTrackerDatabaseQueries.migrateHabitLogsUserId(authUserId, localId)
            db.habitTrackerDatabaseQueries.migrateWantLogsUserId(authUserId, localId)
            db.habitTrackerDatabaseQueries.migrateWantActivitiesUserId(authUserId, localId)
            db.habitTrackerDatabaseQueries.migrateUserIdentitiesUserId(authUserId, localId)
            // LocalHabitIdentity rows reference habitIds (not userIds) — migrate-by-userId
            // is unnecessary because the underlying habit rows still have the same id after
            // their userId flips above.
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
            // The user is still remembered after signOut() returns, so mark this sign-out as ours.
            serverSessionEnd.ownSignOut {
                authRepository.signOut()
                clearAuthenticatedUserData(userId)
            }
            refreshAuthState()
        }
    }

    suspend fun clearAuthenticatedUserData(authUserId: String) {
        deleteUserRows(authUserId)
        forgetSyncedUser()
    }

    private fun deleteUserRows(userId: String) {
        db.habitTrackerDatabaseQueries.transaction {
            // Identity tables first — habit_identities subquery references LocalHabit.userId,
            // so it must run before LocalHabit rows are deleted.
            db.habitTrackerDatabaseQueries.clearHabitIdentitiesForUser(userId)
            db.habitTrackerDatabaseQueries.deleteAllUserIdentitiesForUser(userId)
            db.habitTrackerDatabaseQueries.clearHabitsForUser(userId)
            db.habitTrackerDatabaseQueries.clearHabitLogsForUser(userId)
            db.habitTrackerDatabaseQueries.clearWantLogsForUser(userId)
            db.habitTrackerDatabaseQueries.clearCustomWantActivitiesForUser(userId)
        }
    }

    private fun forgetSyncedUser() {
        // Reset pull watermarks so the next sign-in pulls everything from
        // the cloud instead of skipping rows older than the cached watermark.
        watermarks.reset()
        // The user is signed out, so stop claiming to be them on the
        // next cold start. Covers every sign-out path — they all come through here.
        lastAuthUserStore.clear()
    }

    /** Rows with `syncedAt = null` across the six synced tables. */
    private suspend fun countUnsyncedChanges(userId: String): Int =
        habitRepository.getUnsyncedFor(userId).size +
            wantActivityRepository.getUnsyncedFor(userId).size +
            habitLogRepository.getUnsyncedFor(userId).size +
            wantLogRepository.getUnsyncedFor(userId).size +
            identityRepository.getUnsyncedUserIdentitiesFor(userId).size +
            identityRepository.getUnsyncedHabitIdentitiesFor(userId).size

    suspend fun heldAccountSummary(): HeldAccountSummary? = heldAccounts.summary()

    private fun startSessionGuard() {
        applicationScope.launch {
            syncEngine.syncState
                .filterIsInstance<SyncState.Error>()
                .filter { it.message == "Session expired" }
                .distinctUntilChanged()
                .collect { handleSessionExpired() }
        }
        // A refresh that the server rejects makes no failed push. Without this, the app
        // shows the data of the user as signed out, also after a cold start (#33).
        applicationScope.launch {
            authRepository.noSession.collect {
                if (serverSessionEnd.endedByServer()) endSession()
            }
        }
        // An offline cold start keeps the session without a token, so no sync could run.
        // When the network comes back and supabase-kt refreshes the token, sync at once.
        applicationScope.launch {
            authRepository.sessionRecovered.collect {
                refreshAuthState()
                runCatching { syncEngine.sync(SyncReason.APP_FOREGROUND) }
            }
        }
    }

    /**
     * At a cold start, the session guard and the start screen react to the same status.
     * Call before the start screen is chosen, so that the guard finished its work.
     */
    suspend fun endSessionIfServerEnded() {
        if (authRepository.hasNoSession() && serverSessionEnd.endedByServer()) endSession()
    }

    private suspend fun handleSessionExpired() {
        if (!isAuthenticated()) return
        // Without a token, tryRefreshSession() fails at once and the session would end.
        // supabase-kt retries the refresh itself, and sessionRecovered then syncs.
        if (!authRepository.hasLiveSession()) return
        val refresh = authRepository.tryRefreshSession()
        if (refresh.isSuccess) {
            runCatching { syncEngine.sync(SyncReason.MANUAL) }
            return
        }
        endSession()
    }

    /**
     * Ends a session that no refresh can save, so no push is possible. HeldAccounts keeps
     * unsynced changes for the next sign-in instead of deleting them (#33).
     */
    private suspend fun endSession() = sessionEndLock.withLock {
        // Both guards can find the same end. The first one forgets the user.
        val userId = lastAuthUserStore.rememberedUserId() ?: return@withLock
        // supabase-kt often cleared the session already, so the email comes from the store.
        val email = authRepository.currentEmail() ?: lastAuthUserStore.lastEmail()
        val unsynced = heldAccounts.onSessionExpired(userId, email)
        forgetSyncedUser()
        runCatching { authRepository.signOut() }
        refreshAuthState()
        _pendingSessionExpired.value = SessionExpired(unsynced)
    }

    private fun startSyncNotifier() {
        applicationScope.launch {
            var sawAnyPullSinceLogin = false
            syncEngine.syncState.collect { state ->
                val prefs = notificationPreferences.current()
                if (!prefs.masterEnabled) return@collect
                if (!PermissionUtils.hasNotificationPermission(appContext)) return@collect
                when (state) {
                    is SyncState.Synced -> {
                        syncFailureCounter.reset()
                        if (!sawAnyPullSinceLogin && state.pulled > 0
                            && prefs.isEnabled(NotificationTypeId.CLOUD_RESTORE_COMPLETE)
                        ) {
                            val today = kotlinx.datetime.Clock.System.now()
                                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
                            val key = NotificationFiringDateStore.EVENT_CLOUD_RESTORE
                            if (notificationFiringDateStore.getLastFired(key) != today) {
                                fireSystemNotif(NOTIF_CLOUD_RESTORE,
                                    "Cloud restore complete — your data is back.")
                                notificationFiringDateStore.setLastFired(key, today)
                            }
                        }
                        sawAnyPullSinceLogin = true
                    }
                    is SyncState.Error -> {
                        if (state.message == "Session expired"
                            && prefs.isEnabled(NotificationTypeId.SESSION_EXPIRED)
                        ) {
                            fireSystemNotif(NOTIF_SESSION_EXPIRED,
                                "Signed out — please sign in again to keep syncing.")
                        } else if (state.message != "Session expired"
                            && prefs.isEnabled(NotificationTypeId.SYNC_FAILED_PERSISTENT)
                        ) {
                            if (syncFailureCounter.incrementAndShouldFire()) {
                                fireSystemNotif(NOTIF_SYNC_FAILED,
                                    "Sync has been failing — check your connection.")
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun startPerIdentityReconciler() {
        applicationScope.launch {
            var previous: Set<String> = emptySet()
            combine(
                identityRepository.observeUserIdentities(currentUserId()),
                notificationPreferences.flow,
            ) { ids, prefs -> ids to prefs }
                .collect { (identities, prefs) ->
                    val active = if (
                        prefs.masterEnabled
                        && prefs.isEnabled(NotificationTypeId.DAILY_REMINDER_PER_IDENTITY)
                    ) identities.map { it.id }.toSet() else emptySet()
                    val minutes = prefs.minutesOfDay(NotificationTypeId.DAILY_REMINDER_PER_IDENTITY) ?: (17 * 60 + 30)
                    perIdentityReminderScheduler.reconcile(active, minutes, previous)
                    previous = active
                }
        }
    }

    private fun fireSystemNotif(id: Int, body: String) {
        val builder = NotificationCompat.Builder(appContext, NotificationChannels.SYSTEM)
            .setSmallIcon(com.jktdeveloper.habitto.R.drawable.ic_notification)
            .setContentTitle("Habitto")
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationManagerCompat.from(appContext).notify(id, builder.build())
    }

    private companion object {
        const val NOTIF_SESSION_EXPIRED = 4101
        const val NOTIF_CLOUD_RESTORE = 4102
        const val NOTIF_SYNC_FAILED = 4103
    }

    init {
        // If the DB was wiped due to a schema version bump (dev-only migration path),
        // reset sync watermarks so the next pull fetches everything from the server.
        if (driverFactory.lastCreateWasWipe) {
            watermarks.reset()
        }
        startSessionGuard()
        startSyncNotifier()
        startPerIdentityReconciler()
    }
}

/** The session ended with no push. [unsyncedCount] is the number of changes kept, null if unknown. */
data class SessionExpired(val unsyncedCount: Int?)
