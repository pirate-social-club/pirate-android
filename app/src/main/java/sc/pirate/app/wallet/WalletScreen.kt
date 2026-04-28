package sc.pirate.app.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.api.model.WalletAttachmentSummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.shortAddress
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
    val attachments = session?.walletAttachments.orEmpty()
    val primaryWallet = session?.profile?.primaryWalletAddress
        ?.let { primaryAddress ->
            attachments.firstOrNull { it.walletAddress.equals(primaryAddress, ignoreCase = true) }
                ?: WalletAttachmentSummary(walletAddress = primaryAddress, isPrimary = true)
        }
        ?: attachments.firstOrNull { it.isPrimary }
        ?: attachments.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Wallet",
                style = MaterialTheme.typography.headlineSmall,
                color = PirateTokens.colors.textPrimary,
            )
        }

        if (session == null) {
            item {
                StatusCard(
                    title = "Sign in to view your wallet",
                    description = "Pirate uses your attached wallet for gated communities, messaging identity, and wallet-based account state.",
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
            return@LazyColumn
        }

        item {
            WalletOverviewCard(
                attachmentCount = attachments.size,
                connectedAddress = walletConnectState.connectedAddress,
                primaryWallet = primaryWallet,
            )
        }

        when {
            !walletConnectState.available -> {
                item {
                    StatusCard(
                        title = "WalletConnect unavailable",
                        description = walletConnectState.statusMessage ?: "WalletConnect is not configured for this build.",
                        tone = StatusTone.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            walletConnectState.isConnected -> {
                item {
                    ConnectedWalletCard(
                        connectedAddress = walletConnectState.connectedAddress,
                        connectorType = walletConnectState.connectorType,
                        selectedChain = walletConnectState.selectedChain,
                        statusMessage = walletConnectState.statusMessage,
                    )
                }

                if (attachments.none {
                        it.walletAddress.equals(walletConnectState.connectedAddress, ignoreCase = true)
                    }) {
                    item {
                        PirateButton(
                            text = if (walletUiState.linking) "Linking wallet..." else "Link wallet to Pirate",
                            onClick = onLinkWallet,
                            enabled = !walletUiState.linking,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        StatusCard(
                            title = "Link wallet to finish setup",
                            description = "Connecting the wallet app is only the first half. Link it through Privy so Pirate can attach the address to your account and use it across the app.",
                            tone = StatusTone.Default,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                item {
                    PirateButton(
                        text = "Disconnect wallet app",
                        onClick = onDisconnectWallet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                item {
                    StatusCard(
                        title = "Connect an external wallet",
                        description = "Open the wallet chooser to connect MetaMask, Coinbase Wallet, or another WalletConnect-compatible wallet app, then attach it to your Pirate account.",
                        tone = StatusTone.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    PirateButton(
                        text = "Connect wallet",
                        onClick = onOpenWalletConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                walletConnectState.statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PirateTokens.colors.textSecondary,
                        )
                    }
                }
            }
        }

        walletUiState.linkedWalletAddress?.let { walletAddress ->
            item {
                StatusCard(
                    title = "Wallet linked",
                    description = "${shortAddress(walletAddress)} is now attached to your Pirate account.",
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
                    tone = StatusTone.Danger,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                PirateButton(
                    text = "Clear message",
                    onClick = onClearWalletFeedback,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            WalletCapabilityCard(hasAttachments = attachments.isNotEmpty())
        }

        if (attachments.isEmpty()) {
            item {
                StatusCard(
                    title = "No wallet attachments yet",
                    description = "Your account is signed in, but Pirate does not have an attached wallet to use for gated communities or wallet-backed identity yet.",
                    tone = StatusTone.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            item {
                Text(
                    text = "Attached wallets",
                    style = MaterialTheme.typography.titleMedium,
                    color = PirateTokens.colors.textPrimary,
                )
            }
            items(attachments, key = { it.walletAttachmentId ?: it.walletAddress }) { wallet ->
                AttachedWalletCard(
                    wallet = wallet,
                    isPrimary = primaryWallet?.walletAddress.equals(wallet.walletAddress, ignoreCase = true) || wallet.isPrimary,
                )
            }
        }
    }
}

@Composable
private fun WalletOverviewCard(
    primaryWallet: WalletAttachmentSummary?,
    attachmentCount: Int,
    connectedAddress: String?,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Wallet hub",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Text(
            text = primaryWallet?.walletAddress?.let(::shortAddress) ?: "No primary wallet yet",
            style = MaterialTheme.typography.headlineSmall,
            color = PirateTokens.colors.textPrimary,
        )
        Text(
            text = when {
                primaryWallet != null -> "Primary Pirate wallet"
                else -> "Attach a wallet to unlock wallet-based identity"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WalletStat(label = "Attached", value = attachmentCount.toString(), modifier = Modifier.weight(1f))
            WalletStat(
                label = "App connection",
                value = if (connectedAddress.isNullOrBlank()) "Idle" else "Live",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WalletStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    PirateCard(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

@Composable
private fun ConnectedWalletCard(
    connectedAddress: String?,
    connectorType: String?,
    selectedChain: String?,
    statusMessage: String?,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Connected wallet app",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Text(
            text = connectedAddress?.let(::shortAddress) ?: "Connected",
            style = MaterialTheme.typography.bodyLarge,
            color = PirateTokens.colors.textPrimary,
        )
        connectorType?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "Connector: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        selectedChain?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "Chain: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        statusMessage?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun WalletCapabilityCard(hasAttachments: Boolean) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "What this wallet unlocks",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        val rows = if (hasAttachments) {
            listOf(
                "Gated community membership and posting checks",
                "Wallet-backed identity across Pirate surfaces",
                "Primary wallet context for future chat and commerce flows",
            )
        } else {
            listOf(
                "Community gates still need an attached wallet",
                "Profile wallet surfaces stay incomplete until link succeeds",
                "Wallet-based messaging and commerce stay unavailable",
            )
        }
        rows.forEach { row ->
            Text(
                text = row,
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun AttachedWalletCard(
    wallet: WalletAttachmentSummary,
    isPrimary: Boolean,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = shortAddress(wallet.walletAddress),
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = when {
                isPrimary -> "Primary wallet"
                else -> "Attached wallet"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        wallet.chainNamespace?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "Namespace: $it",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        wallet.chainId?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "Chain ID: $it",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        wallet.walletAttachmentId?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = "Attachment: $it",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}
