package sc.pirate.app.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.auth.PrivyClientStore
import sc.pirate.app.auth.PrivyRuntimeConfig
import android.util.Log

class SessionRefresher(private val app: PirateApp) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var refreshJob: Job? = null

    fun start() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            app.sessionStore.observe().collectLatest { session ->
                if (session == null) return@collectLatest
                scheduleRefresh(session.accessToken)
            }
        }
    }

    private suspend fun scheduleRefresh(token: String) {
        val expiry = SessionExpiry.accessTokenExpiryMs(token)
        if (expiry == null) return
        val refreshAt = expiry - REFRESH_WINDOW_MS
        val delayMs = (refreshAt - System.currentTimeMillis()).coerceAtLeast(0)
        if (delayMs > 0) {
            delay(delayMs)
        }
        attemptRefresh(retryCount = 0)
    }

    private suspend fun attemptRefresh(retryCount: Int) {
        val privyConfig = PrivyRuntimeConfig.fromBuildConfig()
        if (privyConfig.disabledReason() != null) return
        val user = PrivyClientStore.lastAuthenticatedUser
            ?: PrivyClientStore.get(app, privyConfig).getUser()
        if (user == null) {
            Log.w(TAG, "Cannot refresh: no authenticated Privy user stored")
            return
        }
        PrivyClientStore.setUser(user)
        try {
            val accessToken = user.getAccessToken().getOrThrow()
            val session = app.repositories.authRepository.exchangeSession(
                SessionExchangeProof(
                    type = "privy_access_token",
                    privyAccessToken = accessToken,
                )
            )
            app.sessionStore.set(session)
            Log.i(TAG, "Session refreshed successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Session refresh failed (attempt ${retryCount + 1}): ${e.message}")
            if (retryCount < MAX_RETRIES) {
                delay(RETRY_DELAY_MS)
                attemptRefresh(retryCount + 1)
            }
        }
    }

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    companion object {
        private const val TAG = "SessionRefresher"
        private const val REFRESH_WINDOW_MS = 5 * 60 * 1000L
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 30_000L
    }
}
