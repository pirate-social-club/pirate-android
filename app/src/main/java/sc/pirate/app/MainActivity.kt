package sc.pirate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.reown.appkit.ui.openAppKit
import kotlinx.coroutines.delay
import sc.pirate.app.auth.AuthViewModel
import sc.pirate.app.auth.SignInDrawer
import sc.pirate.app.navigation.PirateNavHost
import sc.pirate.app.navigation.PirateRoute
import sc.pirate.app.theme.PirateTheme
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.BottomNavItem
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as PirateApp).verificationCoordinator.handleIntent(intent)
        (application as PirateApp).reownManager.handleDeepLink(intent?.data)
        (application as PirateApp).reownManager.registerActivity(this)
        enableEdgeToEdge()
        setContent {
            PirateTheme {
                PirateAppShell()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the latest callback intent available to SDK-driven auth flows.
        setIntent(intent)
        (application as PirateApp).verificationCoordinator.handleIntent(intent)
        (application as PirateApp).reownManager.handleDeepLink(intent.data)
    }

    override fun onDestroy() {
        (application as PirateApp).reownManager.unregisterActivity()
        super.onDestroy()
    }
}

@Composable
private fun PirateAppShell() {
    val context = LocalContext.current
    val app = context.applicationContext as PirateApp
    val session by app.sessionStore.observe().collectAsState(initial = null)
    val hasSession = session != null
    val authVm: AuthViewModel = viewModel()
    val authState by authVm.state.collectAsState()
    val walletConnectState by app.reownManager.state.collectAsState()
    val unreadChatCount by app.chatService.unreadCount.collectAsState()
    val hideChatBottomNav by app.chatService.hideBottomNav.collectAsState()
    var showSignInDrawer by remember { mutableStateOf(false) }
    var pendingWalletOpen by remember { mutableStateOf(false) }
    var unreadNotificationCount by remember { mutableStateOf(0) }

    LaunchedEffect(hasSession) {
        if (!hasSession) {
            unreadNotificationCount = 0
            return@LaunchedEffect
        }
        while (true) {
            unreadNotificationCount = try {
                val summary = app.repositories.notificationRepository.getSummary()
                summary.openTaskCount + summary.unreadActivityCount
            } catch (_: Exception) {
                unreadNotificationCount
            }
            delay(60_000)
        }
    }

    val bottomItems = listOf(
        BottomNavItem(
            route = PirateRoute.Home.route,
            label = "Home",
            icon = PhosphorIcons.House,
            activeIcon = PhosphorIcons.HouseFill,
            activeRoutes = setOf(PirateRoute.Home.route, PirateRoute.Community.route),
        ),
        BottomNavItem(
            PirateRoute.Wallet.route,
            "Wallet",
            PhosphorIcons.Wallet,
            PhosphorIcons.WalletFill,
            requiresAuth = true,
        ),
        BottomNavItem(
            route = PirateRoute.Chat.route,
            label = "Chat",
            icon = PhosphorIcons.ChatCircle,
            activeIcon = PhosphorIcons.ChatCircleFill,
            requiresAuth = true,
            unreadCount = unreadChatCount,
        ),
        BottomNavItem(
            PirateRoute.Notifications.route,
            "Notifications",
            PhosphorIcons.Bell,
            PhosphorIcons.BellFill,
            activeRoutes = setOf(PirateRoute.Notifications.route, PirateRoute.Inbox.route),
            requiresAuth = true,
            unreadCount = unreadNotificationCount,
        ),
        BottomNavItem(
            PirateRoute.Me.route,
            "Profile",
            PhosphorIcons.UserCircle,
            PhosphorIcons.UserCircleFill,
            requiresAuth = true,
        ),
    )

    PirateScaffold(
        bottomItems = bottomItems,
        hasSession = hasSession,
        hideChatBottomBar = hideChatBottomNav,
        onRequireAuth = { showSignInDrawer = true },
        drawerContent = { navController, closeDrawer, runAfterDrawerClose ->
            PirateAppNavigationDrawer(
                hasSession = hasSession,
                onClose = closeDrawer,
                onHome = {
                    runAfterDrawerClose {
                        navController.navigateFromDrawer(PirateRoute.Home.route)
                    }
                },
                onYourCommunities = {
                    runAfterDrawerClose {
                        navController.navigateFromDrawer(PirateRoute.YourCommunities.route)
                    }
                },
                onChat = {
                    runAfterDrawerClose {
                        if (hasSession) {
                            navController.navigateFromDrawer(PirateRoute.Chat.route)
                        } else {
                            showSignInDrawer = true
                        }
                    }
                },
                onCreateCommunity = {
                    runAfterDrawerClose {
                        if (hasSession) {
                            navController.navigateFromDrawer(PirateRoute.CreateCommunity.route)
                        } else {
                            showSignInDrawer = true
                        }
                    }
                },
                onProfile = {
                    runAfterDrawerClose {
                        if (hasSession) {
                            navController.navigateFromDrawer(PirateRoute.Me.route)
                        } else {
                            showSignInDrawer = true
                        }
                    }
                },
                onLogout = {
                    runAfterDrawerClose {
                        showSignInDrawer = false
                        pendingWalletOpen = false
                        authVm.logout()
                        navController.navigateFromDrawer(PirateRoute.Home.route)
                    }
                },
                onSignIn = {
                    runAfterDrawerClose {
                        showSignInDrawer = true
                    }
                },
            )
        },
    ) { navController, modifier, openDrawer ->
        LaunchedEffect(pendingWalletOpen) {
            if (pendingWalletOpen) {
                delay(260)
                pendingWalletOpen = false
                navController.openAppKit(
                    shouldOpenChooseNetwork = false,
                    onError = { error ->
                        app.reownManager.refreshState(
                            error.message ?: "Could not open wallet chooser."
                        )
                    },
                )
            }
        }

        PirateNavHost(
            navController = navController,
            onOpenNavigation = openDrawer,
            modifier = modifier,
        )
        if (showSignInDrawer) {
            SignInDrawer(
                state = authState,
                walletConnectState = walletConnectState,
                onOpenWalletConnect = {
                    pendingWalletOpen = true
                },
                onLoginWallet = authVm::loginWithConnectedWallet,
                onLoginGoogle = authVm::loginWithGoogle,
                onLoginTwitter = authVm::loginWithTwitter,
                onSendEmailCode = authVm::sendEmailCode,
                onLoginEmail = authVm::loginWithEmail,
                onLogout = authVm::logout,
                onDismiss = { showSignInDrawer = false },
            )
        }
    }
}

