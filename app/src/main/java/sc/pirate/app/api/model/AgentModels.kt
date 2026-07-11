package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAgentListResponse(
    val items: List<UserAgent> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class UserAgent(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val status: String,
    val handle: AgentHandle? = null,
    @SerialName("current_ownership") val currentOwnership: AgentOwnershipRecord? = null,
    val created: Long,
)

@Serializable
data class AgentHandle(
    @SerialName("label_display") val labelDisplay: String,
    val status: String,
)

@Serializable
data class AgentOwnershipRecord(
    @SerialName("ownership_provider") val ownershipProvider: String,
    @SerialName("ownership_state") val ownershipState: String,
    @SerialName("verified_at") val verifiedAt: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
)
