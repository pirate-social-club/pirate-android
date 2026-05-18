package sc.pirate.app.verification

import android.content.Context
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sc.pirate.app.api.CompleteVerificationSessionRequest
import sc.pirate.app.api.StartVerificationSessionRequest
import sc.pirate.app.shared.api.VerificationRepository

data class VeryVerificationLaunchResult(
    val verified: Boolean,
    val verificationSessionId: String? = null,
    val failureReason: String? = null,
)

object VeryVerificationLauncher {
    suspend fun launch(
        context: Context,
        verificationRepository: VerificationRepository,
        verificationIntent: String,
    ): VeryVerificationLaunchResult {
        if (!VeryNativeSdk.isConfigured()) {
            return VeryVerificationLaunchResult(
                verified = false,
                failureReason = "Native verification is not configured for this build. VERY_SDK_KEY is missing.",
            )
        }

        if (!VeryNativeSdk.isSupported(context)) {
            return VeryVerificationLaunchResult(
                verified = false,
                failureReason = "Very native verification is not supported on this device.",
            )
        }

        val session = verificationRepository.startSession(
            StartVerificationSessionRequest(
                provider = "very",
                providerMode = "native_sdk",
                verificationIntent = verificationIntent,
            )
        )

        if (session.providerMode != "native_sdk" || session.launch?.mode != "native_sdk") {
            return VeryVerificationLaunchResult(
                verified = false,
                verificationSessionId = session.verificationSessionId,
                failureReason = "Server did not return a native_sdk session (got mode=${session.launch?.mode ?: "null"}). Native verification is unavailable.",
            )
        }

        val nativeResult = VeryNativeSdk.authenticate(context)
        val signedToken = nativeResult.signedToken?.takeIf { it.isNotBlank() }
        if (!nativeResult.isSuccess || signedToken == null) {
            return VeryVerificationLaunchResult(
                verified = false,
                verificationSessionId = session.verificationSessionId,
                failureReason = nativeResult.errorMessage ?: "Very native verification did not return a signed token.",
            )
        }

        val completed = verificationRepository.completeSession(
            verificationSessionId = session.verificationSessionId,
            input = CompleteVerificationSessionRequest(
                providerPayloadRef = JsonObject(
                    mapOf(
                        "mode" to JsonPrimitive("native_sdk"),
                        "signed_token" to JsonPrimitive(signedToken),
                    )
                ),
            ),
        )
        val verified = completed.status.equals("verified", ignoreCase = true)
        return VeryVerificationLaunchResult(
            verified = verified,
            verificationSessionId = completed.verificationSessionId,
            failureReason = if (verified) null else completed.failureReason ?: "Very verification is still pending.",
        )
    }
}
