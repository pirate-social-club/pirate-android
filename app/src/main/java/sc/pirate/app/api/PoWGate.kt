package sc.pirate.app.api

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlin.coroutines.coroutineContext
import sc.pirate.app.api.model.AltchaPayload

class PoWGate(private val apiClient: ApiClient) {
    suspend fun <T> execute(
        scope: String,
        action: String,
        onSolvingProofOfWorkChanged: (Boolean) -> Unit = {},
        attempt: suspend (altchaHeader: String?) -> T,
    ): T {
        return try {
            attempt(null)
        } catch (error: ApiError) {
            if (!error.isAltchaPowRequired()) throw error
            onSolvingProofOfWorkChanged(true)
            try {
                Log.d(TAG, "ALTCHA required; fetching challenge scope=$scope action=$action")
                val challenge = apiClient.verification.getAltchaChallenge(scope, action)
                Log.d(
                    TAG,
                    "ALTCHA challenge fetched action=${challenge.parameters.data?.get("action")} prefixLength=${challenge.parameters.keyPrefix.length}",
                )
                val solution = withContext(Dispatchers.Default) {
                    AltchaSolver.solve(challenge) {
                        coroutineContext.ensureActive()
                        false
                    } ?: throw IllegalStateException("Proof-of-work challenge could not be solved.")
                }
                Log.d(TAG, "ALTCHA solved counter=${solution.counter}; retrying gated request")
                val payload = AltchaPayload(challenge = challenge, solution = solution)
                val payloadJson = apiClient.json.encodeToString(payload)
                val header = Base64.encodeToString(
                    payloadJson.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                try {
                    attempt(header)
                } catch (retryError: ApiError) {
                    Log.w(
                        TAG,
                        "ALTCHA retry failed status=${retryError.status} code=${retryError.code} message=${retryError.message?.take(120)} missing=${retryError.details?.missingCapabilities}",
                    )
                    throw retryError
                }
            } finally {
                onSolvingProofOfWorkChanged(false)
            }
        }
    }

    private fun ApiError.isAltchaPowRequired(): Boolean =
        status == 403 &&
            code == "gate_failed" &&
            details?.missingCapabilities?.contains("altcha_pow") == true

    private companion object {
        const val TAG = "PoWGate"
    }
}
