package sc.pirate.app.walletconnect

import sc.pirate.app.BuildConfig

data class ReownRuntimeConfig(
    val projectId: String,
    val redirectUri: String,
) {
    fun disabledReason(): String? =
        when {
            projectId.isBlank() -> "Missing REOWN_PROJECT_ID."
            redirectUri.isBlank() -> "Missing REOWN_REDIRECT_URI."
            else -> null
        }

    companion object {
        fun fromBuildConfig(): ReownRuntimeConfig =
            ReownRuntimeConfig(
                projectId = BuildConfig.REOWN_PROJECT_ID.trim(),
                redirectUri = BuildConfig.REOWN_REDIRECT_URI.trim(),
            )
    }
}
