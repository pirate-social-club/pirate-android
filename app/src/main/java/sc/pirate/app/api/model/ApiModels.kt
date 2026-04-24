package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SelfVerificationDisclosures(
    @SerialName("issuing_state") val issuingState: Boolean? = null,
    val name: Boolean? = null,
    @SerialName("passport_number") val passportNumber: Boolean? = null,
    val nationality: Boolean? = null,
    @SerialName("date_of_birth") val dateOfBirth: Boolean? = null,
    val gender: Boolean? = null,
    @SerialName("expiry_date") val expiryDate: Boolean? = null,
    val ofac: Boolean? = null,
    @SerialName("excluded_countries") val excludedCountries: List<String>? = null,
    @SerialName("minimum_age") val minimumAge: Int? = null,
)

@Serializable
data class SelfVerificationLaunch(
    @SerialName("app_name") val appName: String,
    @SerialName("logo_base64") val logoBase64: String? = null,
    val header: String? = null,
    val endpoint: String,
    @SerialName("endpoint_type") val endpointType: String,
    val scope: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_id_type") val userIdType: String,
    val disclosures: SelfVerificationDisclosures,
    @SerialName("deeplink_callback") val deeplinkCallback: String? = null,
    val version: Int? = null,
    @SerialName("user_defined_data") val userDefinedData: String? = null,
    @SerialName("chain_id") val chainId: Int? = null,
    @SerialName("dev_mode") val devMode: Boolean? = null,
)

@Serializable
data class VeryWidgetLaunch(
    @SerialName("app_id") val appId: String,
    val context: String,
    @SerialName("type_id") val typeId: String,
    val query: JsonObject = JsonObject(emptyMap()),
    @SerialName("verify_url") val verifyUrl: String,
)

