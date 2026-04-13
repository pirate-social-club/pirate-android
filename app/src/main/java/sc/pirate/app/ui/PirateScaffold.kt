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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
    navController: NavHostController,
    bottomItems: List<BottomNavItem>,
    content: @Composable (Modifier) -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
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
                                        popUpTo(PirateRoute.Home.route) { saveState = true }
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
            Modifier.padding(
                start = innerPadding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                top = innerPadding.calculateTopPadding(),
                end = innerPadding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
                bottom = innerPadding.calculateBottomPadding(),
            ),
        )
    }
}
