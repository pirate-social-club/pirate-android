package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant

private fun flexibleApiTimestamp(value: JsonElement?): String? {
    val primitive = value as? JsonPrimitive ?: return null
    return primitive.longOrNull?.let { Instant.ofEpochSecond(it).toString() }
        ?: primitive.contentOrNull
}

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
    val context: JsonElement? = null,
    @SerialName("type_id") val typeId: JsonElement? = null,
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
    @SerialName("user_id") private val contractUserId: String? = null,
    @SerialName("id") private val feedUserId: String? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
) {
    val userId: String get() = contractUserId ?: feedUserId.orEmpty()
    val createdAt: String get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
}

@Serializable
data class GlobalHandle(
    val id: String? = null,
    val label: String,
    val tier: String,
    val status: String,
)

@Serializable
data class LinkedHandle(
    @SerialName("linked_handle") val linkedHandle: String,
    val label: String,
    val kind: String,
    @SerialName("verification_state") val verificationState: String,
    val metadata: JsonObject? = null,
)

@Serializable
data class Profile(
    @SerialName("user_id") private val contractUserId: String? = null,
    @SerialName("id") private val feedUserId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val bio: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("cover_ref") val coverRef: String? = null,
    @SerialName("global_handle") val globalHandle: GlobalHandle? = null,
    @SerialName("primary_public_handle") val primaryPublicHandle: LinkedHandle? = null,
    @SerialName("linked_handles") val linkedHandles: List<LinkedHandle> = emptyList(),
    @SerialName("primary_wallet_address") val primaryWalletAddress: String? = null,
    @SerialName("xmtp_inbox") val xmtpInbox: String? = null,
    @SerialName("nationality_badge_country") val nationalityBadgeCountry: String? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("following_count") val followingCount: Int? = null,
    @SerialName("preferred_locale") val preferredLocale: String? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
) {
    val userId: String get() = contractUserId ?: feedUserId.orEmpty()
    val createdAt: String get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
}

@Serializable
data class PublicProfileCommunitySummary(
    @SerialName("community_id") private val contractCommunityId: String? = null,
    @SerialName("community") private val feedCommunityId: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
) {
    val communityId: String get() = contractCommunityId ?: feedCommunityId.orEmpty()
    val createdAt: String get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
}

@Serializable
data class PublicProfileResolution(
    val profile: Profile,
    @SerialName("requested_handle_label") val requestedHandleLabel: String,
    @SerialName("resolved_handle_label") val resolvedHandleLabel: String,
    @SerialName("is_canonical") val isCanonical: Boolean,
    @SerialName("created_communities") val createdCommunities: List<PublicProfileCommunitySummary> = emptyList(),
)

@Serializable
data class PostableCommunitySummary(
    @SerialName("community_id") val communityId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("route_slug") val routeSlug: String? = null,
    val action: String,
)

@Serializable
data class PostableCommunitiesResponse(
    val communities: List<PostableCommunitySummary> = emptyList(),
)

@Serializable
data class Community(
    @SerialName("community_id") private val contractCommunityId: String? = null,
    @SerialName("id") private val feedCommunityId: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    @SerialName("created_by_user") val createdByUser: String? = null,
    @SerialName("namespace_verification_id") private val contractNamespaceVerificationId: String? = null,
    @SerialName("namespace_verification") private val feedNamespaceVerificationId: String? = null,
    @SerialName("pending_namespace_verification_session_id") private val contractPendingNamespaceVerificationSessionId: String? = null,
    @SerialName("pending_namespace_verification_session") private val feedPendingNamespaceVerificationSessionId: String? = null,
    val description: String? = null,
    @SerialName("membership_mode") val membershipMode: String,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("banner_ref") val bannerRef: String? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
) {
    val communityId: String get() = contractCommunityId ?: feedCommunityId.orEmpty()
    val namespaceVerificationId: String? get() = contractNamespaceVerificationId ?: feedNamespaceVerificationId
    val pendingNamespaceVerificationSessionId: String? get() =
        contractPendingNamespaceVerificationSessionId ?: feedPendingNamespaceVerificationSessionId
    val createdAt: String get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
}

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
    val id: String,
    @SerialName("object") val contractObject: String? = null,
    val user: String? = null,
    val type: String,
    @SerialName("subject_type") val subjectType: String,
    val subject: String,
    val status: String,
    val priority: Int = 0,
    val payload: JsonObject? = null,
    @SerialName("resolved_at") val resolvedAt: Long? = null,
    @SerialName("dismissed_at") val dismissedAt: Long? = null,
    val created: Long,
)

