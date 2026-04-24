package sc.pirate.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import sc.pirate.app.navigation.PirateNavHost
import sc.pirate.app.navigation.PirateRoute
import sc.pirate.app.theme.PirateTheme
import sc.pirate.app.ui.BottomNavItem
import sc.pirate.app.ui.PirateScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as PirateApp).verificationCoordinator.handleIntent(intent)
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
    }
}

@Composable
private fun PirateAppShell() {
    val bottomItems = listOf(
        BottomNavItem(PirateRoute.Home.route, "Home", Icons.Filled.Home),
        BottomNavItem(PirateRoute.Wallet.route, "Wallet", Icons.Filled.AccountBalanceWallet),
        BottomNavItem(
            route = PirateRoute.GlobalSubmit.route,
            label = "Create",
            icon = Icons.Filled.Add,
            activeRoutes = setOf(PirateRoute.GlobalSubmit.route, PirateRoute.ComposePost.route),
        ),
        BottomNavItem(PirateRoute.Inbox.route, "Inbox", Icons.Filled.Notifications),
        BottomNavItem(PirateRoute.Me.route, "Profile", Icons.Filled.Person),
    )

    PirateScaffold(bottomItems = bottomItems) { navController, modifier ->
        PirateNavHost(navController = navController, modifier = modifier)
    }
}
