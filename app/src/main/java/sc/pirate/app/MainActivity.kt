package sc.pirate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import sc.pirate.app.navigation.PirateNavHost
import sc.pirate.app.navigation.PirateRoute
import sc.pirate.app.theme.PirateTheme
import sc.pirate.app.theme.PirateTokens

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PirateTheme {
                PirateAppShell()
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun PirateAppShell() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomItems = listOf(
        BottomNavItem(PirateRoute.Home.route, "Home", Icons.Filled.Home),
        BottomNavItem(PirateRoute.YourCommunities.route, "Communities", Icons.Filled.People),
        BottomNavItem(PirateRoute.Inbox.route, "Inbox", Icons.Filled.Email),
        BottomNavItem(PirateRoute.Me.route, "Profile", Icons.Filled.Person),
    )

    val showBottomBar = bottomItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = PirateTokens.colors.bgElevated,
                ) {
                    bottomItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PirateTokens.colors.accentBrand,
                                selectedTextColor = PirateTokens.colors.accentBrand,
                                unselectedIconColor = PirateTokens.colors.textSecondary,
                                unselectedTextColor = PirateTokens.colors.textSecondary,
                                indicatorColor = PirateTokens.colors.surfaceAccent,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        PirateNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