@Serializable
data class VerificationSessionLaunch(
    val mode: String,
    @SerialName("self_app") val selfApp: SelfVerificationLaunch? = null,
    @SerialName("very_widget") val veryWidget: VeryWidgetLaunch? = null,
)

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
data class PublicProfileCommunitySummary(
    @SerialName("community_id") val communityId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class PublicProfileResolution(
    val profile: Profile,
    @SerialName("requested_handle_label") val requestedHandleLabel: String,
    @SerialName("resolved_handle_label") val resolvedHandleLabel: String,
    @SerialName("is_canonical") val isCanonical: Boolean,
    @SerialName("created_communities") val createdCommunities: List<PublicProfileCommunitySummary> = emptyList(),
)

@Serializable
data class Community(
    @SerialName("community_id") val communityId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    val description: String? = null,
    @SerialName("membership_mode") val membershipMode: String,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("banner_ref") val bannerRef: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class HandlePolicyInput(
    @SerialName("policy_template") val policyTemplate: String = "standard",
)

@Serializable
data class CreateCommunityRequest(
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    @SerialName("database_region") val databaseRegion: String? = "auto",
    @SerialName("membership_mode") val membershipMode: String,
    @SerialName("governance_mode") val governanceMode: String = "centralized",
    @SerialName("default_age_gate_policy") val defaultAgeGatePolicy: String = "none",
    @SerialName("allow_anonymous_identity") val allowAnonymousIdentity: Boolean = false,
    @SerialName("handle_policy") val handlePolicy: HandlePolicyInput = HandlePolicyInput(),
)

@Serializable
data class CommunityCreateAcceptedResponse(
    val community: Community,
    val job: Job,
)

@Serializable
data class UserTask(
    @SerialName("task_id") val taskId: String,
    val type: String,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("subject_id") val subjectId: String,
    val status: String,
    val priority: Int? = null,
    val payload: JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class NotificationTasksResponse(
    val items: List<UserTask> = emptyList(),
)

@Serializable
data class NotificationEvent(
    @SerialName("event_id") val eventId: String,
    val type: String,
    @SerialName("actor_user_id") val actorUserId: String? = null,
    @SerialName("subject_type") val subjectType: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("object_type") val objectType: String? = null,
    @SerialName("object_id") val objectId: String? = null,
    val payload: JsonObject? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NotificationReceipt(
    @SerialName("event_id") val eventId: String,
    @SerialName("recipient_user_id") val recipientUserId: String,
    @SerialName("seen_at") val seenAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NotificationFeedItem(
    val event: NotificationEvent,
    val receipt: NotificationReceipt,
)

@Serializable
data class NotificationFeedResponse(
    val items: List<NotificationFeedItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class CommunityRule(
    @SerialName("rule_id") val ruleId: String,
    val title: String,
    val body: String? = null,
    val position: Int? = null,
    val status: String? = null,
)

@Serializable
data class MembershipGateSummary(
    @SerialName("gate_type") val gateType: String,
    @SerialName("accepted_providers") val acceptedProviders: List<String>? = null,
    @SerialName("required_value") val requiredValue: String? = null,
    @SerialName("required_values") val requiredValues: List<String>? = null,
    @SerialName("excluded_values") val excludedValues: List<String>? = null,
    @SerialName("required_minimum_age") val requiredMinimumAge: Int? = null,
    @SerialName("minimum_score") val minimumScore: Int? = null,
    @SerialName("chain_namespace") val chainNamespace: String? = null,
    @SerialName("contract_address") val contractAddress: String? = null,
    @SerialName("inventory_provider") val inventoryProvider: String? = null,
    @SerialName("min_quantity") val minQuantity: Int? = null,
    @SerialName("asset_filter_label") val assetFilterLabel: String? = null,
    @SerialName("asset_category") val assetCategory: String? = null,
)

@Serializable
data class CommunityReferenceLink(
    @SerialName("community_reference_link_id") val communityReferenceLinkId: String? = null,
    val platform: String? = null,
    val label: String? = null,
    val url: String,
    @SerialName("link_status") val linkStatus: String? = null,
    val verified: Boolean? = null,
)

@Serializable
data class CommunityPreview(
    @SerialName("community_id") val communityId: String,
    @SerialName("display_name") val displayName: String,
    val description: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("banner_ref") val bannerRef: String? = null,
    @SerialName("membership_mode") val membershipMode: String,
    @SerialName("human_verification_lane") val humanVerificationLane: String,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("reference_links") val referenceLinks: List<CommunityReferenceLink>? = null,
    @SerialName("membership_gate_summaries") val membershipGateSummaries: List<MembershipGateSummary> = emptyList(),
    val rules: List<CommunityRule> = emptyList(),
    @SerialName("viewer_membership_status") val viewerMembershipStatus: String? = null,
    @SerialName("viewer_following") val viewerFollowing: Boolean? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class WalletScoreStatus(
    @SerialName("current_score") val currentScore: Int? = null,
    @SerialName("required_score") val requiredScore: Int? = null,
    @SerialName("passing_score") val passingScore: Boolean? = null,
    @SerialName("last_score_timestamp") val lastScoreTimestamp: String? = null,
)

@Serializable
data class JoinEligibility(
    @SerialName("community_id") val communityId: String,
    @SerialName("membership_mode") val membershipMode: String,
    @SerialName("human_verification_lane") val humanVerificationLane: String,
    @SerialName("joinable_now") val joinableNow: Boolean,
    val status: String,
    @SerialName("membership_gate_summaries") val membershipGateSummaries: List<MembershipGateSummary> = emptyList(),
    @SerialName("missing_capabilities") val missingCapabilities: List<String> = emptyList(),
    @SerialName("suggested_verification_provider") val suggestedVerificationProvider: String? = null,
    @SerialName("suggested_verification_intent") val suggestedVerificationIntent: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("wallet_score_status") val walletScoreStatus: WalletScoreStatus? = null,
)

@Serializable
data class ThreadSnapshot(
    @SerialName("thread_root_post_id") val threadRootPostId: String,
    @SerialName("snapshot_seq") val snapshotSeq: Int? = null,
    @SerialName("published_through_comment_created_at") val publishedThroughCommentCreatedAt: String? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("swarm_manifest_ref") val swarmManifestRef: String? = null,
    @SerialName("swarm_feed_ref") val swarmFeedRef: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Post(
    @SerialName("post_id") val postId: String,
    @SerialName("community_id") val communityId: String,
    val title: String? = null,
    val body: String? = null,
    val caption: String? = null,
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("link_og_image_url") val linkOgImageUrl: String? = null,
    @SerialName("link_og_title") val linkOgTitle: String? = null,
    @SerialName("post_type") val postType: String? = null,
    val status: String? = null,
    val visibility: String? = null,
    @SerialName("author_user_id") val authorUserId: String? = null,
    @SerialName("identity_mode") val identityMode: String? = null,
    @SerialName("anonymous_identity_scope") val anonymousIdentityScope: String? = null,
    @SerialName("anonymous_label") val anonymousLabel: String? = null,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("translation_policy") val translationPolicy: String? = null,
    @SerialName("access_mode") val accessMode: String? = null,
    @SerialName("asset_id") val assetId: String? = null,
    @SerialName("age_gate_policy") val ageGatePolicy: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class LocalizedPostResponse(
    val post: Post,
    @SerialName("thread_snapshot") val threadSnapshot: ThreadSnapshot? = null,
    @SerialName("upvote_count") val upvoteCount: Int = 0,
    @SerialName("downvote_count") val downvoteCount: Int = 0,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("viewer_vote") val viewerVote: Int? = null,
    val locale: String? = null,
    val flair: String? = null,
    @SerialName("resolved_locale") val resolvedLocale: String? = null,
    @SerialName("translation_state") val translationState: String? = null,
    @SerialName("machine_translated") val machineTranslated: Boolean? = null,
    @SerialName("translated_title") val translatedTitle: String? = null,
    @SerialName("translated_body") val translatedBody: String? = null,
    @SerialName("translated_caption") val translatedCaption: String? = null,
    @SerialName("source_hash") val sourceHash: String? = null,
)

@Serializable
data class PostListResponse(
    val items: List<LocalizedPostResponse> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class HomeFeedCommunitySummary(
    @SerialName("community_id") val communityId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class HomeFeedItem(
    val community: HomeFeedCommunitySummary,
    val post: LocalizedPostResponse,
)

@Serializable
data class HomeFeedResponse(
    val items: List<HomeFeedItem> = emptyList(),
    @SerialName("top_communities") val topCommunities: List<HomeFeedCommunitySummary> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class PostVoteResponse(
    @SerialName("post_id") val postId: String,
    val value: Int,
)

@Serializable
data class CommentVoteResponse(
    @SerialName("comment_id") val commentId: String,
    val value: Int,
)

@Serializable
data class Comment(
    @SerialName("comment_id") val commentId: String,
    @SerialName("community_id") val communityId: String,
    @SerialName("thread_root_post_id") val threadRootPostId: String,
    @SerialName("parent_comment_id") val parentCommentId: String? = null,
    @SerialName("author_user_id") val authorUserId: String? = null,
    @SerialName("identity_mode") val identityMode: String? = null,
    @SerialName("anonymous_scope") val anonymousScope: String? = null,
    @SerialName("anonymous_label") val anonymousLabel: String? = null,
    val body: String? = null,
    val status: String? = null,
    val depth: Int = 0,
    @SerialName("direct_reply_count") val directReplyCount: Int = 0,
    @SerialName("descendant_count") val descendantCount: Int = 0,
    @SerialName("upvote_count") val upvoteCount: Int = 0,
    @SerialName("downvote_count") val downvoteCount: Int = 0,
    val score: Int = 0,
    @SerialName("last_reply_at") val lastReplyAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CommentListItem(
    val comment: Comment,
    @SerialName("viewer_vote") val viewerVote: Int? = null,
    @SerialName("resolved_locale") val resolvedLocale: String? = null,
    @SerialName("translation_state") val translationState: String? = null,
    @SerialName("machine_translated") val machineTranslated: Boolean? = null,
    @SerialName("translated_body") val translatedBody: String? = null,
    @SerialName("source_hash") val sourceHash: String? = null,
)

@Serializable
data class CommentListResponse(
    val items: List<CommentListItem> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("thread_snapshot") val threadSnapshot: ThreadSnapshot? = null,
)

@Serializable
data class CreatePostRequest(
    @SerialName("idempotency_key") val idempotencyKey: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("post_type") val postType: String = "text",
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("age_gate_policy") val ageGatePolicy: String? = null,
    @SerialName("flair_id") val flairId: String? = null,
    @SerialName("identity_mode") val identityMode: String? = null,
    @SerialName("translation_policy") val translationPolicy: String? = null,
    val visibility: String? = null,
)

@Serializable
data class OnboardingStatus(
    @SerialName("reddit_verification_status") val redditVerificationStatus: String,
    @SerialName("reddit_import_status") val redditImportStatus: String,
    @SerialName("cleanup_rename_available") val cleanupRenameAvailable: Boolean,
    @SerialName("onboarding_dismissed_at") val onboardingDismissedAt: String? = null,
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
    @SerialName("user_id") val userId: String? = null,
    val status: String,
    val provider: String,
    @SerialName("provider_mode") val providerMode: String? = null,
    @SerialName("requested_capabilities") val requestedCapabilities: List<String> = emptyList(),
    @SerialName("verification_intent") val verificationIntent: String? = null,
    @SerialName("policy_id") val policyId: String? = null,
    val launch: VerificationSessionLaunch? = null,
    @SerialName("callback_path") val callbackPath: String? = null,
    val nationality: String? = null,
    @SerialName("age_at_verification") val ageAtVerification: Int? = null,
    @SerialName("attestation_id") val attestationId: String? = null,
    @SerialName("proof_hash") val proofHash: String? = null,
    @SerialName("evidence_ref") val evidenceRef: String? = null,
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
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
