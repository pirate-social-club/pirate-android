package sc.pirate.app.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.SessionExchangeProof
import sc.pirate.app.auth.PrivyClientStore
import sc.pirate.app.auth.PrivyRuntimeConfig

data class WalletLinkUiState(
    val linking: Boolean = false,
    val linkedWalletAddress: String? = null,
    val error: String? = null,
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
        _state.value = WalletLinkUiState()
    }
}
