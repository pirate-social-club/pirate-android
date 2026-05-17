package sc.pirate.app.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.privy.auth.PrivyUser
import io.privy.auth.oAuth.OAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiError
import sc.pirate.app.api.SessionExchangeProof
import sc.pirate.app.api.model.SessionExchangeResponse

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class EmailSending(val email: String) : AuthUiState()
    data class EmailCodeSent(val email: String) : AuthUiState()
    data class EmailVerifying(val email: String) : AuthUiState()
    data class Authenticated(val session: SessionExchangeResponse) : AuthUiState()
    data class Unavailable(val message: String) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val authRepository get() = app.repositories.authRepository
    private val sessionStore get() = app.sessionStore
    private val privyConfig = PrivyRuntimeConfig.fromBuildConfig()

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = sessionStore.get()
            if (existing != null) {
                _state.value = AuthUiState.Authenticated(existing)
            } else {
                val disabledReason = privyConfig.disabledReason()
                if (disabledReason != null) {
                    _state.value = AuthUiState.Unavailable(disabledReason)
                } else {
                    restorePrivySession()
                }
            }
        }
    }

    private suspend fun restorePrivySession() {
        try {
            val privy = PrivyClientStore.get(getApplication(), privyConfig)
            privy.awaitReady()
            val user = PrivyClientStore.lastAuthenticatedUser ?: privy.getUser()
            if (user != null) {
                PrivyClientStore.setUser(user)
                exchangePrivyToken(user)
            } else {
                _state.value = AuthUiState.Idle
            }
        } catch (_: Exception) {
            _state.value = privyConfig.disabledReason()?.let(AuthUiState::Unavailable) ?: AuthUiState.Idle
        }
    }

    fun loginWithGoogle() {
        loginWithOAuth(OAuthProvider.Google)
    }

    fun loginWithTwitter() {
        loginWithOAuth(OAuthProvider.Twitter)
    }

    fun loginWithConnectedWallet() {
        if (privyConfig.disabledReason() != null) {
            _state.value = AuthUiState.Unavailable(privyConfig.disabledReason()!!)
            return
        }

        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                privy.awaitReady()
                val linkedWallet = app.reownManager.loginWithConnectedWallet(privy)
                PrivyClientStore.setUser(linkedWallet.user)
                exchangePrivyToken(
                    user = linkedWallet.user,
                    walletAddress = linkedWallet.walletAddress,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Wallet login failed", e)
                _state.value = AuthUiState.Error(e.authMessage("Wallet login failed."))
            }
        }
    }

    private fun loginWithOAuth(provider: OAuthProvider) {
        if (privyConfig.disabledReason() != null) {
            _state.value = AuthUiState.Unavailable(privyConfig.disabledReason()!!)
            return
        }

        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                val user = privy.oAuth.login(
                    oAuthProvider = provider,
                    appUrlScheme = privyConfig.redirectScheme,
                ).getOrThrow()
                PrivyClientStore.setUser(user)
                exchangePrivyToken(user)
            } catch (e: Exception) {
                Log.w(TAG, "${provider.name} login failed", e)
                _state.value = AuthUiState.Error(e.authMessage("${provider.name} login failed."))
            }
        }
    }

    fun loginWithEmail(email: String, code: String) {
        if (privyConfig.disabledReason() != null) {
            _state.value = AuthUiState.Unavailable(privyConfig.disabledReason()!!)
            return
        }

        viewModelScope.launch {
            val trimmedEmail = email.trim()
            _state.value = AuthUiState.EmailVerifying(trimmedEmail)
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                val user = privy.email.loginWithCode(
                    code = code.trim(),
                    email = trimmedEmail,
                ).getOrThrow()
                PrivyClientStore.setUser(user)
                exchangePrivyToken(user)
            } catch (e: Exception) {
                Log.w(TAG, "Email login failed", e)
                _state.value = AuthUiState.Error(e.authMessage("Email sign-in failed."))
            }
        }
    }

    fun sendEmailCode(email: String) {
        if (privyConfig.disabledReason() != null) {
            _state.value = AuthUiState.Unavailable(privyConfig.disabledReason()!!)
            return
        }

        viewModelScope.launch {
            val trimmedEmail = email.trim()
            _state.value = AuthUiState.EmailSending(trimmedEmail)
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                privy.email.sendCode(trimmedEmail).getOrThrow()
                _state.value = AuthUiState.EmailCodeSent(trimmedEmail)
            } catch (e: Exception) {
                Log.w(TAG, "Could not send email code", e)
                _state.value = AuthUiState.Error(e.authMessage("Could not send email code."))
            }
        }
    }

    private suspend fun exchangePrivyToken(
        user: PrivyUser,
        walletAddress: String? = null,
    ) {
        val accessToken = user.getAccessToken().getOrThrow()
        val session = authRepository.exchangeSession(
            SessionExchangeProof(
                type = "privy_access_token",
                privyAccessToken = accessToken,
                walletAddress = walletAddress,
            )
        )
        sessionStore.set(session)
        _state.value = AuthUiState.Authenticated(session)
    }

    fun logout() {
        viewModelScope.launch {
            try {
                if (privyConfig.disabledReason() == null) {
                    val privy = PrivyClientStore.get(getApplication(), privyConfig)
                    privy.logout()
                }
            } catch (_: Exception) { }
            PrivyClientStore.setUser(null)
            sessionStore.clear()
            _state.value = privyConfig.disabledReason()?.let(AuthUiState::Unavailable) ?: AuthUiState.Idle
        }
    }

    private fun Exception.authMessage(fallback: String): String {
        if (this is ApiError) {
            val retryHint = if (retryable) " Try again." else ""
            return "${message ?: fallback} (HTTP $status, $code).$retryHint"
        }
        val detail = message?.trim()?.takeIf { it.isNotBlank() }
        if (detail != null) return detail
        val type = this::class.java.simpleName.takeIf { it.isNotBlank() } ?: "unknown error"
        return "$fallback ($type)"
    }

    private companion object {
        const val TAG = "PirateAuth"
    }
}
