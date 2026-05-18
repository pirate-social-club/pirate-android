package sc.pirate.app.verification

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import org.very.sdk.VeryConfig
import org.very.sdk.VeryPresentationStyle
import org.very.sdk.VerySDK
import sc.pirate.app.BuildConfig
import kotlin.coroutines.resume

data class VeryNativeAuthenticationResult(
    val isSuccess: Boolean,
    val signedToken: String? = null,
    val userId: String? = null,
    val errorMessage: String? = null,
)

object VeryNativeSdk {
    val sdkKey: String
        get() = BuildConfig.VERY_SDK_KEY.trim()

    fun isConfigured(): Boolean = sdkKey.isNotBlank()

    fun isSupported(context: Context): Boolean {
        if (!isConfigured() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching { VerySDK.isSupported(context) }.getOrDefault(false)
    }

    suspend fun authenticate(
        context: Context,
        userId: String? = null,
    ): VeryNativeAuthenticationResult = suspendCancellableCoroutine { continuation ->
        val activity = context.findActivity()
        if (activity == null) {
            continuation.resume(
                VeryNativeAuthenticationResult(
                    isSuccess = false,
                    errorMessage = "Very native verification needs an active screen.",
                )
            )
            return@suspendCancellableCoroutine
        }

        if (!isSupported(activity)) {
            continuation.resume(
                VeryNativeAuthenticationResult(
                    isSuccess = false,
                    errorMessage = "Very native verification is not supported on this device.",
                )
            )
            return@suspendCancellableCoroutine
        }

        val config = VeryConfig(
            sdkKey = sdkKey,
            userId = userId,
            themeMode = "dark",
        )

        runCatching {
            VerySDK.authenticate(
                context = activity,
                config = config,
                presentationStyle = VeryPresentationStyle.FULL_SCREEN,
            ) sdkCallback@ { result ->
                if (!continuation.isActive) return@sdkCallback

                if (result.isSuccess) {
                    continuation.resume(
                        VeryNativeAuthenticationResult(
                            isSuccess = true,
                            signedToken = result.signedToken?.trim(),
                            userId = result.userId?.trim(),
                        )
                    )
                    return@sdkCallback
                }

                val message = result.errorMessage?.takeIf { it.isNotBlank() }
                    ?: result.errorType.toString()
                continuation.resume(
                    VeryNativeAuthenticationResult(
                        isSuccess = false,
                        errorMessage = message,
                    )
                )
            }
        }.onFailure { error ->
            if (!continuation.isActive) return@onFailure
            continuation.resume(
                VeryNativeAuthenticationResult(
                    isSuccess = false,
                    errorMessage = error.message ?: "Very native verification could not start.",
                )
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
