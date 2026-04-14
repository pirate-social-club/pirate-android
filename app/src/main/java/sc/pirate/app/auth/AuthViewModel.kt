package sc.pirate.app.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.privy.auth.PrivyUser
import io.privy.auth.oAuth.OAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.SessionExchangeProof
import sc.pirate.app.api.SessionStore
import sc.pirate.app.api.model.SessionExchangeResponse

sealed class AuthUiState {
    data object Idle : AuthUiState()
    data object Loading : AuthUiState()
    data class Authenticated(val session: SessionExchangeResponse) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val apiClient get() = app.apiClient
    private val sessionStore get() = app.sessionStore
    private val privyConfig = PrivyRuntimeConfig.fromBuildConfig()

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = sessionStore.get()
            if (existing != null) {
                _state.value = AuthUiState.Authenticated(existing)
            }
        }
    }

    fun loginWithGoogle() {
        loginWithOAuth(OAuthProvider.Google)
    }

    fun loginWithTwitter() {
        loginWithOAuth(OAuthProvider.Twitter)
    }

    private fun loginWithOAuth(provider: OAuthProvider) {
        if (privyConfig.disabledReason() != null) {
            _state.value = AuthUiState.Error(privyConfig.disabledReason()!!)
            return
        }

        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                val user = privy.oAuth.login(
                    oAuthProvider = provider,
                    appUrlScheme = "pirate",
                ).getOrThrow()
                exchangePrivyToken(user)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "OAuth login failed")
            }
        }
    }

    fun loginWithEmail(email: String, code: String) {
        if (privyConfig.disabledReason() != null) {
            _state.value = AuthUiState.Error(privyConfig.disabledReason()!!)
            return
        }

        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                val user = privy.email.loginWithCode(
                    code = code.trim(),
                    email = email.trim(),
                ).getOrThrow()
                exchangePrivyToken(user)
            } catch (e: Exception) {
                _state.value = AuthUiState.Error(e.message ?: "Email login failed")
            }
        }
    }

    fun sendEmailCode(email: String) {
        if (privyConfig.disabledReason() != null) return

        viewModelScope.launch {
            try {
                val privy = PrivyClientStore.get(getApplication(), privyConfig)
                privy.email.sendCode(email.trim()).getOrThrow()
            } catch (_: Exception) { }
        }
    }

    private suspend fun exchangePrivyToken(user: PrivyUser) {
        val accessToken = user.getAccessToken().getOrThrow()
        val session = ApiClient.Auth.sessionExchange(
            SessionExchangeProof(
                type = "privy_access_token",
                privyAccessToken = accessToken,
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
            sessionStore.clear()
            _state.value = AuthUiState.Idle
        }
    }
}
