package com.jktdeveloper.habitto.ui.navigation

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
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
import com.jktdeveloper.habitto.ui.onboarding.OnboardingScreen
import com.jktdeveloper.habitto.ui.onboarding.OnboardingViewModel
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
                )
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

            composable(Screen.You.route) {
                val vm = androidx.lifecycle.viewmodel.compose.viewModel {
                    com.jktdeveloper.habitto.ui.you.YouHubViewModel(container)
                }
                com.jktdeveloper.habitto.ui.you.YouHubScreen(
                    viewModel = vm,
                    onOpenSettings = { navController.navigate(Screen.Settings.route) },
                    onSignIn = { navController.navigate(Screen.Auth.route) },
                    onSignOutComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                    onOpenIdentities = { navController.navigate(Screen.IdentityList.route) },
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
                )
            }
        }
    }
}
