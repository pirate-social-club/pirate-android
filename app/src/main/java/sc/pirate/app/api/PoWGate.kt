package sc.pirate.app.api

import android.util.Base64
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
                val challenge = apiClient.verification.getAltchaChallenge(scope, action)
                val solution = withContext(Dispatchers.Default) {
                    AltchaSolver.solve(challenge) {
                        coroutineContext.ensureActive()
                        false
                    } ?: throw IllegalStateException("Proof-of-work challenge could not be solved.")
                }
                val payload = AltchaPayload(challenge = challenge, solution = solution)
                val payloadJson = apiClient.json.encodeToString(payload)
                val header = Base64.encodeToString(
                    payloadJson.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP,
                )
                attempt(header)
            } finally {
                onSolvingProofOfWorkChanged(false)
            }
        }
    }

    private fun ApiError.isAltchaPowRequired(): Boolean =
        status == 403 &&
            code == "gate_failed" &&
            details?.missingCapabilities?.contains("altcha_pow") == true
}