@Serializable
data class NotificationTasksResponse(
    val items: List<UserTask> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class NotificationEvent(
    val id: String,
    @SerialName("object") val contractObject: String? = null,
    val type: String,
    @SerialName("actor_user") val actorUser: String? = null,
    @SerialName("subject_type") val subjectType: String,
    val subject: String,
    @SerialName("object_type") val objectType: String? = null,
    val payload: JsonObject? = null,
    val created: Long,
)

@Serializable
data class NotificationReceipt(
    val id: String,
    @SerialName("object") val contractObject: String? = null,
    @SerialName("recipient_user") val recipientUser: String,
    @SerialName("seen_at") val seenAt: Long? = null,
    @SerialName("read_at") val readAt: Long? = null,
    val created: Long,
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
data class NotificationSummary(
    @SerialName("open_task_count") val openTaskCount: Int = 0,
    @SerialName("unread_activity_count") val unreadActivityCount: Int = 0,
    @SerialName("has_unread") val hasUnread: Boolean = false,
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
data class CommunityRoleSummary(
    val user: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val handle: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    val role: String? = null,
)

@Serializable
data class CommunityPreview(
    @SerialName("community_id") private val contractCommunityId: String? = null,
    @SerialName("id") private val feedCommunityId: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    val description: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("banner_ref") val bannerRef: String? = null,
    @SerialName("membership_mode") val membershipMode: String,
    @SerialName("human_verification_lane") val humanVerificationLane: String,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    val owner: CommunityRoleSummary? = null,
    val moderators: List<CommunityRoleSummary> = emptyList(),
    @SerialName("reference_links") val referenceLinks: List<CommunityReferenceLink>? = null,
    @SerialName("membership_gate_summaries") val membershipGateSummaries: List<MembershipGateSummary> = emptyList(),
    val rules: List<CommunityRule> = emptyList(),
    @SerialName("viewer_membership_status") val viewerMembershipStatus: String? = null,
    @SerialName("viewer_community_role") val viewerCommunityRole: String? = null,
    @SerialName("viewer_following") val viewerFollowing: Boolean? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
) {
    val communityId: String get() = contractCommunityId ?: feedCommunityId.orEmpty()
    val createdAt: String get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
}

@Serializable
data class WalletScoreStatus(
    @SerialName("current_score") val currentScore: Int? = null,
    @SerialName("required_score") val requiredScore: Int? = null,
    @SerialName("passing_score") val passingScore: Boolean? = null,
    @SerialName("last_score_timestamp") val lastScoreTimestamp: String? = null,
)

@Serializable
data class JoinEligibility(
    @SerialName("community_id") private val contractCommunityId: String? = null,
    @SerialName("community") private val feedCommunityId: String? = null,
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
) {
    val communityId: String get() = contractCommunityId ?: feedCommunityId.orEmpty()
}

@Serializable
data class AltchaChallenge(
    val parameters: AltchaChallengeParameters,
    val signature: String,
)

@Serializable
data class AltchaChallengeParameters(
    val algorithm: String,
    val nonce: String,
    val salt: String,
    val cost: Int,
    val keyLength: Int,
    val keyPrefix: String,
    val expiresAt: Long? = null,
    val data: Map<String, String?>? = null,
)

@Serializable
data class AltchaSolution(
    val counter: Int,
    val derivedKey: String,
)

@Serializable
data class AltchaPayload(
    val challenge: AltchaChallenge,
    val solution: AltchaSolution,
)

@Serializable
data class ThreadSnapshot(
    @SerialName("thread_root_post_id") private val contractThreadRootPostId: String? = null,
    @SerialName("thread_root_post") private val feedThreadRootPostId: String? = null,
    @SerialName("snapshot_seq") val snapshotSeq: Int? = null,
    @SerialName("published_through_comment_created_at") private val contractPublishedThroughCommentCreatedAt: String? = null,
    @SerialName("published_through_comment_created") private val feedPublishedThroughCommentCreatedAt: Long? = null,
    @SerialName("comment_count") val commentCount: Int = 0,
    @SerialName("swarm_manifest_ref") val swarmManifestRef: String? = null,
    @SerialName("swarm_feed_ref") val swarmFeedRef: String? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
) {
    val threadRootPostId: String get() = contractThreadRootPostId ?: feedThreadRootPostId.orEmpty()
    val publishedThroughCommentCreatedAt: String? get() =
        contractPublishedThroughCommentCreatedAt ?: feedPublishedThroughCommentCreatedAt?.let { Instant.ofEpochSecond(it).toString() }
    val createdAt: String? get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }
}

@Serializable
data class PostMediaRef(
    @SerialName("storage_ref") val storageRef: String,
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("poster_ref") val posterRef: String? = null,
    @SerialName("poster_mime_type") val posterMimeType: String? = null,
    @SerialName("poster_width") val posterWidth: Int? = null,
    @SerialName("poster_height") val posterHeight: Int? = null,
)

@Serializable
data class PostEmbedPreview(
    @SerialName("author_name") val authorName: String? = null,
    @SerialName("author_url") val authorUrl: String? = null,
    val text: String? = null,
    @SerialName("has_media") val hasMedia: Boolean? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    val created: String? = null,
    val title: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)

@Serializable
data class PostEmbed(
    val embed: String? = null,
    @SerialName("embed_key") val embedKey: String? = null,
    val provider: String,
    @SerialName("provider_ref") val providerRef: String? = null,
    @SerialName("canonical_url") val canonicalUrl: String,
    @SerialName("original_url") val originalUrl: String? = null,
    val state: String? = null,
    val preview: PostEmbedPreview? = null,
    @SerialName("oembed_html") val oembedHtml: String? = null,
    @SerialName("oembed_cache_age") val oembedCacheAge: Long? = null,
    @SerialName("unavailable_reason") val unavailableReason: String? = null,
    @SerialName("last_checked_at") val lastCheckedAt: Long? = null,
)

@Serializable
data class Post(
    @SerialName("post_id") private val contractPostId: String? = null,
    @SerialName("id") private val feedPostId: String? = null,
    @SerialName("community_id") private val contractCommunityId: String? = null,
    @SerialName("community") private val feedCommunityId: String? = null,
    val title: String? = null,
    val body: String? = null,
    val caption: String? = null,
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("link_og_image_url") val linkOgImageUrl: String? = null,
    @SerialName("link_og_title") val linkOgTitle: String? = null,
    val embeds: List<PostEmbed> = emptyList(),
    @SerialName("media_refs") val mediaRefs: List<PostMediaRef> = emptyList(),
    @SerialName("post_type") val postType: String? = null,
    val status: String? = null,
    val visibility: String? = null,
    @SerialName("author_user_id") private val contractAuthorUserId: String? = null,
    @SerialName("author_user") private val feedAuthorUserId: String? = null,
    @SerialName("identity_mode") val identityMode: String? = null,
    @SerialName("anonymous_identity_scope") val anonymousIdentityScope: String? = null,
    @SerialName("anonymous_label") val anonymousLabel: String? = null,
    @SerialName("source_language") val sourceLanguage: String? = null,
    @SerialName("translation_policy") val translationPolicy: String? = null,
    @SerialName("access_mode") val accessMode: String? = null,
    @SerialName("asset_id") private val legacyAssetId: String? = null,
    @SerialName("asset") private val canonicalAssetId: String? = null,
    @SerialName("song_artifact_bundle") val songArtifactBundle: String? = null,
    @SerialName("anchor_live_room") val anchorLiveRoom: String? = null,
    @SerialName("anchor_live_room_status") val anchorLiveRoomStatus: String? = null,
    @SerialName("song_title") val songTitle: String? = null,
    @SerialName("song_mode") val songMode: String? = null,
    @SerialName("rights_basis") val rightsBasis: String? = null,
    @SerialName("upstream_asset_refs") val upstreamAssetRefs: List<String> = emptyList(),
    @SerialName("analysis_state") val analysisState: String? = null,
    @SerialName("content_safety_state") val contentSafetyState: String? = null,
    @SerialName("age_gate_policy") val ageGatePolicy: String? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val postId: String get() = contractPostId ?: feedPostId.orEmpty()
    val communityId: String get() = contractCommunityId ?: feedCommunityId.orEmpty()
    val authorUserId: String? get() = contractAuthorUserId ?: feedAuthorUserId
    val assetId: String? get() = canonicalAssetId ?: legacyAssetId
    val createdAt: String get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
}

@Serializable
data class SongPresentation(
    val title: String? = null,
    @SerialName("cover_art_ref") val coverArtRef: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
)

@Serializable
data class LocalizedPostResponse(
    val post: Post,
    @SerialName("song_presentation") val songPresentation: SongPresentation? = null,
    @SerialName("age_gate_viewer_state") val ageGateViewerState: String? = null,
    @SerialName("thread_snapshot") val threadSnapshot: ThreadSnapshot? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
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
    @SerialName("community_id") private val contractCommunityId: String? = null,
    @SerialName("id") private val feedCommunityId: String? = null,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("member_count") val memberCount: Int? = null,
    @SerialName("follower_count") val followerCount: Int? = null,
    @SerialName("viewer_following") val viewerFollowing: Boolean? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val communityId: String? get() = contractCommunityId ?: feedCommunityId
}

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
data class PublicCommunitySearchItem(
    @SerialName("community") val communityId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    @SerialName("membership_gate_summaries") val membershipGateSummaries: List<MembershipGateSummary> = emptyList(),
)

@Serializable
data class PublicCommunitySearchResponse(
    val query: String? = null,
    val communities: List<PublicCommunitySearchItem> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
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
    @SerialName("comment_id") private val contractCommentId: String? = null,
    @SerialName("id") private val feedCommentId: String? = null,
    @SerialName("community_id") private val contractCommunityId: String? = null,
    @SerialName("community") private val feedCommunityId: String? = null,
    @SerialName("thread_root_post_id") private val contractThreadRootPostId: String? = null,
    @SerialName("thread_root_post") private val feedThreadRootPostId: String? = null,
    @SerialName("parent_comment_id") private val contractParentCommentId: String? = null,
    @SerialName("parent_comment") private val feedParentCommentId: String? = null,
    @SerialName("author_user_id") private val contractAuthorUserId: String? = null,
    @SerialName("author_user") private val feedAuthorUserId: String? = null,
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
    @SerialName("last_reply_at") private val contractLastReplyAt: JsonElement? = null,
    @SerialName("last_reply") private val feedLastReplyAt: Long? = null,
    @SerialName("created_at") private val contractCreatedAt: String? = null,
    @SerialName("created") private val feedCreatedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val commentId: String get() = contractCommentId ?: feedCommentId.orEmpty()
    val communityId: String get() = contractCommunityId ?: feedCommunityId.orEmpty()
    val threadRootPostId: String get() = contractThreadRootPostId ?: feedThreadRootPostId.orEmpty()
    val parentCommentId: String? get() = contractParentCommentId ?: feedParentCommentId
    val authorUserId: String? get() = contractAuthorUserId ?: feedAuthorUserId
    val lastReplyAt: String? get() = flexibleApiTimestamp(contractLastReplyAt)
        ?: feedLastReplyAt?.let { Instant.ofEpochSecond(it).toString() }
    val createdAt: String get() = contractCreatedAt ?: feedCreatedAt?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
}

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
    val caption: String? = null,
    @SerialName("post_type") val postType: String,
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("age_gate_policy") val ageGatePolicy: String? = null,
    @SerialName("flair_id") val flairId: String? = null,
    @SerialName("identity_mode") val identityMode: String? = null,
    @SerialName("translation_policy") val translationPolicy: String? = null,
    val visibility: String? = null,
    @SerialName("song_artifact_bundle") val songArtifactBundle: String? = null,
    @SerialName("song_mode") val songMode: String? = null,
    @SerialName("rights_basis") val rightsBasis: String? = null,
    @SerialName("access_mode") val accessMode: String? = null,
    @SerialName("license_preset") val licensePreset: String? = null,
    @SerialName("commercial_rev_share_pct") val commercialRevSharePct: Int? = null,
    @SerialName("upstream_asset_refs") val upstreamAssetRefs: List<String>? = null,
)

@Serializable
data class CreateSongArtifactUploadRequest(
    @SerialName("artifact_kind") val artifactKind: String,
    @SerialName("mime_type") val mimeType: String,
    val filename: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("content_hash") val contentHash: String? = null,
)

@Serializable
data class SongArtifactUpload(
    val id: String,
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    @SerialName("uploader_user") val uploaderUser: String = "",
    @SerialName("artifact_kind") val artifactKind: String = "",
    val status: String = "pending_upload",
    @SerialName("storage_ref") val storageRef: String = "",
    @SerialName("mime_type") val mimeType: String = "",
    val filename: String? = null,
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    @SerialName("content_hash") val contentHash: String? = null,
    @SerialName("storage_provider") val storageProvider: String? = null,
    @SerialName("storage_bucket") val storageBucket: String? = null,
    @SerialName("storage_object_key") val storageObjectKey: String? = null,
    @SerialName("storage_endpoint") val storageEndpoint: String? = null,
    @SerialName("gateway_url") val gatewayUrl: String? = null,
    @SerialName("upload_url") val uploadUrl: String = "",
    val created: Long = 0,
)

@Serializable
data class SongArtifactUploadRef(
    @SerialName("song_artifact_upload") val songArtifactUpload: String,
)

@Serializable
data class SongPreviewWindow(
    @SerialName("start_ms") val startMs: Long,
    @SerialName("duration_ms") val durationMs: Long,
)

@Serializable
data class CreateSongArtifactBundleRequest(
    @SerialName("primary_audio") val primaryAudio: SongArtifactUploadRef,
    val title: String,
    val lyrics: String = "",
    @SerialName("genius_annotations_url") val geniusAnnotationsUrl: String? = null,
    @SerialName("cover_art") val coverArt: SongArtifactUploadRef? = null,
    @SerialName("preview_audio") val previewAudio: SongArtifactUploadRef? = null,
    @SerialName("preview_window") val previewWindow: SongPreviewWindow? = null,
    @SerialName("canvas_video") val canvasVideo: SongArtifactUploadRef? = null,
    @SerialName("instrumental_audio") val instrumentalAudio: SongArtifactUploadRef? = null,
    @SerialName("vocal_audio") val vocalAudio: SongArtifactUploadRef? = null,
)

@Serializable
data class SongArtifactBundle(
    val id: String,
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    @SerialName("creator_user") val creatorUser: String = "",
    val status: String = "draft",
    val title: String = "",
    @SerialName("primary_audio") val primaryAudio: JsonObject? = null,
    val lyrics: String = "",
    @SerialName("genius_annotations_url") val geniusAnnotationsUrl: String? = null,
    @SerialName("cover_art") val coverArt: JsonObject? = null,
    @SerialName("preview_audio") val previewAudio: JsonObject? = null,
    @SerialName("preview_window") val previewWindow: SongPreviewWindow? = null,
    @SerialName("preview_status") val previewStatus: String = "pending",
    @SerialName("preview_error") val previewError: String? = null,
    @SerialName("canvas_video") val canvasVideo: JsonObject? = null,
    @SerialName("instrumental_audio") val instrumentalAudio: JsonObject? = null,
    @SerialName("vocal_audio") val vocalAudio: JsonObject? = null,
    @SerialName("moderation_result") val moderationResult: JsonObject? = null,
    val created: Long = 0,
)

@Serializable
data class DerivativeSource(
    val id: String,
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    val asset: String = "",
    val title: String = "",
    val kind: String = "",
    @SerialName("story_ip") val storyIp: String = "",
    @SerialName("story_license_terms") val storyLicenseTerms: String = "",
    @SerialName("license_preset") val licensePreset: String? = null,
    @SerialName("commercial_rev_share_pct") val commercialRevSharePct: Int? = null,
    @SerialName("creator_user") val creatorUser: String = "",
    @SerialName("creator_handle") val creatorHandle: String? = null,
    @SerialName("creator_display_name") val creatorDisplayName: String? = null,
)

@Serializable
data class DerivativeSourceListResponse(
    val items: List<DerivativeSource> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class CommunityMoneyChainRef(
    @SerialName("chain_namespace") val chainNamespace: String? = null,
    @SerialName("chain_id") val chainId: Int? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class CommunityMoneyAssetRef(
    @SerialName("asset_symbol") val assetSymbol: String? = null,
    @SerialName("chain_namespace") val chainNamespace: String? = null,
    @SerialName("chain_id") val chainId: Int? = null,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
data class Asset(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    @SerialName("storage_ref") val storageRef: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("access_mode") val accessMode: String? = null,
)

@Serializable
data class AssetAccessResponse(
    @SerialName("access_granted") val accessGranted: Boolean = false,
    @SerialName("decision_reason") val decisionReason: String? = null,
    @SerialName("delivery_kind") val deliveryKind: String? = null,
    @SerialName("delivery_ref") val deliveryRef: String? = null,
)

@Serializable
data class CreateCommunityListingRequest(
    val asset: String? = null,
    @SerialName("live_room") val liveRoom: String? = null,
    @SerialName("price_cents") val priceCents: Int,
    @SerialName("regional_pricing_enabled") val regionalPricingEnabled: Boolean? = null,
    @SerialName("donation_partner") val donationPartner: String? = null,
    @SerialName("donation_share_bps") val donationShareBps: Int? = null,
    val status: String = "active",
)

@Serializable
data class UpdateCommunityListingRequest(
    @SerialName("price_cents") val priceCents: Int? = null,
    @SerialName("regional_pricing_enabled") val regionalPricingEnabled: Boolean? = null,
    @SerialName("donation_partner") val donationPartner: String? = null,
    @SerialName("donation_share_bps") val donationShareBps: Int? = null,
    val status: String? = null,
)

@Serializable
data class CommunityListing(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    val asset: String? = null,
    @SerialName("live_room") val liveRoom: String? = null,
    @SerialName("listing_mode") val listingMode: String? = null,
    val status: String? = null,
    @SerialName("price_cents") val priceCents: Int = 0,
    @SerialName("regional_pricing_enabled") val regionalPricingEnabled: Boolean? = null,
    @SerialName("donation_partner") val donationPartner: String? = null,
    @SerialName("donation_share_bps") val donationShareBps: Int? = null,
    val created: Long? = null,
)

@Serializable
data class CommunityListingListResponse(
    val items: List<CommunityListing> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class CommunitySaleAllocationLeg(
    val user: String? = null,
    @SerialName("share_bps") val shareBps: Int? = null,
    @SerialName("amount_cents") val amountCents: Int? = null,
)

@Serializable
data class CommunityPurchase(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    val listing: String = "",
    val asset: String? = null,
    @SerialName("live_room") val liveRoom: String? = null,
    @SerialName("buyer_user") val buyerUser: String? = null,
    @SerialName("purchase_price_cents") val purchasePriceCents: Int = 0,
    @SerialName("pricing_tier") val pricingTier: String? = null,
    @SerialName("purchase_entitlement") val purchaseEntitlement: String? = null,
    @SerialName("entitlement_kind") val entitlementKind: String? = null,
    @SerialName("entitlement_target_ref") val entitlementTargetRef: String? = null,
    val created: Long? = null,
)

@Serializable
data class CommunityPurchaseListResponse(
    val items: List<CommunityPurchase> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class CommunityPurchaseQuotePreflightRequest(
    val listing: String? = null,
    @SerialName("funding_asset") val fundingAsset: CommunityMoneyAssetRef? = null,
    @SerialName("source_chain") val sourceChain: CommunityMoneyChainRef? = null,
    @SerialName("route_provider") val routeProvider: String? = null,
    @SerialName("client_estimated_slippage_bps") val clientEstimatedSlippageBps: Int = 0,
    @SerialName("client_estimated_hop_count") val clientEstimatedHopCount: Int = 1,
    @SerialName("client_route_valid_for_seconds") val clientRouteValidForSeconds: Int? = null,
)

@Serializable
data class CommunityPurchaseQuotePreflight(
    val community: String = "",
    val eligible: Boolean = false,
    @SerialName("base_price_cents") val basePriceCents: Int? = null,
    @SerialName("viewer_price_cents") val viewerPriceCents: Int? = null,
    @SerialName("best_verified_price_cents") val bestVerifiedPriceCents: Int? = null,
    @SerialName("max_self_discount_bps") val maxSelfDiscountBps: Int? = null,
    @SerialName("quoted_at") val quotedAt: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
)

@Serializable
data class CommunityPurchaseQuoteRequest(
    val listing: String,
    @SerialName("funding_asset") val fundingAsset: CommunityMoneyAssetRef? = null,
    @SerialName("source_chain") val sourceChain: CommunityMoneyChainRef? = null,
    @SerialName("route_provider") val routeProvider: String? = null,
    @SerialName("client_estimated_slippage_bps") val clientEstimatedSlippageBps: Int = 0,
    @SerialName("client_estimated_hop_count") val clientEstimatedHopCount: Int = 1,
    @SerialName("client_route_valid_for_seconds") val clientRouteValidForSeconds: Int? = null,
)

@Serializable
data class CommunityPurchaseQuote(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    val listing: String = "",
    @SerialName("buyer_user") val buyerUser: String? = null,
    val asset: String? = null,
    @SerialName("live_room") val liveRoom: String? = null,
    @SerialName("base_price_cents") val basePriceCents: Int = 0,
    @SerialName("final_price_cents") val finalPriceCents: Int = 0,
    @SerialName("funding_asset") val fundingAsset: CommunityMoneyAssetRef? = null,
    @SerialName("source_chain") val sourceChain: CommunityMoneyChainRef? = null,
    @SerialName("route_provider") val routeProvider: String? = null,
    @SerialName("funding_destination_address") val fundingDestinationAddress: String? = null,
    @SerialName("quoted_at") val quotedAt: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
)

@Serializable
data class CommunityPurchaseSettlementRequest(
    val quote: String,
    @SerialName("settlement_wallet_attachment") val settlementWalletAttachment: String,
    @SerialName("funding_tx_ref") val fundingTxRef: String,
    @SerialName("settlement_tx_ref") val settlementTxRef: String,
)

@Serializable
data class CommunityPurchaseSettlement(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val quote: String = "",
    val community: String = "",
    val listing: String = "",
    val asset: String? = null,
    @SerialName("live_room") val liveRoom: String? = null,
    @SerialName("purchase_price_cents") val purchasePriceCents: Int = 0,
    @SerialName("purchase_entitlement") val purchaseEntitlement: String? = null,
    @SerialName("entitlement_kind") val entitlementKind: String? = null,
    @SerialName("entitlement_target_ref") val entitlementTargetRef: String? = null,
    @SerialName("settled_at") val settledAt: Long? = null,
)

@Serializable
data class CommunityPurchaseSettlementFailureRequest(
    val quote: String,
)

@Serializable
data class CommunityPurchaseSettlementFailure(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val quote: String = "",
    val community: String = "",
    val status: String? = null,
    @SerialName("failed_at") val failedAt: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
)

@Serializable
data class LiveRoomPerformerAllocationInput(
    val user: String? = null,
    val role: String? = null,
    @SerialName("share_bps") val shareBps: Int? = null,
)

@Serializable
data class LiveRoomSetlistItemInput(
    @SerialName("song_artifact_bundle") val songArtifactBundle: String? = null,
    @SerialName("source_asset_ref") val sourceAssetRef: String? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerialName("rights_basis") val rightsBasis: String? = null,
    @SerialName("license_ref") val licenseRef: String? = null,
    @SerialName("rights_status") val rightsStatus: String? = null,
    @SerialName("blocking_rights_failure") val blockingRightsFailure: Boolean? = null,
)

@Serializable
data class LiveRoomSetlistInput(
    val status: String? = null,
    val items: List<LiveRoomSetlistItemInput> = emptyList(),
)

@Serializable
data class CreateLiveRoomRequest(
    val title: String? = null,
    val description: String? = null,
    @SerialName("room_kind") val roomKind: String? = null,
    @SerialName("access_mode") val accessMode: String? = null,
    val visibility: String? = null,
    @SerialName("guest_user") val guestUser: String? = null,
    @SerialName("event_start_at") val eventStartAt: Long? = null,
    @SerialName("cover_ref") val coverRef: String? = null,
    @SerialName("store_url") val storeUrl: String? = null,
    @SerialName("store_label") val storeLabel: String? = null,
    @SerialName("performer_allocations") val performerAllocations: List<LiveRoomPerformerAllocationInput> = emptyList(),
    val setlist: LiveRoomSetlistInput? = null,
)

@Serializable
data class PublishLiveRoomRequest(
    val room: CreateLiveRoomRequest,
    val listing: CreateCommunityListingRequest,
)

@Serializable
data class LiveRoomPerformerAllocation(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val user: String = "",
    val role: String = "",
    @SerialName("share_bps") val shareBps: Int = 0,
)

@Serializable
data class LiveRoomSetlistItem(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val position: Int = 0,
    @SerialName("song_artifact_bundle") val songArtifactBundle: String? = null,
    @SerialName("source_asset_ref") val sourceAssetRef: String? = null,
    val title: String = "",
    val artist: String? = null,
    @SerialName("rights_basis") val rightsBasis: String? = null,
    @SerialName("license_ref") val licenseRef: String? = null,
    @SerialName("rights_status") val rightsStatus: String? = null,
    @SerialName("blocking_rights_failure") val blockingRightsFailure: Boolean = false,
)

@Serializable
data class LiveRoomSetlist(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val status: String? = null,
    val items: List<LiveRoomSetlistItem> = emptyList(),
)

@Serializable
data class LiveRoom(
    val id: String = "",
    @SerialName("object") val contractObject: String? = null,
    val community: String = "",
    @SerialName("anchor_post") val anchorPost: String = "",
    @SerialName("host_user") val hostUser: String = "",
    @SerialName("guest_user") val guestUser: String? = null,
    @SerialName("room_kind") val roomKind: String? = null,
    val status: String = "scheduled",
    @SerialName("access_mode") val accessMode: String = "free",
    val visibility: String? = null,
    val title: String = "",
    val description: String? = null,
    @SerialName("cover_ref") val coverRef: String? = null,
    @SerialName("store_url") val storeUrl: String? = null,
    @SerialName("store_label") val storeLabel: String? = null,
    @SerialName("event_start_at") val eventStartAt: Long? = null,
    @SerialName("live_started_at") val liveStartedAt: Long? = null,
    @SerialName("ended_at") val endedAt: Long? = null,
    @SerialName("canceled_at") val canceledAt: Long? = null,
    @SerialName("broadcast_ref") val broadcastRef: String? = null,
    @SerialName("replay_status") val replayStatus: String? = null,
    @SerialName("performer_allocations") val performerAllocations: List<LiveRoomPerformerAllocation> = emptyList(),
    val setlist: LiveRoomSetlist? = null,
    val created: Long? = null,
)

@Serializable
data class PublishLiveRoomResponse(
    val room: LiveRoom,
    val listing: CommunityListing,
)

@Serializable
data class LiveRoomAccess(
    val allowed: Boolean = false,
    @SerialName("decision_reason") val decisionReason: String? = null,
    @SerialName("access_mode") val accessMode: String = "free",
    val visibility: String? = null,
    val listing: String? = null,
    @SerialName("purchase_entitlement") val purchaseEntitlement: String? = null,
    @SerialName("guest_invite_status") val guestInviteStatus: String? = null,
)

@Serializable
data class LiveRoomAccessResponse(
    val room: LiveRoom,
    val access: LiveRoomAccess,
)

@Serializable
data class LiveRoomViewerRenewRequest(
    val uid: Long,
)

@Serializable
data class LiveRoomAttachRequest(
    @SerialName("client_kind") val clientKind: String = "android_native",
    val refresh: Boolean = false,
)

@Serializable
data class LiveRoomRuntimeBlock(
    val status: String? = null,
    val seat: String? = null,
    @SerialName("room_runtime_id") val roomRuntimeId: String? = null,
)

@Serializable
data class LiveRoomBridgeBlock(
    val ticket: String? = null,
    @SerialName("ticket_expires_at") val ticketExpiresAt: Long? = null,
)

@Serializable
data class LiveRoomAgoraBlock(
    @SerialName("app_id") val appId: String? = null,
    val channel: String? = null,
    val uid: Long? = null,
    val token: String? = null,
    @SerialName("token_expires_at") val tokenExpiresAt: Long? = null,
    val configured: Boolean = false,
)

@Serializable
data class LiveRoomJacktripBlock(
    val required: Boolean = false,
    val configured: Boolean = false,
    val server: String? = null,
    val port: Int? = null,
    @SerialName("bind_port") val bindPort: Int? = null,
    val quality: String? = null,
    @SerialName("buffer_strategy") val bufferStrategy: String? = null,
    @SerialName("linux_audio_setup_recommended") val linuxAudioSetupRecommended: Boolean = false,
)

@Serializable
data class LiveRoomHostAttachResponse(
    val room: LiveRoom,
    val runtime: LiveRoomRuntimeBlock? = null,
    val bridge: LiveRoomBridgeBlock? = null,
    val agora: LiveRoomAgoraBlock? = null,
    val jacktrip: LiveRoomJacktripBlock? = null,
)

@Serializable
data class LiveRoomGuestAttachResponse(
    val room: LiveRoom,
    val runtime: LiveRoomRuntimeBlock? = null,
    val bridge: LiveRoomBridgeBlock? = null,
    val agora: LiveRoomAgoraBlock? = null,
    val jacktrip: LiveRoomJacktripBlock? = null,
)

@Serializable
data class LiveRoomViewerAttachResponse(
    val room: LiveRoom,
    val access: LiveRoomAccess,
    val runtime: LiveRoomRuntimeBlock? = null,
    val agora: LiveRoomAgoraBlock? = null,
)

@Serializable
data class OnboardingStatus(
    @SerialName("reddit_verification_status") val redditVerificationStatus: String = "not_started",
    @SerialName("reddit_import_status") val redditImportStatus: String = "not_started",
    @SerialName("cleanup_rename_available") val cleanupRenameAvailable: Boolean = false,
    @SerialName("onboarding_dismissed_at") val onboardingDismissedAt: JsonElement? = null,
    val dismissed: JsonElement? = null,
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
    @SerialName("imported_reddit_score") val importedRedditScore: Int? = null,
    @SerialName("top_subreddits") val topSubreddits: List<SubredditKarma> = emptyList(),
    @SerialName("moderator_of") val moderatorOf: List<String> = emptyList(),
    @SerialName("inferred_interests") val inferredInterests: List<String> = emptyList(),
    @SerialName("suggested_communities") val suggestedCommunities: List<SuggestedCommunity> = emptyList(),
)

@Serializable
data class VerificationSession(
    @SerialName("id") val verificationSessionId: String,
    val user: String? = null,
    val status: String,
    val provider: String,
    @SerialName("provider_mode") val providerMode: String? = null,
    @SerialName("requested_capabilities") val requestedCapabilities: List<String> = emptyList(),
    @SerialName("verification_requirements") val verificationRequirements: List<JsonElement> = emptyList(),
    @SerialName("verification_intent") val verificationIntent: String? = null,
    val policy: String? = null,
    @SerialName("wallet_attachment") val walletAttachment: String? = null,
    val launch: VerificationSessionLaunch? = null,
    @SerialName("callback_path") val callbackPath: String? = null,
    val nationality: String? = null,
    @SerialName("age_at_verification") val ageAtVerification: Int? = null,
    val attestation: String? = null,
    @SerialName("proof_hash") val proofHash: String? = null,
    @SerialName("evidence_ref") val evidenceRef: String? = null,
    @SerialName("verified_at") val verifiedAt: Long? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    val created: Long,
    @SerialName("expires_at") val expiresAt: Long,
)

@Serializable
data class NamespaceVerificationSession(
    @SerialName("namespace_verification_session_id") val namespaceVerificationSessionId: String,
    @SerialName("namespace_verification_id") val namespaceVerificationId: String? = null,
    val family: String? = null,
    @SerialName("submitted_root_label") val submittedRootLabel: String? = null,
    @SerialName("normalized_root_label") val normalizedRootLabel: String? = null,
    val status: String,
    @SerialName("challenge_kind") val challengeKind: String? = null,
    @SerialName("challenge_host") val challengeHost: String? = null,
    @SerialName("challenge_txt_value") val challengeTxtValue: String? = null,
    @SerialName("challenge_payload") val challengePayload: JsonObject? = null,
    @SerialName("challenge_expires_at") val challengeExpiresAt: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
)

@Serializable
data class WalletAttachmentSummary(
    @SerialName("wallet_attachment_id") val walletAttachmentId: String? = null,
    @SerialName("chain_namespace") val chainNamespace: String? = null,
    @SerialName("wallet_address") val walletAddress: String,
    @SerialName("is_primary") val isPrimary: Boolean = false,
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
    @SerialName("community") private val contractCommunityId: String? = null,
    @SerialName("community_id") private val legacyCommunityId: String? = null,
    val status: String,
) {
    val communityId: String get() = contractCommunityId ?: legacyCommunityId.orEmpty()
}

@Serializable
data class CommunityFollowResponse(
    @SerialName("community") private val contractCommunityId: String? = null,
    @SerialName("community_id") private val legacyCommunityId: String? = null,
    val following: Boolean,
    @SerialName("follower_count") val followerCount: Int? = null,
) {
    val communityId: String get() = contractCommunityId ?: legacyCommunityId.orEmpty()
}

@Serializable
data class ErrorResponse(
    val code: String? = null,
    val message: String? = null,
    val retryable: Boolean? = null,
    val details: GateFailureDetails? = null,
)

@Serializable
data class GateFailureDetails(
    @SerialName("missing_capabilities") val missingCapabilities: List<String> = emptyList(),
)
