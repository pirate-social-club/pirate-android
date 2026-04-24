package sc.pirate.app.api

import kotlinx.serialization.Serializable

typealias VerificationProvider = String
typealias VerificationProviderMode = String
typealias VerificationIntent = String

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
    val provider: VerificationProvider,
    @kotlinx.serialization.SerialName("provider_mode") val providerMode: VerificationProviderMode? = null,
    @kotlinx.serialization.SerialName("requested_capabilities") val requestedCapabilities: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("wallet_attachment_id") val walletAttachmentId: String? = null,
    @kotlinx.serialization.SerialName("verification_intent") val verificationIntent: VerificationIntent? = null,
    @kotlinx.serialization.SerialName("policy_id") val policyId: String? = null,
)

@Serializable
data class CompleteVerificationSessionRequest(
    @kotlinx.serialization.SerialName("attestation_id") val attestationId: String? = null,
    val proof: String? = null,
    @kotlinx.serialization.SerialName("proof_hash") val proofHash: String? = null,
    @kotlinx.serialization.SerialName("provider_payload_ref") val providerPayloadRef: String? = null,
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
data class AttachNamespaceRequest(
    @kotlinx.serialization.SerialName("namespace_verification_id") val namespaceVerificationId: String,
)

@Serializable
data class SetPendingNamespaceSessionRequest(
    @kotlinx.serialization.SerialName("namespace_verification_session_id") val namespaceVerificationSessionId: String? = null,
)

@Serializable
data class PostVoteRequest(
    val value: Int,
)

@Serializable
data class CommentVoteRequest(
    val value: Int,
)

@Serializable
data class CreateCommentRequest(
    val body: String,
    @kotlinx.serialization.SerialName("identity_mode") val identityMode: String? = null,
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

@Serializable
data class MarkNotificationsReadRequest(
    @kotlinx.serialization.SerialName("event_ids") val eventIds: List<String> = emptyList(),
)

@Serializable
data class DismissNotificationTaskRequest(
    @kotlinx.serialization.SerialName("task_id") val taskId: String,
)
