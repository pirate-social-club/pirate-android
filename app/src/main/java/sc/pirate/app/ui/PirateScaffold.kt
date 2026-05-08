package sc.pirate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.accompanist.navigation.material.ExperimentalMaterialNavigationApi
import com.google.accompanist.navigation.material.ModalBottomSheetLayout
import com.google.accompanist.navigation.material.rememberBottomSheetNavigator
import sc.pirate.app.navigation.PirateRoute
import sc.pirate.app.theme.PirateTokens

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val activeIcon: ImageVector = icon,
    val activeRoutes: Set<String> = setOf(route),
    val requiresAuth: Boolean = false,
    val unreadCount: Int = 0,
)

@Composable
@OptIn(ExperimentalMaterialNavigationApi::class)
fun PirateScaffold(
    bottomItems: List<BottomNavItem>,
    hasSession: Boolean,
    hideChatBottomBar: Boolean = false,
    onRequireAuth: () -> Unit,
    content: @Composable (NavHostController, Modifier) -> Unit,
) {
    val bottomSheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(bottomSheetNavigator)
    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar =
        currentRoute in listOf(
            PirateRoute.Home.route,
            PirateRoute.Chat.route,
            PirateRoute.Community.route,
            PirateRoute.Wallet.route,
            PirateRoute.Notifications.route,
            PirateRoute.Inbox.route,
            PirateRoute.Me.route,
        ) && !(currentRoute == PirateRoute.Chat.route && hideChatBottomBar)

    ModalBottomSheetLayout(bottomSheetNavigator = bottomSheetNavigator) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showBottomBar) {
                    Surface(
                        color = PirateTokens.colors.bgPage.copy(alpha = 0.95f),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .navigationBarsPadding()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                        ) {
                            bottomItems.forEach { item ->
                                val selected = currentRoute in item.activeRoutes
                                BottomNavIcon(
                                    item = item,
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            if (item.requiresAuth && !hasSession) {
                                                onRequireAuth()
                                                return@BottomNavIcon
                                            }
                                            navController.navigate(item.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                )
                            }
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
                ).statusBarsPadding()
            )
        }
    }
}

@Composable
private fun BottomNavIcon(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val iconColor = if (selected) {
        PirateTokens.colors.textPrimary
    } else {
        PirateTokens.colors.textSecondary
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = item.label,
                onClick = onClick,
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (item.unreadCount > 0) {
                    Badge(containerColor = PirateTokens.colors.accentBrand) {
                        Text(text = formatUnreadCount(item.unreadCount))
                    }
                }
            },
        ) {
            Icon(
                imageVector = if (selected) item.activeIcon else item.icon,
                contentDescription = item.label,
                tint = iconColor,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

private fun formatUnreadCount(count: Int): String = if (count > 99) "99+" else count.toString()
