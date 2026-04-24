package sc.pirate.app.verification

import android.net.Uri

object VerificationDeepLinks {
    const val SCHEME = "pirate"
    const val HOST = "verification"
    const val CALLBACK_PATH = "/callback"

    fun buildCallbackUri(verificationSessionId: String, provider: String): Uri =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .path(CALLBACK_PATH)
            .appendQueryParameter("provider", provider)
            .appendQueryParameter("verification_session_id", verificationSessionId)
            .build()

    fun isVerificationCallback(uri: Uri): Boolean =
        uri.scheme == SCHEME && uri.host == HOST && uri.path == CALLBACK_PATH
}
