package com.jktdeveloper.habitto.devtools

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.jktdeveloper.habitto.AppContainer

/** Mounts `Screen.DevTools` on debug builds. */
fun NavGraphBuilder.devToolsRoute(
    container: AppContainer,
    routePath: String,
    onBack: () -> Unit,
) {
    composable(routePath) {
        val vm = viewModel { DevToolsViewModel(container) }
        DevToolsScreen(viewModel = vm, onBack = onBack)
    }
}
