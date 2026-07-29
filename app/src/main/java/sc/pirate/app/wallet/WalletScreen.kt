package sc.pirate.app.wallet

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

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
    onLoadNativeBalance: (String, String) -> Unit,
    onSendNativeAsset: (String, String, String, String) -> Unit,
    onClearSendFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var receiveAddress by remember { mutableStateOf<String?>(null) }
    var showSend by rememberSaveable { mutableStateOf(false) }
    val linkedWallet = session?.primaryWalletAttachment()
    val connectedAddress = walletConnectState.connectedAddress?.takeIf { it.isNotBlank() }
    val connectedWalletIsLinked = connectedAddress != null && session?.walletAttachments.orEmpty().any {
        it.walletAddress.equals(connectedAddress, ignoreCase = true)
    }
    val safeReceiveAddress = linkedWallet?.walletAddress ?: connectedAddress?.takeIf { connectedWalletIsLinked }

    LaunchedEffect(connectedAddress, walletConnectState.selectedChain, connectedWalletIsLinked) {
        val address = connectedAddress
        val chain = walletConnectState.selectedChain
        if (address != null && chain != null && connectedWalletIsLinked) {
            onLoadNativeBalance(address, chain)
        }
    }

    receiveAddress?.let { address ->
        ReceiveWalletDialog(
            address = address,
            onCopy = { clipboard.setText(AnnotatedString(address)) },
            onShare = {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, address)
                        },
                        "Share wallet address",
                    ),
                )
            },
            onDismiss = { receiveAddress = null },
        )
    }

    if (showSend && connectedAddress != null && walletConnectState.selectedChain != null) {
        SendNativeAssetDialog(
            from = connectedAddress,
            chainId = walletConnectState.selectedChain,
            symbol = nativeSymbol(walletConnectState.selectedChain),
            sending = walletUiState.sending,
            transactionHash = walletUiState.sendTransactionHash,
            error = walletUiState.sendError,
            onSend = { recipient, amount ->
                onSendNativeAsset(connectedAddress, recipient, amount, walletConnectState.selectedChain)
            },
            onDismiss = {
                if (!walletUiState.sending) {
                    showSend = false
                    onClearSendFeedback()
                }
            },
        )
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

            safeReceiveAddress?.let { address ->
                item {
                    PirateButton(
                        text = "Receive",
                        onClick = { receiveAddress = address },
                        variant = ButtonVariant.Outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (connectedWalletIsLinked && walletConnectState.selectedChain != null) {
                item {
                    when {
                        walletUiState.balanceLoading -> StatusCard(
                            title = "Loading balance",
                            description = "Reading the connected network's native-asset balance.",
                            tone = StatusTone.Default,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        walletUiState.nativeBalance != null -> StatusCard(
                            title = "Native balance",
                            description = "${walletUiState.nativeBalance} ${walletUiState.nativeBalanceSymbol.orEmpty()} · ${walletConnectState.selectedChain}",
                            tone = StatusTone.Default,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        walletUiState.balanceError != null -> StatusCard(
                            title = "Balance unavailable",
                            description = walletUiState.balanceError,
                            tone = StatusTone.Warning,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item {
                    PirateButton(
                        text = "Send ${walletUiState.nativeBalanceSymbol ?: nativeSymbol(walletConnectState.selectedChain)}",
                        onClick = { showSend = true },
                        enabled = !walletUiState.sending,
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
                title = "Balances and sending are not available yet",
                description = "Pirate will enable live balances, Send, and royalty claims only after chain, asset, and settlement support are fully connected and verified.",
                tone = StatusTone.Default,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SendNativeAssetDialog(
    from: String,
    chainId: String,
    symbol: String,
    sending: Boolean,
    transactionHash: String?,
    error: String?,
    onSend: (recipient: String, amount: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var recipient by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send $symbol") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Network: $chainId", color = PirateTokens.colors.textSecondary)
                Text("From: ${shortWalletAddress(from)}", color = PirateTokens.colors.textSecondary)
                Text(
                    "Your wallet will show the final network fee and require confirmation.",
                    color = PirateTokens.colors.textSecondary,
                )
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient address") },
                    enabled = !sending && transactionHash == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($symbol)") },
                    enabled = !sending && transactionHash == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { StatusCard("Could not send", it, StatusTone.Warning) }
                transactionHash?.let {
                    StatusCard("Transaction submitted", shortWalletAddress(it), StatusTone.Success)
                }
            }
        },
        confirmButton = {
            if (transactionHash == null) {
                PirateButton(
                    text = "Review in wallet",
                    onClick = { onSend(recipient, amount) },
                    loading = sending,
                    enabled = !sending && recipient.isNotBlank() && amount.isNotBlank(),
                )
            } else {
                PirateButton("Done", onDismiss)
            }
        },
        dismissButton = {
            if (transactionHash == null) {
                PirateButton("Cancel", onDismiss, variant = ButtonVariant.Outline, enabled = !sending)
            }
        },
    )
}

@Composable
private fun ReceiveWalletDialog(
    address: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val qrBitmap = remember(address) { walletQrBitmap(address) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receive") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Only send assets supported by this EVM wallet and its active network.",
                    color = PirateTokens.colors.textSecondary,
                )
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code for wallet address",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(address, style = MaterialTheme.typography.bodySmall)
                PirateButton("Copy address", onCopy, modifier = Modifier.fillMaxWidth())
                PirateButton(
                    "Share address",
                    onShare,
                    variant = ButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PirateButton("Done", onDismiss, variant = ButtonVariant.Outline)
        },
    )
}

private fun walletQrBitmap(address: String, size: Int = 768): Bitmap {
    val matrix = QRCodeWriter().encode("ethereum:$address", BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) for (y in 0 until size) {
            setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
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
