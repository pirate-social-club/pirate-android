package sc.pirate.app.api

import kotlinx.serialization.Serializable

@Serializable
data class SessionExchangeProof(
    val type: String,
    @kotlinx.serialization.SerialName("privy_access_token") val privyAccessToken: String? = null,
    @kotlinx.serialization.SerialName("wallet_address") val walletAddress: String? = null,
    val jwt: String? = null,
)

@Serializable
data class SessionExchangeRequest(
    val proof: SessionExchangeProof,
)

@Serializable
data class StartVerificationSessionRequest(
    val provider: String,
    @kotlinx.serialization.SerialName("wallet_attachment_id") val walletAttachmentId: String? = null,
)

@Serializable
data class CompleteVerificationSessionRequest(
    @kotlinx.serialization.SerialName("attestation_id") val attestationId: String? = null,
    @kotlinx.serialization.SerialName("proof_hash") val proofHash: String? = null,
)

@Serializable
data class StartNamespaceVerificationSessionRequest(
    val family: String,
    @kotlinx.serialization.SerialName("root_label") val rootLabel: String,
)

@Serializable
data class CompleteNamespaceVerificationSessionRequest(
    @kotlinx.serialization.SerialName("restart_challenge") val restartChallenge: Boolean? = null,
)

@Serializable
data class StartRedditVerificationRequest(
    @kotlinx.serialization.SerialName("reddit_username") val redditUsername: String,
)

@Serializable
data class StartRedditImportRequest(
    @kotlinx.serialization.SerialName("reddit_username") val redditUsername: String,
)

@Serializable
data class RenameHandleRequest(
    @kotlinx.serialization.SerialName("desired_label") val desiredLabel: String,
)

@Serializable
data class RenameHandleResponse(
    @kotlinx.serialization.SerialName("global_handle_id") val globalHandleId: String,
    val label: String,
    val tier: String,
    val status: String,
)
