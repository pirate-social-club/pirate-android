package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class GlobalHandle(
    val label: String,
    val tier: String,
    val status: String,
)

@Serializable
data class Profile(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("global_handle") val globalHandle: GlobalHandle? = null,
    @SerialName("preferred_locale") val preferredLocale: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Community(
    @SerialName("community_id") val communityId: String,
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    @SerialName("membership_mode") val membershipMode: String,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Post(
    @SerialName("post_id") val postId: String,
    @SerialName("community_id") val communityId: String,
    val title: String? = null,
    val body: String? = null,
    @SerialName("post_type") val postType: String? = null,
    @SerialName("author_user_id") val authorUserId: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class LocalizedPostResponse(
    val post: Post,
    val locale: String? = null,
    val flair: String? = null,
)

@Serializable
data class PostListResponse(
    val items: List<LocalizedPostResponse> = emptyList(),
)

@Serializable
data class CreatePostRequest(
    val title: String,
    val body: String? = null,
    @SerialName("post_type") val postType: String = "text",
    @SerialName("age_gate_policy") val ageGatePolicy: String? = null,
    @SerialName("flair_id") val flairId: String? = null,
)

@Serializable
data class OnboardingStatus(
    @SerialName("reddit_verification_status") val redditVerificationStatus: String,
    @SerialName("reddit_import_status") val redditImportStatus: String,
    @SerialName("cleanup_rename_available") val cleanupRenameAvailable: Boolean,
    @SerialName("unique_human_verification_status") val uniqueHumanVerificationStatus: String? = null,
)

@Serializable
data class RedditVerification(
    val status: String,
    @SerialName("reddit_username") val redditUsername: String? = null,
    @SerialName("verification_hint") val verificationHint: String? = null,
    @SerialName("code_placement_surface") val codePlacementSurface: String? = null,
    @SerialName("failure_code") val failureCode: String? = null,
)

@Serializable
data class SubredditKarma(
    val subreddit: String,
    val karma: Int? = null,
    val posts: Int? = null,
    @SerialName("rank_source") val rankSource: String? = null,
)

@Serializable
data class SuggestedCommunity(
    @SerialName("community_id") val communityId: String,
    val name: String,
    val reason: String,
)

@Serializable
data class RedditImportSummary(
    @SerialName("account_age_days") val accountAgeDays: Int? = null,
    @SerialName("global_karma") val globalKarma: Int? = null,
    @SerialName("top_subreddits") val topSubreddits: List<SubredditKarma> = emptyList(),
    @SerialName("moderator_of") val moderatorOf: List<String> = emptyList(),
    @SerialName("inferred_interests") val inferredInterests: List<String> = emptyList(),
    @SerialName("suggested_communities") val suggestedCommunities: List<SuggestedCommunity> = emptyList(),
)

@Serializable
data class VerificationSession(
    @SerialName("verification_session_id") val verificationSessionId: String,
    val status: String,
    val provider: String,
)

@Serializable
data class NamespaceVerificationSession(
    @SerialName("namespace_verification_session_id") val namespaceVerificationSessionId: String,
    val status: String,
    @SerialName("challenge_host") val challengeHost: String? = null,
    @SerialName("challenge_txt_value") val challengeTxtValue: String? = null,
    @SerialName("challenge_expires_at") val challengeExpiresAt: String? = null,
)

@Serializable
data class WalletAttachmentSummary(
    @SerialName("wallet_address") val walletAddress: String,
    @SerialName("chain_id") val chainId: String? = null,
)

@Serializable
data class SessionExchangeResponse(
    @SerialName("access_token") val accessToken: String,
    val user: User,
    val profile: Profile,
    val onboarding: OnboardingStatus,
    @SerialName("wallet_attachments") val walletAttachments: List<WalletAttachmentSummary> = emptyList(),
)

@Serializable
data class Job(
    @SerialName("job_id") val jobId: String,
    val status: String,
)

@Serializable
data class CommunityJoinResponse(
    @SerialName("community_id") val communityId: String,
    val status: String,
)

@Serializable
data class ErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val retryable: Boolean? = null,
)
