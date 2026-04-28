package sc.pirate.app.walletconnect

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.Account
import com.reown.appkit.client.models.request.Request
import com.reown.appkit.client.models.request.SentRequestResult
import com.reown.appkit.engine.coinbase.CoinbaseResult
import com.reown.appkit.presets.AppKitChainsPresets
import io.privy.auth.PrivyUser
import io.privy.auth.siwe.SiweMessageParams
import io.privy.auth.siwe.WalletLoginMetadata
import io.privy.sdk.Privy
import io.privy.wallet.WalletClientType
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class ReownUiState(
    val available: Boolean,
    val initialized: Boolean = false,
    val isConnected: Boolean = false,
    val connectedAddress: String? = null,
    val connectorType: String? = null,
    val selectedChain: String? = null,
    val statusMessage: String? = null,
)

data class LinkedWalletResult(
    val user: PrivyUser,
    val walletAddress: String,
)

class ReownManager(
    private val application: Application,
) {
    private val config = ReownRuntimeConfig.fromBuildConfig()
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pendingWalletConnectRequests =
        ConcurrentHashMap<Long, CompletableDeferred<Modal.Model.SessionRequestResponse>>()
    @Volatile
    private var registeredActivity: ComponentActivity? = null

    @Volatile
    private var initAttempted = false

    @Volatile
    private var initialized = false

    private val delegate = object : AppKit.ModalDelegate {
        override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
            refreshState("Wallet connected.")
        }

        override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
            refreshState("Wallet connection was rejected.")
        }

        override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) {
            refreshState()
        }

        override fun onSessionExtend(session: Modal.Model.Session) {
            refreshState()
        }

        override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) {
            refreshState()
        }

        override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
            refreshState("Wallet disconnected.")
        }

        override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
            completePendingRequest(response)
            refreshState()
        }

        override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {
            refreshState("Wallet connection request expired.")
        }

        override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {
            refreshState("Wallet request expired.")
        }

        override fun onConnectionStateChange(state: Modal.Model.ConnectionState) {
            refreshState()
        }

        override fun onError(error: Modal.Model.Error) {
            refreshState(error.throwable?.message ?: "WalletConnect reported an error.")
        }

        override fun onSessionAuthenticateResponse(response: Modal.Model.SessionAuthenticateResponse) {
            refreshState(
                when (response) {
                    is Modal.Model.SessionAuthenticateResponse.Result -> "Wallet authenticated."
                    else -> "Wallet authentication failed."
                }
            )
        }

        override fun onSIWEAuthenticationResponse(response: Modal.Model.SIWEAuthenticateResponse) {
            refreshState(
                when (response) {
                    is Modal.Model.SIWEAuthenticateResponse.Result -> "Wallet signed SIWE challenge."
                    else -> "Wallet SIWE signature failed."
                }
            )
        }
    }

    private val _state = MutableStateFlow(
        ReownUiState(
            available = config.disabledReason() == null,
            statusMessage = config.disabledReason(),
        )
    )
    val state: StateFlow<ReownUiState> = _state.asStateFlow()

    fun initialize() {
        val disabledReason = config.disabledReason()
        if (disabledReason != null) {
            _state.value = ReownUiState(available = false, statusMessage = disabledReason)
            return
        }
        if (initAttempted) {
            refreshState()
            return
        }

        synchronized(this) {
            if (initAttempted) {
                refreshState()
                return
            }
            initAttempted = true
        }

        val appMetaData = Core.Model.AppMetaData(
            name = "Pirate",
            description = "Pirate Android wallet connect",
            url = "https://pirate.sc",
            icons = listOf("https://pirate.sc/icon.png"),
            redirect = config.redirectUri,
        )

        try {
            CoreClient.initialize(
                projectId = config.projectId,
                connectionType = ConnectionType.AUTOMATIC,
                application = application,
                metaData = appMetaData,
                relay = null,
                keyServerUrl = null,
                networkClientTimeout = null,
                telemetryEnabled = false,
                onError = { error ->
                    refreshState(error.throwable.message ?: "Wallet connect failed to initialize.")
                },
            )
            AppKit.initialize(
                init = Modal.Params.Init(core = CoreClient),
                onSuccess = {
                    initialized = true
                    AppKit.setChains(AppKitChainsPresets.ethChains.values.toList())
                    AppKit.setDelegate(delegate)
                    registeredActivity?.let { AppKit.register(it) }
                    refreshState("Wallet connect ready.")
                },
                onError = { error ->
                    refreshState(error.throwable?.message ?: "Wallet connect failed to initialize.")
                },
            )
        } catch (error: Throwable) {
            refreshState(error.message ?: "Wallet connect failed to initialize.")
        }
    }

    fun registerActivity(activity: ComponentActivity) {
        registeredActivity = activity
        if (initialized) {
            runCatching { AppKit.register(activity) }
        }
    }

    fun unregisterActivity() {
        registeredActivity = null
        if (initialized) {
            runCatching { AppKit.unregister() }
        }
    }

    fun handleDeepLink(uri: Uri?) {
        if (uri == null || !initialized) return
        AppKit.handleDeepLink(uri.toString()) { error ->
            refreshState(error.throwable?.message ?: "Could not process wallet callback.")
        }
    }

    fun disconnect() {
        if (!initialized) return
        AppKit.disconnect(
            onSuccess = {
                refreshState("Wallet disconnected.")
            },
            onError = { error ->
                refreshState(error.message ?: "Wallet disconnect failed.")
            },
        )
    }

    suspend fun loginWithConnectedWallet(privy: Privy): LinkedWalletResult {
        return authenticateConnectedWallet(privy, linkMode = false)
    }

    suspend fun linkConnectedWallet(privy: Privy): LinkedWalletResult {
        return authenticateConnectedWallet(privy, linkMode = true)
    }

    private suspend fun authenticateConnectedWallet(
        privy: Privy,
        linkMode: Boolean,
    ): LinkedWalletResult {
        require(initialized) { "WalletConnect is not initialized." }

        val account = requireNotNull(runCatching { AppKit.getAccount() }.getOrNull()) {
            "Connect a wallet before linking it to Pirate."
        }
        val chain = account.chain
        val walletAddress = account.address
        val siweParams = SiweMessageParams(
            appDomain = "pirate.sc",
            appUri = "https://pirate.sc",
            chainId = resolveSiweChainId(chain),
            walletAddress = walletAddress,
        )
        val message = privy.siwe.generateMessage(siweParams).getOrThrow()
        val signature = signPersonalMessage(
            message = message,
            address = walletAddress,
            chainId = chain.id,
        )
        val walletMetadata = WalletLoginMetadata(
            walletClientType = walletClientTypeFor(runCatching { AppKit.getConnectorType() }.getOrNull()),
            connectorType = connectorTypeValue(runCatching { AppKit.getConnectorType() }.getOrNull()),
        )
        val user = if (linkMode) {
            privy.siwe.link(signature, walletAddress, siweParams, walletMetadata).getOrThrow()
        } else {
            privy.siwe.login(signature, walletAddress, siweParams, walletMetadata).getOrThrow()
        }

        refreshState(if (linkMode) "Wallet linked in Privy." else "Wallet signed in with Privy.")
        return LinkedWalletResult(
            user = user,
            walletAddress = walletAddress,
        )
    }

    fun refreshState(statusMessage: String? = _state.value.statusMessage) {
        mainScope.launch {
            val disabledReason = config.disabledReason()
            if (disabledReason != null) {
                _state.value = ReownUiState(available = false, statusMessage = disabledReason)
                return@launch
            }

            val account = runCatching { AppKit.getAccount() }.getOrNull()
            val address = extractAddress(account)

            _state.value = ReownUiState(
                available = true,
                initialized = initialized,
                isConnected = !address.isNullOrBlank(),
                connectedAddress = address,
                connectorType = runCatching { AppKit.getConnectorType()?.toString() }.getOrNull(),
                selectedChain = runCatching { AppKit.getSelectedChain()?.id }.getOrNull(),
                statusMessage = statusMessage,
            )
        }
    }

    private fun completePendingRequest(response: Modal.Model.SessionRequestResponse) {
        val requestId = when (val result = response.result) {
            is Modal.Model.JsonRpcResponse.JsonRpcError -> result.id
            is Modal.Model.JsonRpcResponse.JsonRpcResult -> result.id
        }
        pendingWalletConnectRequests.remove(requestId)?.complete(response)
    }

    private suspend fun signPersonalMessage(
        message: String,
        address: String,
        chainId: String,
    ): String {
        val params = Json.encodeToString(
            ListSerializer(String.serializer()),
            listOf(message, address),
        )
        val request = Request(
            method = "personal_sign",
            params = params,
            chainId = chainId,
            expiry = null,
        )

        val requestOutcome = CompletableDeferred<Any>()

        AppKit.request(
            request = request,
            onSuccess = { result ->
                requestOutcome.complete(result)
            },
            onError = { error ->
                requestOutcome.complete(error)
            },
        )

        val requestResult = when (val outcome = withTimeout(30_000) { requestOutcome.await() }) {
            is Throwable -> throw outcome
            is SentRequestResult -> outcome
            else -> error("Wallet request failed before returning a signature request handle.")
        }

        return when (requestResult) {
            is SentRequestResult.Coinbase -> {
                val first = requestResult.results.firstOrNull()
                    ?: error("Coinbase did not return a signature.")
                when (first) {
                    is CoinbaseResult.Result -> first.value
                    is CoinbaseResult.Error -> error(first.message)
                }
            }
            is SentRequestResult.WalletConnect -> {
                val responseDeferred = CompletableDeferred<Modal.Model.SessionRequestResponse>()
                pendingWalletConnectRequests[requestResult.requestId] = responseDeferred
                try {
                    val response = withTimeout(120_000) { responseDeferred.await() }
                    when (val jsonRpcResult = response.result) {
                        is Modal.Model.JsonRpcResponse.JsonRpcError -> error(jsonRpcResult.message)
                        is Modal.Model.JsonRpcResponse.JsonRpcResult ->
                            jsonRpcResult.result as? String
                                ?: error("Wallet returned a non-string signature.")
                    }
                } finally {
                    pendingWalletConnectRequests.remove(requestResult.requestId)
                }
            }
        }
    }

    private fun walletClientTypeFor(connectorType: Modal.ConnectorType?): WalletClientType =
        when (connectorType) {
            Modal.ConnectorType.COINBASE -> WalletClientType.CoinbaseWallet
            Modal.ConnectorType.WALLET_CONNECT, null -> WalletClientType.Other
        }

    private fun connectorTypeValue(connectorType: Modal.ConnectorType?): String =
        when (connectorType) {
            Modal.ConnectorType.COINBASE -> "coinbase_wallet"
            Modal.ConnectorType.WALLET_CONNECT, null -> "wallet_connect"
        }

    private fun resolveSiweChainId(chain: Modal.Model.Chain): String {
        val fromReference = chain.chainReference.trim()
        if (fromReference.isNotEmpty()) {
            return fromReference
        }

        val parsedFromId = chain.id.substringAfter(':', "")
            .trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        if (parsedFromId != null) {
            return parsedFromId
        }

        error("Unsupported wallet chain id `${chain.id}` for SIWE.")
    }

    private fun extractAddress(account: Account?): String? {
        return account?.address?.takeIf { it.isNotBlank() }
    }
}
