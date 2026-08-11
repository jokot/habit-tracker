package com.jktdeveloper.habitto.ui.navigation

import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jktdeveloper.habitto.AppContainer
import com.jktdeveloper.habitto.ui.auth.AuthScreen
import com.jktdeveloper.habitto.ui.auth.AuthViewModel
import com.jktdeveloper.habitto.ui.home.HomeScreen
import com.jktdeveloper.habitto.ui.home.HomeViewModel
import com.jktdeveloper.habitto.ui.habit.HabitDetailScreen
import com.jktdeveloper.habitto.ui.habit.HabitDetailViewModel
import com.jktdeveloper.habitto.ui.habit.HabitListScreen
import com.jktdeveloper.habitto.ui.habit.HabitListViewModel
import com.jktdeveloper.habitto.ui.identity.AddIdentityScreen
import com.jktdeveloper.habitto.ui.identity.AddIdentityViewModel
import com.jktdeveloper.habitto.ui.onboarding.OnboardingScreen
import com.jktdeveloper.habitto.ui.onboarding.OnboardingViewModel
import com.jktdeveloper.habitto.devtools.devToolsRoute
import com.habittracker.data.sync.SyncReason
import kotlinx.coroutines.withTimeoutOrNull

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object StreakHistory : Screen("streak-history")
    object You : Screen("you")
    object IdentityList : Screen("identity_list")
    object IdentityDetail : Screen("identity_detail/{identityId}") {
        const val ARG_ID = "identityId"
        fun route(id: String) = "identity_detail/$id"
    }
    object AddIdentity : Screen("add_identity")
    object HabitList : Screen("habit_list")
    object HabitDetail : Screen("habit_detail/{habitId}") {
        const val ARG_ID = "habitId"
        fun route(id: String) = "habit_detail/$id"
    }
    object HabitForm : Screen("habit_form?habitId={habitId}&identityId={identityId}") {
        const val ARG_HABIT_ID = "habitId"
        const val ARG_IDENTITY_ID = "identityId"
        fun route(habitId: String? = null, identityId: String? = null): String {
            val params = buildList {
                habitId?.let { add("habitId=$it") }
                identityId?.let { add("identityId=$it") }
            }.joinToString("&")
            return if (params.isEmpty()) "habit_form" else "habit_form?$params"
        }
    }
    object ExchangeRate : Screen("exchange_rate")
    object DevTools : Screen("dev_tools")
    object WantList : Screen("want_list")
    object WantDetail : Screen("want_detail/{wantId}?openTimer={openTimer}") {
        const val ARG_ID = "wantId"

        /** Set by the widget: land on the detail with the duration sheet already up. */
        const val ARG_OPEN_TIMER = "openTimer"
        fun route(id: String, openTimer: Boolean = false) = "want_detail/$id?openTimer=$openTimer"
    }
    object WantForm : Screen("want_form?wantId={wantId}") {
        const val ARG_ID = "wantId"
        fun route(id: String? = null) = if (id == null) "want_form" else "want_form?wantId=$id"
    }
    object NotificationsSettings : Screen("notifications-settings")
    object WantTimer : Screen("want-timer/{activityId}") {
        const val ARG_ID = "activityId"
        fun route(activityId: String) = "want-timer/$activityId"
    }
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Wait for supabase-kt to finish loading any persisted session from storage
        // before deciding who the current user is. Without this, currentUserId()
        // returns the local guest UUID until the session lazily loads, and we
        // falsely route authenticated users through onboarding.
        withTimeoutOrNull(3_000L) {
            container.authRepository.awaitSessionRestored()
        }
        container.refreshAuthState()

        container.seedLocalDataIfEmpty()
        val userId = container.currentUserId()

        // Fresh device with existing session — try a 2s cloud restore before routing.
        if (container.isAuthenticated() &&
            container.habitRepository.getHabitsForUser(userId).isEmpty()
        ) {
            withTimeoutOrNull(2_000L) {
                container.syncEngine.sync(SyncReason.POST_SIGN_IN)
            }
        }

        startDestination = if (container.isOnboardedUseCase.execute(userId)) {
            Screen.Home.route
        } else {
            Screen.Onboarding.route
        }
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        container.sessionExpiredEvents.collect {
            Toast.makeText(context, "Session expired — sign in again", Toast.LENGTH_LONG).show()
            navController.navigate(Screen.Auth.route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    val start = startDestination
    if (start == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // MainActivity is singleTop, so a deep link fired while the app is already running never
    // re-runs onCreate — it arrives in onNewIntent, and NavHost only ever reads the intent its
    // activity was created with. Without this, widget and notification links are silently
    // dropped on a warm start. Registered below the startDestination gate so the graph exists.
    DisposableEffect(navController) {
        val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>()
            .first()
        val listener = Consumer<Intent> { navController.handleDeepLink(it) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomNav = currentRoute in BOTTOM_NAV_ROUTES

    Scaffold(
        bottomBar = {
            if (showBottomNav) BottomNav(currentRoute = currentRoute, navController = navController)
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding),
        ) {

            composable(Screen.Auth.route) {
                val vm = viewModel { AuthViewModel(container) }
                AuthScreen(
                    viewModel = vm,
                    launcher = container.googleSignInLauncher,
                    onSuccess = {
                        // After sign-in, go to Home and wipe any Onboarding/Auth from the stack.
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.Onboarding.route) {
                val vm = viewModel { OnboardingViewModel(container) }
                OnboardingScreen(
                    viewModel = vm,
                    onFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                    onSignIn = { navController.navigate(Screen.Auth.route) },
                )
            }

            composable(Screen.Home.route) {
                val vm = viewModel { HomeViewModel(container) }
                HomeScreen(
                    viewModel = vm,
                    onSignIn = { navController.navigate(Screen.Auth.route) },
                    onOpenStreakHistory = { navController.navigate(Screen.StreakHistory.route) },
                    onIdentityClick = { id -> navController.navigate(Screen.IdentityDetail.route(id)) },
                    onIdentitiesClick = { navController.navigate(Screen.IdentityList.route) },
                    onOpenExchangeRate = { navController.navigate(Screen.ExchangeRate.route) },
                    onOpenWantDetail = { id, openTimer ->
                        navController.navigate(Screen.WantDetail.route(id, openTimer))
                    },
                    onOpenTimer = { id -> navController.navigate(Screen.WantTimer.route(id)) },
                )
                // Ask here, after onboarding: the user has habits by now, so the
                // notifications on offer mean something.
                com.jktdeveloper.habitto.ui.onboarding.NotificationPermissionPromptHost()
            }

            composable(Screen.Settings.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.settings.SettingsViewModel(
                        notificationPrefs = container.notificationPreferences,
                        scheduler = container.notificationScheduler,
                        signOutAction = { container.signOutFromSettings() },
                        unsyncedCountProvider = {
                            val userId = container.currentUserId()
                            val habits = container.habitLogRepository.getUnsyncedFor(userId).size
                            val wants = container.wantLogRepository.getUnsyncedFor(userId).size
                            habits + wants
                        },
                        onSignOutComplete = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        },
                    )
                }
                val showDialog by vm.showLogoutDialog.collectAsState()
                val unsyncedCount by vm.logoutUnsyncedCount.collectAsState()
                val isSigningOut by vm.isSigningOut.collectAsState()
                if (showDialog) {
                    com.jktdeveloper.habitto.ui.auth.LogoutDialog(
                        unsyncedCount = unsyncedCount,
                        onConfirm = { force -> vm.confirmSignOut(force) },
                        onDismiss = vm::dismissLogoutDialog,
                        isProcessing = isSigningOut,
                    )
                }
                val authState by container.authState.collectAsState()
                val email = remember(authState) { container.currentAccountEmail() }
                com.jktdeveloper.habitto.ui.settings.SettingsScreen(
                    viewModel = vm,
                    isAuthenticated = authState.isAuthenticated,
                    accountEmail = email,
                    onSignOut = { vm.beginSignOut() },
                    onSignIn = { navController.navigate(Screen.Auth.route) },
                    onBack = { navController.popBackStack() },
                    onOpenNotificationsSettings = { navController.navigate(Screen.NotificationsSettings.route) },
                    onOpenDevTools = if (com.jktdeveloper.habitto.BuildConfig.DEBUG) {
                        { navController.navigate(Screen.DevTools.route) }
                    } else null,
                )
            }

            composable(Screen.NotificationsSettings.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.settings.NotificationsSettingsViewModel(container)
                }
                com.jktdeveloper.habitto.ui.settings.NotificationsSettingsScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.StreakHistory.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.streak.StreakHistoryViewModel(
                        useCase = container.computeStreakUseCase,
                        getDayPointsUseCase = container.getDayPointsUseCase,
                        userIdProvider = { container.currentUserId() },
                    )
                }
                com.jktdeveloper.habitto.ui.streak.StreakHistoryScreen(
                    viewModel = vm,
                )
            }

            composable(Screen.ExchangeRate.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.exchange.ExchangeRateViewModel(container)
                }
                com.jktdeveloper.habitto.ui.exchange.ExchangeRateScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.WantList.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.want.WantListViewModel(container)
                }
                com.jktdeveloper.habitto.ui.want.WantListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onAddWant = { navController.navigate(Screen.WantForm.route()) },
                    onEditWant = { id -> navController.navigate(Screen.WantForm.route(id)) },
                    onOpenDetail = { id -> navController.navigate(Screen.WantDetail.route(id)) },
                )
            }

            composable(
                route = Screen.WantDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.WantDetail.ARG_ID) {
                        type = androidx.navigation.NavType.StringType
                    },
                    androidx.navigation.navArgument(Screen.WantDetail.ARG_OPEN_TIMER) {
                        type = androidx.navigation.NavType.BoolType
                        defaultValue = false
                    },
                ),
                deepLinks = listOf(
                    androidx.navigation.navDeepLink {
                        uriPattern = "com.jktdeveloper.habitto://want-detail/{wantId}?openTimer={openTimer}"
                    },
                ),
            ) { entry ->
                val wantId = entry.arguments?.getString(Screen.WantDetail.ARG_ID).orEmpty()
                val openTimer = entry.arguments?.getBoolean(Screen.WantDetail.ARG_OPEN_TIMER) == true
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.want.WantDetailViewModel(wantId, container)
                }
                com.jktdeveloper.habitto.ui.want.WantDetailScreen(
                    viewModel = vm,
                    autoOpenTimer = openTimer,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Screen.WantForm.route(wantId)) },
                    onOpenTimer = { id -> navController.navigate(Screen.WantTimer.route(id)) },
                )
            }

            composable(
                route = Screen.WantForm.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.WantForm.ARG_ID) {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val wantId = entry.arguments?.getString(Screen.WantForm.ARG_ID)
                val mode = if (wantId == null) {
                    com.jktdeveloper.habitto.ui.want.FormMode.New
                } else {
                    com.jktdeveloper.habitto.ui.want.FormMode.Edit(wantId)
                }
                val vm = androidx.lifecycle.viewmodel.compose.viewModel(
                    key = "want_form_${wantId ?: "new"}",
                ) {
                    com.jktdeveloper.habitto.ui.want.WantFormViewModel(mode, container)
                }
                com.jktdeveloper.habitto.ui.want.WantFormScreen(
                    viewModel = vm,
                    onClose = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.WantTimer.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.WantTimer.ARG_ID) {
                        type = androidx.navigation.NavType.StringType
                    },
                ),
                deepLinks = listOf(
                    androidx.navigation.navDeepLink { uriPattern = "com.jktdeveloper.habitto://want-timer/{activityId}" },
                ),
            ) { entry ->
                val activityId = entry.arguments?.getString(Screen.WantTimer.ARG_ID).orEmpty()
                val vm = remember { com.jktdeveloper.habitto.ui.want.WantTimerViewModel(container) }
                com.jktdeveloper.habitto.ui.want.WantTimerScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                )
            }

            devToolsRoute(
                container = container,
                routePath = Screen.DevTools.route,
                onBack = { navController.popBackStack() },
            )

            composable(Screen.You.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.you.YouHubViewModel(container)
                }
                com.jktdeveloper.habitto.ui.you.YouHubScreen(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onOpenIdentities = { navController.navigate(Screen.IdentityList.route) },
                    onHabitsClick = { navController.navigate(Screen.HabitList.route) },
                    onOpenExchangeRate = { navController.navigate(Screen.ExchangeRate.route) },
                    onOpenWants = { navController.navigate(Screen.WantList.route) },
                )
            }

            composable(Screen.IdentityList.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.identity.IdentityListViewModel(container)
                }
                com.jktdeveloper.habitto.ui.identity.IdentityListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onIdentityClick = { id -> navController.navigate(Screen.IdentityDetail.route(id)) },
                    onAddIdentityClick = { navController.navigate(Screen.AddIdentity.route) },
                )
            }

            composable(
                route = Screen.IdentityDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.IdentityDetail.ARG_ID) {
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString(Screen.IdentityDetail.ARG_ID) ?: return@composable
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.identity.IdentityDetailViewModel(container, id)
                }
                com.jktdeveloper.habitto.ui.identity.IdentityDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onRemoveSuccess = { navController.popBackStack() },
                    onHabitClick = { hid -> navController.navigate(Screen.HabitDetail.route(hid)) },
                    onAddHabit = { navController.navigate(Screen.HabitForm.route(identityId = id)) },
                )
            }

            composable(Screen.AddIdentity.route) {
                val vm = viewModel { AddIdentityViewModel(container) }
                AddIdentityScreen(
                    viewModel = vm,
                    onClose = { navController.popBackStack() },
                    onCommitSuccess = {
                        navController.navigate(Screen.IdentityList.route) {
                            popUpTo(Screen.IdentityList.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Screen.HabitList.route) {
                val vm = viewModel { HabitListViewModel(container) }
                HabitListScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onHabitClick = { id -> navController.navigate(Screen.HabitDetail.route(id)) },
                    onAddHabit = { navController.navigate(Screen.HabitForm.route()) },
                )
            }

            composable(
                route = Screen.HabitDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.HabitDetail.ARG_ID) {
                        type = androidx.navigation.NavType.StringType
                    },
                ),
            ) { entry ->
                val habitId = entry.arguments?.getString(Screen.HabitDetail.ARG_ID).orEmpty()
                val vm = viewModel { HabitDetailViewModel(container, habitId) }
                HabitDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(Screen.HabitForm.route(habitId = id)) },
                )
            }

            composable(
                route = Screen.HabitForm.route,
                arguments = listOf(
                    androidx.navigation.navArgument(Screen.HabitForm.ARG_HABIT_ID) {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    androidx.navigation.navArgument(Screen.HabitForm.ARG_IDENTITY_ID) {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                val habitId = entry.arguments?.getString(Screen.HabitForm.ARG_HABIT_ID)
                val identityId = entry.arguments?.getString(Screen.HabitForm.ARG_IDENTITY_ID)
                val vm = viewModel { com.jktdeveloper.habitto.ui.habit.HabitFormViewModel(container, habitId = habitId, prefillIdentityId = identityId) }
                com.jktdeveloper.habitto.ui.habit.HabitFormScreen(
                    viewModel = vm,
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onDeleted = {
                        // Pop the form, then pop HabitDetail (which now shows
                        // "Habit not found" since the row was just tombstoned).
                        // Second pop is a no-op if HabitDetail wasn't on the
                        // stack (e.g. delete from a non-detail entry).
                        navController.popBackStack()
                        navController.popBackStack(Screen.HabitDetail.route, inclusive = true)
                    },
                )
            }
        }
    }
}
