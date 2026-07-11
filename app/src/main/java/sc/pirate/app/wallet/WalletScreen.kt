package sc.pirate.app.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.api.model.WalletAttachmentSummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.walletconnect.ReownUiState

@Composable
fun WalletScreen(
    session: SessionExchangeResponse?,
    walletConnectState: ReownUiState,
    walletUiState: WalletLinkUiState,
    onOpenWalletConnect: () -> Unit,
    onLinkWallet: () -> Unit,
    onClearWalletFeedback: () -> Unit,
    onDisconnectWallet: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkedWallet = session?.primaryWalletAttachment()
    val connectedAddress = walletConnectState.connectedAddress?.takeIf { it.isNotBlank() }
    val connectedWalletIsLinked = connectedAddress != null && session?.walletAttachments.orEmpty().any {
        it.walletAddress.equals(connectedAddress, ignoreCase = true)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Wallet",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PirateTokens.colors.textPrimary,
                )
                Text(
                    text = "Connect and link an EVM wallet to your Pirate account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        }

        if (session == null) {
            item {
                StatusCard(
                    title = "Sign in to manage wallets",
                    description = "Wallet linking is available after you sign in.",
                    tone = StatusTone.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                PirateButton(
                    text = "Sign in",
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            linkedWallet?.let { wallet ->
                item {
                    WalletAddressCard(
                        title = "Linked to Pirate",
                        address = wallet.walletAddress,
                        description = if (wallet.isPrimary) "Primary wallet" else "Linked wallet",
                    )
                }
            }

            if (connectedAddress != null) {
                item {
                    WalletAddressCard(
                        title = "Connected wallet",
                        address = connectedAddress,
                        description = walletConnectState.selectedChain ?: "WalletConnect session",
                    )
                }
                if (!connectedWalletIsLinked) {
                    item {
                        PirateButton(
                            text = "Link connected wallet",
                            onClick = onLinkWallet,
                            loading = walletUiState.linking,
                            enabled = !walletUiState.linking,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    PirateButton(
                        text = "Disconnect wallet",
                        onClick = onDisconnectWallet,
                        variant = ButtonVariant.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                item {
                    PirateButton(
                        text = "Connect wallet",
                        onClick = onOpenWalletConnect,
                        enabled = walletConnectState.available,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        walletUiState.linkedWalletAddress?.let { address ->
            item {
                StatusCard(
                    title = "Wallet linked",
                    description = shortWalletAddress(address),
                    tone = StatusTone.Success,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        walletUiState.error?.let { error ->
            item {
                StatusCard(
                    title = "Wallet link failed",
                    description = error,
                    tone = StatusTone.Warning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                PirateButton(
                    text = "Dismiss",
                    onClick = onClearWalletFeedback,
                    variant = ButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        walletConnectState.statusMessage
            ?.takeIf { it.isNotBlank() && walletUiState.error == null }
            ?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
            }

        item {
            StatusCard(
                title = "Balances and transfers are not available yet",
                description = "Pirate will show live balances, Send, Receive, and royalty claims only after those flows are fully connected and verified.",
                tone = StatusTone.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WalletAddressCard(
    title: String,
    address: String,
    description: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Text(
            text = address,
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

private fun SessionExchangeResponse.primaryWalletAttachment(): WalletAttachmentSummary? =
    profile.primaryWalletAddress
        ?.let { primaryAddress ->
            walletAttachments.firstOrNull { it.walletAddress.equals(primaryAddress, ignoreCase = true) }
                ?: WalletAttachmentSummary(walletAddress = primaryAddress, isPrimary = true)
        }
        ?: walletAttachments.firstOrNull { it.isPrimary }
        ?: walletAttachments.firstOrNull()

private fun shortWalletAddress(address: String): String =
    if (address.length <= 14) address else "${address.take(6)}...${address.takeLast(4)}"
