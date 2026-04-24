package sc.pirate.app.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

@Composable
fun WalletScreen(
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Wallet",
            style = MaterialTheme.typography.headlineSmall,
            color = PirateTokens.colors.textPrimary,
        )
        StatusCard(
            title = "No wallet connected",
            description = "Sign in to view your connected wallets.",
            tone = StatusTone.Default,
            modifier = Modifier.fillMaxWidth(),
        )
        PirateButton(
            text = "Sign in",
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
