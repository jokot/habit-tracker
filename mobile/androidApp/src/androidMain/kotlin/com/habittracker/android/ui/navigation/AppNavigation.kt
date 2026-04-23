package com.habittracker.android.ui.navigation

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
import com.habittracker.android.AppContainer
import com.habittracker.android.ui.auth.AuthScreen
import com.habittracker.android.ui.auth.AuthViewModel
import com.habittracker.android.ui.home.HomeScreen
import com.habittracker.android.ui.home.HomeViewModel
import com.habittracker.android.ui.onboarding.OnboardingScreen
import com.habittracker.android.ui.onboarding.OnboardingViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        container.seedLocalDataIfEmpty()
        val userId = container.currentUserId()
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
                onSuccess = { navController.popBackStack() },
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
            )
        }

        composable(Screen.Home.route) {
            val vm = viewModel { HomeViewModel(container) }
            HomeScreen(
                viewModel = vm,
                onSignIn = { navController.navigate(Screen.Auth.route) },
            )
        }
    }
}
