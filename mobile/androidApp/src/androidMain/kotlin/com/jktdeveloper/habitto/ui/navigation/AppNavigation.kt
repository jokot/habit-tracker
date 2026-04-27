package com.jktdeveloper.habitto.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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

    val start = startDestination
    if (start == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = navController, startDestination = start) {

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
                onOpenSettings = { navController.navigate(Screen.Settings.route) },
                onOpenStreakHistory = { navController.navigate(Screen.StreakHistory.route) },
            )
        }
    }
}
