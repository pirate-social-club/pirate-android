package sc.pirate.app.auth

import sc.pirate.app.BuildConfig

data class PrivyRuntimeConfig(
    val enabled: Boolean,
    val appId: String,
    val appClientId: String,
) {
    fun disabledReason(): String? =
        when {
            !enabled -> "Privy auth is disabled for this build."
            appId.isBlank() -> "Missing PRIVY_APP_ID."
            appClientId.isBlank() -> "Missing PRIVY_APP_CLIENT_ID."
            else -> null
        }

    companion object {
        fun fromBuildConfig(): PrivyRuntimeConfig =
            PrivyRuntimeConfig(
                enabled = BuildConfig.PRIVY_APP_ID.isNotBlank() && BuildConfig.PRIVY_APP_CLIENT_ID.isNotBlank(),
                appId = BuildConfig.PRIVY_APP_ID.trim(),
                appClientId = BuildConfig.PRIVY_APP_CLIENT_ID.trim(),
            )
    }
}
