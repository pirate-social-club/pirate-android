package sc.pirate.app.verification

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingVerificationSession(
    val provider: String,
    val verificationSessionId: String,
)

sealed class VerificationCallbackResult {
    data class Completed(
        val provider: String,
        val verificationSessionId: String?,
        val proof: String,
    ) : VerificationCallbackResult()

    data class Failed(
        val provider: String,
        val verificationSessionId: String?,
        val reason: String,
    ) : VerificationCallbackResult()

    data class Expired(
        val provider: String,
        val verificationSessionId: String?,
    ) : VerificationCallbackResult()
}

class VerificationCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _callbackResults = MutableStateFlow<VerificationCallbackResult?>(null)
    val callbackResults: StateFlow<VerificationCallbackResult?> = _callbackResults.asStateFlow()

    fun savePendingSession(session: PendingVerificationSession) {
        prefs.edit()
            .putString(KEY_PROVIDER, session.provider)
            .putString(KEY_SESSION_ID, session.verificationSessionId)
            .apply()
    }

    fun readPendingSession(): PendingVerificationSession? {
        val provider = prefs.getString(KEY_PROVIDER, null)
        val verificationSessionId = prefs.getString(KEY_SESSION_ID, null)
        if (provider.isNullOrBlank() || verificationSessionId.isNullOrBlank()) return null
        return PendingVerificationSession(
            provider = provider,
            verificationSessionId = verificationSessionId,
        )
    }

    fun clearPendingSession() {
        prefs.edit()
            .remove(KEY_PROVIDER)
            .remove(KEY_SESSION_ID)
            .apply()
    }

    fun clearCallbackResult() {
        _callbackResults.value = null
    }

    fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val callback = parseVerificationCallback(data) ?: return
        _callbackResults.value = callback
    }

    private fun parseVerificationCallback(uri: Uri): VerificationCallbackResult? {
        if (!VerificationDeepLinks.isVerificationCallback(uri)) return null

        val pendingSession = readPendingSession() ?: return null

        val provider = getCallbackParam(uri, "provider")
            ?.takeIf { it.isNotBlank() }
            ?: pendingSession.provider
        if (provider != pendingSession.provider) return null

        val verificationSessionId = getCallbackParam(uri, "verification_session_id")
            ?.takeIf { it.isNotBlank() }
            ?: getCallbackParam(uri, "self_verification_session_id")
            ?.takeIf { it.isNotBlank() }
            ?: pendingSession.verificationSessionId
        if (verificationSessionId != pendingSession.verificationSessionId) return null

        val proof = getCallbackParam(uri, "proof")?.trim()
        if (!proof.isNullOrBlank()) {
            return VerificationCallbackResult.Completed(
                provider = provider,
                verificationSessionId = verificationSessionId,
                proof = proof,
            )
        }

        if (getCallbackParam(uri, "expired") == "true") {
            return VerificationCallbackResult.Expired(
                provider = provider,
                verificationSessionId = verificationSessionId,
            )
        }

        val error = getCallbackParam(uri, "error")?.trim()
        if (!error.isNullOrBlank()) {
            return VerificationCallbackResult.Failed(
                provider = provider,
                verificationSessionId = verificationSessionId,
                reason = error,
            )
        }

        return null
    }

    private fun getCallbackParam(uri: Uri, name: String): String? =
        uri.getQueryParameter(name) ?: getFragmentParam(uri.fragment, name)

    private fun getFragmentParam(fragment: String?, name: String): String? {
        if (fragment.isNullOrBlank()) return null

        val rawParams = fragment.substringAfter("?", fragment).removePrefix("?")
        if (!rawParams.contains("=")) return null

        return Uri.parse("pirate://verification-fragment?$rawParams").getQueryParameter(name)
    }

    private companion object {
        const val PREFS_NAME = "pirate_verification"
        const val KEY_PROVIDER = "pending_provider"
        const val KEY_SESSION_ID = "pending_session_id"
    }
}