private fun NavHostController.navigateFromDrawer(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PirateAppNavigationDrawer(
    hasSession: Boolean,
    onClose: () -> Unit,
    onHome: () -> Unit,
    onYourCommunities: () -> Unit,
    onChat: () -> Unit,
    onCreateCommunity: () -> Unit,
    onProfile: () -> Unit,
    onLogout: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = PirateTokens.colors.bgPage,
        drawerContentColor = PirateTokens.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pirate",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PirateTokens.colors.textPrimary,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = PhosphorIcons.CaretLeft,
                        contentDescription = "Close navigation",
                        tint = PirateTokens.colors.textPrimary,
                    )
                }
            }

            DrawerSectionLabel("Feed")
            DrawerRow("Home", PhosphorIcons.House, onHome)
            DrawerRow("Your Communities", PhosphorIcons.Flag, onYourCommunities)
            DrawerRow("Agents", PhosphorIcons.Robot, onChat)
            DrawerRow("Create Community", PhosphorIcons.Plus, onCreateCommunity)

            Spacer(modifier = Modifier.weight(1f))
            if (hasSession) {
                DrawerRow("Profile", PhosphorIcons.UserCircle, onProfile)
                DrawerRow("Log out", PhosphorIcons.SignOut, onLogout)
            } else {
                DrawerRow("Sign in", PhosphorIcons.UserCircle, onSignIn)
            }
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = PirateTokens.colors.textSecondary.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
private fun DrawerRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PirateTokens.colors.textPrimary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = PirateTokens.colors.textPrimary,
        )
    }
}
