package com.habittracker.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.habittracker.android.AppContainer
import com.habittracker.android.ui.auth.AuthScreen
import com.habittracker.android.ui.auth.AuthViewModel
import com.habittracker.android.ui.home.HomeScreen
import com.habittracker.android.ui.home.HomeViewModel
import com.habittracker.android.ui.log.LogHabitScreen
import com.habittracker.android.ui.log.LogHabitViewModel
import com.habittracker.android.ui.log.LogWantScreen
import com.habittracker.android.ui.log.LogWantViewModel
import com.habittracker.android.ui.onboarding.OnboardingScreen
import com.habittracker.android.ui.onboarding.OnboardingViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object LogHabit : Screen("log_habit/{habitId}") {
        const val ARG = "habitId"
        fun route(habitId: String) = "log_habit/$habitId"
    }
    object LogWant : Screen("log_want/{activityId}") {
        const val ARG = "activityId"
        fun route(activityId: String) = "log_want/$activityId"
    }
}

@Composable
fun AppNavigation(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Auth.route) {

        composable(Screen.Auth.route) {
            val vm = viewModel { AuthViewModel(container) }
            AuthScreen(
                viewModel = vm,
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                },
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
                onLogHabit = { habitId -> navController.navigate(Screen.LogHabit.route(habitId)) },
                onLogWant = { activityId -> navController.navigate(Screen.LogWant.route(activityId)) },
            )
        }

        composable(
            route = Screen.LogHabit.route,
            arguments = listOf(navArgument(Screen.LogHabit.ARG) { type = NavType.StringType }),
        ) { backStack ->
            val habitId = backStack.arguments?.getString(Screen.LogHabit.ARG) ?: return@composable
            val vm = viewModel(key = habitId) { LogHabitViewModel(habitId, container) }
            LogHabitScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.LogWant.route,
            arguments = listOf(navArgument(Screen.LogWant.ARG) { type = NavType.StringType }),
        ) { backStack ->
            val activityId = backStack.arguments?.getString(Screen.LogWant.ARG) ?: return@composable
            val vm = viewModel(key = activityId) { LogWantViewModel(activityId, container) }
            LogWantScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
