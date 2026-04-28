package sc.pirate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
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
    val bottomItems = listOf(
        BottomNavItem(
            route = PirateRoute.Home.route,
            label = "Home",
            icon = PhosphorIcons.House,
            activeIcon = PhosphorIcons.HouseFill,
            activeRoutes = setOf(PirateRoute.Home.route, PirateRoute.Community.route),
        ),
        BottomNavItem(PirateRoute.Wallet.route, "Wallet", PhosphorIcons.Wallet, PhosphorIcons.WalletFill),
        BottomNavItem(
            route = PirateRoute.Chat.route,
            label = "Chat",
            icon = PhosphorIcons.ChatCircle,
            activeIcon = PhosphorIcons.ChatCircleFill,
        ),
        BottomNavItem(PirateRoute.Inbox.route, "Inbox", PhosphorIcons.Bell, PhosphorIcons.BellFill),
        BottomNavItem(PirateRoute.Me.route, "Profile", PhosphorIcons.UserCircle, PhosphorIcons.UserCircleFill),
    )

    PirateScaffold(bottomItems = bottomItems) { navController, modifier ->
        PirateNavHost(navController = navController, modifier = modifier)
    }
}
