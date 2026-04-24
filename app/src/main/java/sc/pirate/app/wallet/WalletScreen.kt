package sc.pirate.app.wallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sc.pirate.app.ui.FeatureStubScreen

@Composable
fun WalletScreen(
    modifier: Modifier = Modifier,
) {
    FeatureStubScreen(
        title = "Wallet",
        body = "Wallet is not wired on Android yet. This route exists to match the mobile web shell while the happy path stays focused on communities and posting.",
        modifier = modifier,
    )
}
