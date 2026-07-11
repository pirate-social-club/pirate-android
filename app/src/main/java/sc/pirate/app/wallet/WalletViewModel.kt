package sc.pirate.app.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.web3j.crypto.WalletUtils
import sc.pirate.app.walletconnect.EvmTransactionRequest
import sc.pirate.app.api.SessionExchangeProof
import sc.pirate.app.auth.PrivyClientStore
import sc.pirate.app.auth.PrivyRuntimeConfig

data class WalletLinkUiState(
    val linking: Boolean = false,
    val linkedWalletAddress: String? = null,
    val error: String? = null,
    val balanceLoading: Boolean = false,
    val nativeBalance: String? = null,
    val nativeBalanceSymbol: String? = null,
    val balanceError: String? = null,
    val sending: Boolean = false,
    val sendTransactionHash: String? = null,
    val sendError: String? = null,
)

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val authRepository get() = app.repositories.authRepository
    private val sessionStore get() = app.sessionStore
    private val privyConfig = PrivyRuntimeConfig.fromBuildConfig()

    private val _state = MutableStateFlow(WalletLinkUiState())
    val state: StateFlow<WalletLinkUiState> = _state.asStateFlow()

    fun linkConnectedWallet() {
        val disabledReason = privyConfig.disabledReason()
        if (disabledReason != null) {
            _state.value = WalletLinkUiState(error = disabledReason)
            return
        }

        viewModelScope.launch {
            _state.value = WalletLinkUiState(linking = true)
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                privy.awaitReady()
                val linkedWallet = app.reownManager.linkConnectedWallet(privy)
                PrivyClientStore.setUser(linkedWallet.user)
                val accessToken = linkedWallet.user.getAccessToken().getOrThrow()
                val session = authRepository.exchangeSession(
                    SessionExchangeProof(
                        type = "privy_access_token",
                        privyAccessToken = accessToken,
                        walletAddress = linkedWallet.walletAddress,
                    )
                )
                sessionStore.set(session)
                app.reownManager.refreshState("Wallet linked to Pirate.")
                _state.value = WalletLinkUiState(
                    linkedWalletAddress = linkedWallet.walletAddress,
                )
            } catch (error: Exception) {
                _state.value = WalletLinkUiState(
                    error = error.message ?: "Wallet linking failed.",
                )
            }
        }
    }

    fun clearFeedback() {
        if (_state.value.linking) return
        _state.value = _state.value.copy(linkedWalletAddress = null, error = null)
    }

    fun loadNativeBalance(address: String, chainId: String) {
        if (_state.value.balanceLoading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                balanceLoading = true,
                nativeBalance = null,
                nativeBalanceSymbol = null,
                balanceError = null,
            )
            try {
                val atomic = app.reownManager.getNativeBalance(address, chainId)
                _state.value = _state.value.copy(
                    balanceLoading = false,
                    nativeBalance = formatNativeBalance(atomic),
                    nativeBalanceSymbol = nativeSymbol(chainId),
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    balanceLoading = false,
                    balanceError = error.message ?: "Could not load wallet balance.",
                )
            }
        }
    }

    fun sendNativeAsset(from: String, recipient: String, amount: String, chainId: String) {
        if (_state.value.sending) return
        val normalizedRecipient = recipient.trim()
        val value = runCatching { nativeAmountToAtomic(amount) }.getOrElse { error ->
            _state.value = _state.value.copy(sendError = error.message ?: "Enter a valid amount.")
            return
        }
        if (!WalletUtils.isValidAddress(normalizedRecipient)) {
            _state.value = _state.value.copy(sendError = "Enter a valid EVM wallet address.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, sendError = null, sendTransactionHash = null)
            try {
                val hash = app.reownManager.sendEvmTransaction(
                    EvmTransactionRequest(
                        from = from,
                        to = normalizedRecipient,
                        value = "0x${value.toString(16)}",
                    ),
                    chainId,
                )
                _state.value = _state.value.copy(sending = false, sendTransactionHash = hash)
                loadNativeBalance(from, chainId)
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    sending = false,
                    sendError = error.message ?: "Wallet transaction failed.",
                )
            }
        }
    }

    fun clearSendFeedback() {
        if (_state.value.sending) return
        _state.value = _state.value.copy(sendTransactionHash = null, sendError = null)
    }
}

internal fun formatNativeBalance(atomic: BigInteger): String =
    BigDecimal(atomic)
        .movePointLeft(18)
        .setScale(6, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()

internal fun nativeSymbol(chainId: String): String = when (chainId.substringAfter(':')) {
    "137", "80002" -> "POL"
    "56", "97" -> "BNB"
    "43114", "43113" -> "AVAX"
    else -> "ETH"
}

internal fun nativeAmountToAtomic(value: String): BigInteger {
    val amount = value.trim().toBigDecimalOrNull() ?: throw IllegalArgumentException("Enter a valid amount.")
    require(amount > BigDecimal.ZERO) { "Amount must be greater than zero." }
    require(amount.scale().coerceAtLeast(0) <= 18) { "Use no more than 18 decimal places." }
    return amount.movePointRight(18).toBigIntegerExact()
}
