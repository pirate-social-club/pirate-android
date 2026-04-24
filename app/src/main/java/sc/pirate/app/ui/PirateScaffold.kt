package sc.pirate.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import sc.pirate.app.navigation.PirateRoute
import sc.pirate.app.theme.PirateTokens

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun PirateScaffold(
    bottomItems: List<BottomNavItem>,
    content: @Composable (NavHostController, Modifier) -> Unit,
) {
    val navController = rememberNavController()
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        PirateRoute.Home.route,
        PirateRoute.YourCommunities.route,
        PirateRoute.Inbox.route,
        PirateRoute.Me.route,
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = PirateTokens.colors.bgElevated,
                    contentColor = PirateTokens.colors.textPrimary,
                ) {
                    bottomItems.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PirateTokens.colors.accentBrand,
                                selectedTextColor = PirateTokens.colors.accentBrand,
                                indicatorColor = PirateTokens.colors.surfaceAccent,
                                unselectedIconColor = PirateTokens.colors.textSecondary,
                                unselectedTextColor = PirateTokens.colors.textSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        content(
            navController,
            Modifier.padding(
                start = innerPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = innerPadding.calculateBottomPadding(),
            )
        )
    }
}
