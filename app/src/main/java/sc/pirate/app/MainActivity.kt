package sc.pirate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reown.appkit.ui.openAppKit
import kotlinx.coroutines.delay
import sc.pirate.app.auth.AuthViewModel
import sc.pirate.app.auth.SignInDrawer
import sc.pirate.app.navigation.PirateNavHost
import sc.pirate.app.navigation.PirateRoute
import sc.pirate.app.theme.PirateTheme
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
    ) { navController, modifier ->
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

        PirateNavHost(navController = navController, modifier = modifier)
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
