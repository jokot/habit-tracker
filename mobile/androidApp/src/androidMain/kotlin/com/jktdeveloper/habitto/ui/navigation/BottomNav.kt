package com.jktdeveloper.habitto.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItem(Screen.Home.route, "Today", Icons.Outlined.Today),
    NavItem(Screen.StreakHistory.route, "Streak", Icons.Outlined.LocalFireDepartment),
    NavItem(Screen.You.route, "You", Icons.Outlined.AccountCircle),
)

@Composable
fun BottomNav(
    currentRoute: String?,
    navController: NavController,
) {
    NavigationBar {
        NAV_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

/** Routes that show the bottom nav. */
val BOTTOM_NAV_ROUTES: Set<String> = setOf(
    Screen.Home.route,
    Screen.StreakHistory.route,
    Screen.You.route,
)
