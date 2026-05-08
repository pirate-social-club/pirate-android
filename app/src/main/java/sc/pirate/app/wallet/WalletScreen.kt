package sc.pirate.app.wallet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import sc.pirate.app.R
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.api.model.WalletAttachmentSummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.walletconnect.ReownUiState

private data class WalletAssetRow(
    val id: String,
    val symbol: String,
    val name: String,
    val chainTitle: String,
    val tokenIconRes: Int,
    val chainIconRes: Int?,
    val balance: String,
    val fiatValue: String,
)

private val WalletAssetRows = listOf(
    WalletAssetRow(
        id = "ethereum-eth",
        symbol = "ETH",
        name = "Ether",
        chainTitle = "Ethereum",
        tokenIconRes = R.drawable.wallet_icon_ethereum,
        chainIconRes = R.drawable.wallet_icon_ethereum,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "base-eth",
        symbol = "ETH",
        name = "Ether",
        chainTitle = "Base",
        tokenIconRes = R.drawable.wallet_icon_ethereum,
        chainIconRes = R.drawable.wallet_icon_base,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "base-usdc",
        symbol = "USDC",
        name = "USD Coin",
        chainTitle = "Base",
        tokenIconRes = R.drawable.wallet_icon_usdc,
        chainIconRes = R.drawable.wallet_icon_base,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "optimism-eth",
        symbol = "ETH",
        name = "Ether",
        chainTitle = "Optimism",
        tokenIconRes = R.drawable.wallet_icon_ethereum,
        chainIconRes = R.drawable.wallet_icon_optimism,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "story-ip",
        symbol = "IP",
        name = "IP",
        chainTitle = "Story",
        tokenIconRes = R.drawable.wallet_icon_ip,
        chainIconRes = R.drawable.wallet_icon_story,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "tempo-pathusd",
        symbol = "pathUSD",
        name = "pathUSD",
        chainTitle = "Tempo",
        tokenIconRes = R.drawable.wallet_icon_tempo,
        chainIconRes = R.drawable.wallet_icon_tempo,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "bitcoin-btc",
        symbol = "BTC",
        name = "Bitcoin",
        chainTitle = "Bitcoin",
        tokenIconRes = R.drawable.wallet_icon_bitcoin,
        chainIconRes = null,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "solana-sol",
        symbol = "SOL",
        name = "Solana",
        chainTitle = "Solana",
        tokenIconRes = R.drawable.wallet_icon_solana,
        chainIconRes = R.drawable.wallet_icon_solana,
        balance = "0",
        fiatValue = "\$0.00",
    ),
    WalletAssetRow(
        id = "cosmos-atom",
        symbol = "ATOM",
        name = "Cosmos Hub",
        chainTitle = "Cosmos",
        tokenIconRes = R.drawable.wallet_icon_cosmos,
        chainIconRes = R.drawable.wallet_icon_cosmos,
        balance = "0",
        fiatValue = "\$0.00",
    ),
)

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

    val walletAddress = primaryWallet?.walletAddress
        ?: walletConnectState.connectedAddress
    val canReceive = !walletAddress.isNullOrBlank()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (session == null) {
            item {
                StatusCard(
                    title = "Sign in to view your wallet",
                    description = "Sign in to load balances, royalties, and wallet actions.",
                    tone = StatusTone.Default,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                )
            }
            item {
                PirateButton(
                    text = "Sign in",
                    onClick = onSignIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            return@LazyColumn
        }

        item {
            WalletBalanceSection(
                walletAddress = walletAddress,
                actionsPending = walletConnectState.available && walletConnectState.statusMessage?.contains("ready", ignoreCase = true) == false,
                onSend = {},
                onReceive = if (canReceive) onOpenWalletConnect else null,
            )
        }

        item {
            RoyaltiesSection(
                claimableAmount = "\$0.00",
                onClaim = {},
            )
        }

        items(WalletAssetRows, key = { it.id }) { asset ->
            MobileAssetRow(asset = asset)
        }
    }
}

@Composable
private fun WalletBalanceSection(
    walletAddress: String?,
    actionsPending: Boolean,
    onSend: (() -> Unit)?,
    onReceive: (() -> Unit)?,
) {
    val showWalletActions = !walletAddress.isNullOrBlank() || actionsPending

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Total balance",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Text(
            text = "\$0.00",
            style = MaterialTheme.typography.displaySmall,
            color = PirateTokens.colors.textPrimary,
        )
        if (showWalletActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WalletActionButton(
                    text = "Send",
                    onClick = onSend,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                )
                WalletActionButton(
                    text = "Receive",
                    onClick = onReceive,
                    enabled = !actionsPending && !walletAddress.isNullOrBlank() && onReceive != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WalletActionButton(
    text: String,
    onClick: (() -> Unit)?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(PirateTokens.radius.lg),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PirateTokens.colors.textPrimary,
            disabledContentColor = PirateTokens.colors.textDisabled,
        ),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RoyaltiesSection(
    claimableAmount: String,
    onClaim: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        HorizontalDivider(color = PirateTokens.colors.borderSoft)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Royalties",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Text(
            text = claimableAmount,
            style = MaterialTheme.typography.displaySmall,
            color = PirateTokens.colors.textPrimary,
        )
        PirateButton(
            text = "Claim",
            onClick = onClaim,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .height(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MobileAssetRow(asset: WalletAssetRow) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PirateTokens.colors.bgPage,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TokenChainMark(asset = asset)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = PirateTokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = asset.chainTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = asset.balance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textPrimary,
                    )
                    Text(
                        text = asset.fiatValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
            }
            HorizontalDivider(color = PirateTokens.colors.borderSoft)
        }
    }
}

@Composable
private fun TokenChainMark(asset: WalletAssetRow) {
    Box(modifier = Modifier.size(40.dp)) {
        Surface(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, PirateTokens.colors.borderSoft, CircleShape),
            color = androidx.compose.ui.graphics.Color.White,
        ) {
            Image(
                painter = painterResource(asset.tokenIconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
            )
        }
        asset.chainIconRes?.let { chainIconRes ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), CircleShape),
                color = androidx.compose.ui.graphics.Color.White,
            ) {
                Image(
                    painter = painterResource(chainIconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                )
            }
        }
    }
}
