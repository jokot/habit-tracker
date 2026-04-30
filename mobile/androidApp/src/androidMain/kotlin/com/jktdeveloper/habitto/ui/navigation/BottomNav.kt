package com.jktdeveloper.habitto.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Today
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jktdeveloper.habitto.ui.theme.BottomNavBg
import com.jktdeveloper.habitto.ui.theme.BottomNavBgDark

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val NAV_ITEMS = listOf(
    NavItem(Screen.Home.route, "Today", Icons.Rounded.Today),
    NavItem(Screen.StreakHistory.route, "Streak", Icons.Rounded.LocalFireDepartment),
    NavItem(Screen.You.route, "You", Icons.Outlined.Person),
)

@Composable
fun BottomNav(
    currentRoute: String?,
    navController: NavController,
) {
    val barBg = if (isSystemInDarkTheme()) BottomNavBgDark else BottomNavBg
    Column {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        NavigationBar(
            containerColor = barBg,
        ) {
            NAV_ITEMS.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        // Try pop first — if the destination is already in the backstack
                        // (e.g. user pushed StreakHistory via "View all"), popping is
                        // lossless. Fall back to navigate for first-time tab switches.
                        val popped = navController.popBackStack(item.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
            )
            }
        }
    }
}

/** Routes that show the bottom nav. */
val BOTTOM_NAV_ROUTES: Set<String> = setOf(
    Screen.Home.route,
    Screen.StreakHistory.route,
    Screen.You.route,
)
