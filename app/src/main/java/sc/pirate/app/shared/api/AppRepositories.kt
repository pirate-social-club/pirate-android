package sc.pirate.app.shared.api

import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.CompleteVerificationSessionRequest
import sc.pirate.app.api.CreateCommentRequest
import sc.pirate.app.api.ProfileUpdateInput
import sc.pirate.app.api.RenameHandleResponse
import sc.pirate.app.api.SessionExchangeProof
import sc.pirate.app.api.StartVerificationSessionRequest
import sc.pirate.app.api.StreamUpload
import sc.pirate.app.api.model.Community
import sc.pirate.app.api.model.CommunityCreateAcceptedResponse
import sc.pirate.app.api.model.CommunityFollowResponse
import sc.pirate.app.api.model.CommunityJoinResponse
import sc.pirate.app.api.model.CommunityListing
import sc.pirate.app.api.model.CommunityListingListResponse
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CommunityPurchaseListResponse
import sc.pirate.app.api.model.CommunityPurchaseQuote
import sc.pirate.app.api.model.CommunityPurchaseQuoteRequest
import sc.pirate.app.api.model.CommunityPurchaseSettlement
import sc.pirate.app.api.model.CommunityPurchaseSettlementFailure
import sc.pirate.app.api.model.CommunityPurchaseSettlementFailureRequest
import sc.pirate.app.api.model.CommunityPurchaseSettlementRequest
import sc.pirate.app.api.model.CommentListResponse
import sc.pirate.app.api.model.CommentVoteResponse
import sc.pirate.app.api.model.CreateCommunityListingRequest
import sc.pirate.app.api.model.CreateCommunityRequest
import sc.pirate.app.api.model.CreateLiveRoomRequest
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.api.model.CreateUserReportRequest
import sc.pirate.app.api.model.CreateSongArtifactBundleRequest
import sc.pirate.app.api.model.CreateSongArtifactUploadRequest
import sc.pirate.app.api.model.DerivativeSourceListResponse
import sc.pirate.app.api.model.HomeFeedResponse
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.LiveRoom
import sc.pirate.app.api.model.LiveRoomAccessResponse
import sc.pirate.app.api.model.LiveRoomAttachRequest
import sc.pirate.app.api.model.LiveRoomGuestAttachResponse
import sc.pirate.app.api.model.LiveRoomHostAttachResponse
import sc.pirate.app.api.model.LiveRoomReplayDraft
import sc.pirate.app.api.model.LiveRoomViewerAttachResponse
import sc.pirate.app.api.model.LiveRoomViewerRenewRequest
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.MembershipRequestListResponse
import sc.pirate.app.api.model.MembershipRequestSummary
import sc.pirate.app.api.model.NotificationFeedResponse
import sc.pirate.app.api.model.NotificationSummary
import sc.pirate.app.api.model.NotificationTasksResponse
import sc.pirate.app.api.model.OnboardingStatus
import sc.pirate.app.api.model.PostListResponse
import sc.pirate.app.api.model.PostVoteResponse
import sc.pirate.app.api.model.PostableCommunitiesResponse
import sc.pirate.app.api.model.Profile
import sc.pirate.app.api.model.PublishLiveRoomRequest
import sc.pirate.app.api.model.PublishLiveRoomResponse
import sc.pirate.app.api.model.PublishLiveRoomReplayDraftRequest
import sc.pirate.app.api.model.UpdateLiveRoomReplayDraftRequest
import sc.pirate.app.api.model.UpdateCommunityRulesRequest
import sc.pirate.app.api.model.PublicProfileResolution
import sc.pirate.app.api.model.PublicCommunitySearchResponse
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.api.model.SongArtifactBundle
import sc.pirate.app.api.model.SongArtifactUpload
import sc.pirate.app.api.model.UserTask
import sc.pirate.app.api.model.VerificationSession

interface AuthRepository {
    suspend fun exchangeSession(proof: SessionExchangeProof): SessionExchangeResponse
}

interface OnboardingRepository {
    suspend fun getStatus(): OnboardingStatus
    suspend fun dismiss(): OnboardingStatus
}

interface FeedRepository {
    suspend fun home(
        cursor: String? = null,
        locale: String? = null,
        sort: String? = null,
        timeRange: String? = null,
    ): HomeFeedResponse
}

interface CommunityRepository {
    suspend fun createCommunity(request: CreateCommunityRequest): CommunityCreateAcceptedResponse
    suspend fun getCommunity(communityId: String): Community
    suspend fun attachNamespace(communityId: String, namespaceVerificationId: String): Community
    suspend fun setPendingNamespaceSession(communityId: String, sessionId: String?): Community
    suspend fun getPreview(communityId: String, locale: String? = null): CommunityPreview
    suspend fun getPublicPreview(communityId: String, locale: String? = null): CommunityPreview
    suspend fun searchPublicCommunities(query: String, limit: Int? = null): PublicCommunitySearchResponse
    suspend fun getJoinEligibility(communityId: String): JoinEligibility
    suspend fun joinCommunity(communityId: String, altchaHeader: String? = null): CommunityJoinResponse
    suspend fun followCommunity(communityId: String): CommunityFollowResponse
    suspend fun unfollowCommunity(communityId: String): CommunityFollowResponse
    suspend fun listMembershipRequests(
        communityId: String,
        cursor: String? = null,
        limit: Int = 50,
    ): MembershipRequestListResponse
    suspend fun reviewMembershipRequest(
        communityId: String,
        requestId: String,
        approve: Boolean,
    ): MembershipRequestSummary
    suspend fun updateRules(communityId: String, request: UpdateCommunityRulesRequest): Community
    suspend fun listPosts(
        communityId: String,
        limit: Int? = null,
        cursor: String? = null,
        locale: String? = null,
        sort: String? = null,
        flairId: String? = null,
    ): PostListResponse
    suspend fun listPublicPosts(
        communityId: String,
        limit: Int? = null,
        cursor: String? = null,
        locale: String? = null,
        sort: String? = null,
        flairId: String? = null,
    ): PostListResponse
    suspend fun createPost(
        communityId: String,
        request: CreatePostRequest,
        altchaHeader: String? = null,
    ): LocalizedPostResponse
    suspend fun uploadMedia(kind: String, bytes: ByteArray, filename: String, mimeType: String): String
    suspend fun uploadMedia(kind: String, upload: StreamUpload, filename: String): String
    suspend fun createArtifactUpload(
        communityId: String,
        request: CreateSongArtifactUploadRequest,
    ): SongArtifactUpload
    suspend fun uploadArtifactContent(communityId: String, uploadId: String, bytes: ByteArray): SongArtifactUpload
    suspend fun uploadArtifactContent(communityId: String, uploadId: String, upload: StreamUpload): SongArtifactUpload
    suspend fun createSongArtifactBundle(
        communityId: String,
        request: CreateSongArtifactBundleRequest,
    ): SongArtifactBundle
    suspend fun getSongArtifactBundle(communityId: String, bundleId: String): SongArtifactBundle
    suspend fun listDerivativeSources(
        communityId: String,
        kind: String? = null,
        scope: String? = null,
        query: String? = null,
        limit: Int? = null,
    ): DerivativeSourceListResponse
    suspend fun createLiveRoom(
        communityId: String,
        request: CreateLiveRoomRequest,
        altchaHeader: String? = null,
    ): LiveRoom
    suspend fun publishLiveRoom(
        communityId: String,
        request: PublishLiveRoomRequest,
        altchaHeader: String? = null,
    ): PublishLiveRoomResponse
    suspend fun hostAttachLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomAttachRequest = LiveRoomAttachRequest(),
    ): LiveRoomHostAttachResponse
    suspend fun guestAcceptLiveRoom(communityId: String, liveRoomId: String): LiveRoom
    suspend fun guestAttachLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomAttachRequest = LiveRoomAttachRequest(),
    ): LiveRoomGuestAttachResponse
    suspend fun guestRevokeLiveRoom(communityId: String, liveRoomId: String): LiveRoom
    suspend fun cancelLiveRoom(communityId: String, liveRoomId: String): LiveRoom
    suspend fun endLiveRoom(communityId: String, liveRoomId: String): LiveRoom
    suspend fun getLiveRoomReplayDraft(communityId: String, liveRoomId: String): LiveRoomReplayDraft
    suspend fun updateLiveRoomReplayDraft(
        communityId: String,
        liveRoomId: String,
        request: UpdateLiveRoomReplayDraftRequest,
    ): LiveRoomReplayDraft
    suspend fun publishLiveRoomReplayDraft(
        communityId: String,
        liveRoomId: String,
        request: PublishLiveRoomReplayDraftRequest,
    ): LiveRoomReplayDraft
    suspend fun getLiveRoomAccess(communityId: String, liveRoomId: String): LiveRoomAccessResponse
    suspend fun getPublicLiveRoomAccess(communityId: String, liveRoomId: String): LiveRoomAccessResponse
    suspend fun viewerAttachLiveRoom(communityId: String, liveRoomId: String): LiveRoomViewerAttachResponse
    suspend fun publicViewerAttachLiveRoom(communityId: String, liveRoomId: String): LiveRoomViewerAttachResponse
    suspend fun viewerRenewLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomViewerRenewRequest,
    ): LiveRoomViewerAttachResponse
    suspend fun publicViewerRenewLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomViewerRenewRequest,
    ): LiveRoomViewerAttachResponse
    suspend fun listListings(communityId: String): CommunityListingListResponse
    suspend fun createListing(communityId: String, request: CreateCommunityListingRequest): CommunityListing
    suspend fun listPurchases(communityId: String): CommunityPurchaseListResponse
    suspend fun createPurchaseQuote(
        communityId: String,
        request: CommunityPurchaseQuoteRequest,
    ): CommunityPurchaseQuote
    suspend fun settlePurchase(
        communityId: String,
        request: CommunityPurchaseSettlementRequest,
    ): CommunityPurchaseSettlement
    suspend fun failPurchase(
        communityId: String,
        request: CommunityPurchaseSettlementFailureRequest,
    ): CommunityPurchaseSettlementFailure
}

interface PostRepository {
    suspend fun getPost(postId: String): LocalizedPostResponse
    suspend fun getPublicPost(postId: String): LocalizedPostResponse
    suspend fun votePost(postId: String, value: Int): PostVoteResponse
    suspend fun listComments(
        communityId: String,
        postId: String,
        limit: Int? = null,
        cursor: String? = null,
        locale: String? = null,
        sort: String? = null,
    ): CommentListResponse
    suspend fun listPublicComments(
        postId: String,
        limit: Int? = null,
        cursor: String? = null,
        locale: String? = null,
        sort: String? = null,
    ): CommentListResponse
    suspend fun createComment(
        communityId: String,
        postId: String,
        request: CreateCommentRequest,
        altchaHeader: String? = null,
    )
    suspend fun listReplies(
        commentId: String,
        limit: Int? = null,
        cursor: String? = null,
        locale: String? = null,
        sort: String? = null,
    ): CommentListResponse
    suspend fun listPublicReplies(
        commentId: String,
        limit: Int? = null,
        cursor: String? = null,
        locale: String? = null,
        sort: String? = null,
    ): CommentListResponse
    suspend fun createReply(
        commentId: String,
        request: CreateCommentRequest,
        altchaHeader: String? = null,
    )
    suspend fun voteComment(commentId: String, value: Int): CommentVoteResponse
    suspend fun reportPost(communityId: String, postId: String, request: CreateUserReportRequest)
    suspend fun reportComment(communityId: String, commentId: String, request: CreateUserReportRequest)
}

interface ProfileRepository {
    suspend fun getMe(): Profile
    suspend fun getPostableCommunities(): PostableCommunitiesResponse
    suspend fun getByUserId(userId: String): Profile
    suspend fun getPublicByHandle(handleLabel: String): PublicProfileResolution
    suspend fun getPublicByWallet(walletAddress: String): PublicProfileResolution
    suspend fun updateMe(input: ProfileUpdateInput): Profile
    suspend fun publishXmtpInbox(inboxId: String): Profile
    suspend fun uploadMedia(kind: String, bytes: ByteArray, filename: String, mimeType: String): String
    suspend fun renameHandle(desiredLabel: String): RenameHandleResponse
}

interface VerificationRepository {
    suspend fun startSession(input: StartVerificationSessionRequest): VerificationSession
    suspend fun getSession(verificationSessionId: String): VerificationSession
    suspend fun completeSession(
        verificationSessionId: String,
        input: CompleteVerificationSessionRequest = CompleteVerificationSessionRequest(),
    ): VerificationSession
    suspend fun startNamespaceSession(family: String, rootLabel: String): sc.pirate.app.api.model.NamespaceVerificationSession
    suspend fun getNamespaceSession(sessionId: String): sc.pirate.app.api.model.NamespaceVerificationSession
    suspend fun completeNamespaceSession(
        sessionId: String,
        restartChallenge: Boolean? = null,
    ): sc.pirate.app.api.model.NamespaceVerificationSession
}

interface NotificationRepository {
    suspend fun getSummary(): NotificationSummary
    suspend fun getTasks(): NotificationTasksResponse
    suspend fun getFeed(limit: Int? = null, cursor: String? = null): NotificationFeedResponse
    suspend fun markRead(eventIds: List<String> = emptyList())
    suspend fun dismissTask(taskId: String): UserTask
}

class ApiAuthRepository(
    private val apiClient: ApiClient,
) : AuthRepository {
    override suspend fun exchangeSession(proof: SessionExchangeProof): SessionExchangeResponse {
        return apiClient.auth.sessionExchange(proof)
    }
}

class ApiOnboardingRepository(
    private val apiClient: ApiClient,
) : OnboardingRepository {
    override suspend fun getStatus(): OnboardingStatus = apiClient.onboarding.getStatus()

    override suspend fun dismiss(): OnboardingStatus = apiClient.onboarding.dismiss()
}

class ApiFeedRepository(
    private val apiClient: ApiClient,
) : FeedRepository {
    override suspend fun home(
        cursor: String?,
        locale: String?,
        sort: String?,
        timeRange: String?,
    ): HomeFeedResponse {
        return apiClient.feed.home(
            cursor = cursor,
            locale = locale,
            sort = sort,
            timeRange = timeRange,
        )
    }
}

class ApiCommunityRepository(
    private val apiClient: ApiClient,
) : CommunityRepository {
    override suspend fun createCommunity(request: CreateCommunityRequest): CommunityCreateAcceptedResponse {
        return apiClient.communities.create(request)
    }

    override suspend fun getCommunity(communityId: String): Community {
        return apiClient.communities.get(communityId)
    }

    override suspend fun attachNamespace(communityId: String, namespaceVerificationId: String): Community {
        return apiClient.communities.attachNamespace(communityId, namespaceVerificationId)
    }

    override suspend fun setPendingNamespaceSession(communityId: String, sessionId: String?): Community {
        return apiClient.communities.setPendingNamespaceSession(communityId, sessionId)
    }

    override suspend fun getPreview(communityId: String, locale: String?): CommunityPreview {
        return apiClient.communities.preview(communityId, locale)
    }

    override suspend fun getPublicPreview(communityId: String, locale: String?): CommunityPreview {
        return apiClient.publicCommunities.preview(communityId, locale)
    }

    override suspend fun searchPublicCommunities(query: String, limit: Int?): PublicCommunitySearchResponse {
        return apiClient.publicCommunities.search(query, limit)
    }

    override suspend fun getJoinEligibility(communityId: String): JoinEligibility {
        return apiClient.communities.getJoinEligibility(communityId)
    }

    override suspend fun joinCommunity(communityId: String, altchaHeader: String?): CommunityJoinResponse {
        return apiClient.communities.join(communityId, altchaHeader)
    }

    override suspend fun followCommunity(communityId: String): CommunityFollowResponse {
        return apiClient.communities.follow(communityId)
    }

    override suspend fun unfollowCommunity(communityId: String): CommunityFollowResponse {
        return apiClient.communities.unfollow(communityId)
    }

    override suspend fun listMembershipRequests(
        communityId: String,
        cursor: String?,
        limit: Int,
    ): MembershipRequestListResponse {
        return apiClient.communities.listMembershipRequests(communityId, cursor, limit)
    }

    override suspend fun reviewMembershipRequest(
        communityId: String,
        requestId: String,
        approve: Boolean,
    ): MembershipRequestSummary {
        return apiClient.communities.reviewMembershipRequest(communityId, requestId, approve)
    }

    override suspend fun updateRules(communityId: String, request: UpdateCommunityRulesRequest): Community {
        return apiClient.communities.updateRules(communityId, request)
    }

    override suspend fun listPosts(
        communityId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
        flairId: String?,
    ): PostListResponse {
        return apiClient.communities.listPosts(
            communityId = communityId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
            flairId = flairId,
        )
    }

    override suspend fun listPublicPosts(
        communityId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
        flairId: String?,
    ): PostListResponse {
        return apiClient.publicCommunities.listPosts(
            communityId = communityId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
            flairId = flairId,
        )
    }

    override suspend fun createPost(
        communityId: String,
        request: CreatePostRequest,
        altchaHeader: String?,
    ): LocalizedPostResponse {
        return apiClient.communities.createPost(communityId, request, altchaHeader)
    }

    override suspend fun uploadMedia(kind: String, bytes: ByteArray, filename: String, mimeType: String): String {
        return apiClient.communities.uploadMedia(kind, bytes, filename, mimeType).mediaRef
    }

    override suspend fun uploadMedia(kind: String, upload: StreamUpload, filename: String): String {
        return apiClient.communities.uploadMedia(kind, upload, filename).mediaRef
    }

    override suspend fun createArtifactUpload(
        communityId: String,
        request: CreateSongArtifactUploadRequest,
    ): SongArtifactUpload {
        return apiClient.communities.createArtifactUpload(communityId, request)
    }

    override suspend fun uploadArtifactContent(
        communityId: String,
        uploadId: String,
        bytes: ByteArray,
    ): SongArtifactUpload {
        return apiClient.communities.uploadArtifactContent(communityId, uploadId, bytes)
    }

    override suspend fun uploadArtifactContent(
        communityId: String,
        uploadId: String,
        upload: StreamUpload,
    ): SongArtifactUpload {
        return apiClient.communities.uploadArtifactContent(communityId, uploadId, upload)
    }

    override suspend fun createSongArtifactBundle(
        communityId: String,
        request: CreateSongArtifactBundleRequest,
    ): SongArtifactBundle {
        return apiClient.communities.createSongArtifactBundle(communityId, request)
    }

    override suspend fun getSongArtifactBundle(communityId: String, bundleId: String): SongArtifactBundle {
        return apiClient.communities.getSongArtifactBundle(communityId, bundleId)
    }

    override suspend fun listDerivativeSources(
        communityId: String,
        kind: String?,
        scope: String?,
        query: String?,
        limit: Int?,
    ): DerivativeSourceListResponse {
        return apiClient.communities.listDerivativeSources(communityId, kind, scope, query, limit)
    }

    override suspend fun createLiveRoom(
        communityId: String,
        request: CreateLiveRoomRequest,
        altchaHeader: String?,
    ): LiveRoom {
        return apiClient.communities.createLiveRoom(communityId, request, altchaHeader)
    }

    override suspend fun publishLiveRoom(
        communityId: String,
        request: PublishLiveRoomRequest,
        altchaHeader: String?,
    ): PublishLiveRoomResponse {
        return apiClient.communities.publishLiveRoom(communityId, request, altchaHeader)
    }

    override suspend fun hostAttachLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomAttachRequest,
    ): LiveRoomHostAttachResponse {
        return apiClient.communities.hostAttachLiveRoom(communityId, liveRoomId, request)
    }

    override suspend fun guestAcceptLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
        return apiClient.communities.guestAcceptLiveRoom(communityId, liveRoomId)
    }

    override suspend fun guestAttachLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomAttachRequest,
    ): LiveRoomGuestAttachResponse {
        return apiClient.communities.guestAttachLiveRoom(communityId, liveRoomId, request)
    }

    override suspend fun guestRevokeLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
        return apiClient.communities.guestRevokeLiveRoom(communityId, liveRoomId)
    }

    override suspend fun cancelLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
        return apiClient.communities.cancelLiveRoom(communityId, liveRoomId)
    }

    override suspend fun endLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
        return apiClient.communities.endLiveRoom(communityId, liveRoomId)
    }

    override suspend fun getLiveRoomReplayDraft(communityId: String, liveRoomId: String): LiveRoomReplayDraft =
        apiClient.communities.getLiveRoomReplayDraft(communityId, liveRoomId)

    override suspend fun updateLiveRoomReplayDraft(
        communityId: String,
        liveRoomId: String,
        request: UpdateLiveRoomReplayDraftRequest,
    ): LiveRoomReplayDraft = apiClient.communities.updateLiveRoomReplayDraft(communityId, liveRoomId, request)

    override suspend fun publishLiveRoomReplayDraft(
        communityId: String,
        liveRoomId: String,
        request: PublishLiveRoomReplayDraftRequest,
    ): LiveRoomReplayDraft = apiClient.communities.publishLiveRoomReplayDraft(communityId, liveRoomId, request)

    override suspend fun getLiveRoomAccess(communityId: String, liveRoomId: String): LiveRoomAccessResponse {
        return apiClient.communities.getLiveRoomAccess(communityId, liveRoomId)
    }

    override suspend fun getPublicLiveRoomAccess(communityId: String, liveRoomId: String): LiveRoomAccessResponse {
        return apiClient.publicCommunities.getLiveRoomAccess(communityId, liveRoomId)
    }

    override suspend fun viewerAttachLiveRoom(
        communityId: String,
        liveRoomId: String,
    ): LiveRoomViewerAttachResponse {
        return apiClient.communities.viewerAttachLiveRoom(communityId, liveRoomId)
    }

    override suspend fun publicViewerAttachLiveRoom(
        communityId: String,
        liveRoomId: String,
    ): LiveRoomViewerAttachResponse {
        return apiClient.publicCommunities.viewerAttachLiveRoom(communityId, liveRoomId)
    }

    override suspend fun viewerRenewLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomViewerRenewRequest,
    ): LiveRoomViewerAttachResponse {
        return apiClient.communities.viewerRenewLiveRoom(communityId, liveRoomId, request)
    }

    override suspend fun publicViewerRenewLiveRoom(
        communityId: String,
        liveRoomId: String,
        request: LiveRoomViewerRenewRequest,
    ): LiveRoomViewerAttachResponse {
        return apiClient.publicCommunities.viewerRenewLiveRoom(communityId, liveRoomId, request)
    }

    override suspend fun listListings(communityId: String): CommunityListingListResponse {
        return apiClient.communities.listListings(communityId)
    }

    override suspend fun createListing(
        communityId: String,
        request: CreateCommunityListingRequest,
    ): CommunityListing {
        return apiClient.communities.createListing(communityId, request)
    }

    override suspend fun listPurchases(communityId: String): CommunityPurchaseListResponse {
        return apiClient.communities.listPurchases(communityId)
    }

    override suspend fun createPurchaseQuote(
        communityId: String,
        request: CommunityPurchaseQuoteRequest,
    ): CommunityPurchaseQuote {
        return apiClient.communities.createPurchaseQuote(communityId, request)
    }

    override suspend fun settlePurchase(
        communityId: String,
        request: CommunityPurchaseSettlementRequest,
    ): CommunityPurchaseSettlement {
        return apiClient.communities.settlePurchase(communityId, request)
    }

    override suspend fun failPurchase(
        communityId: String,
        request: CommunityPurchaseSettlementFailureRequest,
    ): CommunityPurchaseSettlementFailure {
        return apiClient.communities.failPurchase(communityId, request)
    }
}

class ApiPostRepository(
    private val apiClient: ApiClient,
) : PostRepository {
    override suspend fun getPost(postId: String): LocalizedPostResponse = apiClient.posts.get(postId)

    override suspend fun getPublicPost(postId: String): LocalizedPostResponse = apiClient.publicPosts.get(postId)

    override suspend fun votePost(postId: String, value: Int): PostVoteResponse = apiClient.posts.vote(postId, value)

    override suspend fun listComments(
        communityId: String,
        postId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
    ): CommentListResponse {
        return apiClient.communities.listComments(
            communityId = communityId,
            postId = postId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
        )
    }

    override suspend fun listPublicComments(
        postId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
    ): CommentListResponse {
        return apiClient.publicComments.listPostComments(
            postId = postId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
        )
    }

    override suspend fun createComment(
        communityId: String,
        postId: String,
        request: CreateCommentRequest,
        altchaHeader: String?,
    ) {
        apiClient.communities.createComment(communityId, postId, request, altchaHeader)
    }

    override suspend fun listPublicReplies(
        commentId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
    ): CommentListResponse {
        return apiClient.publicComments.listReplies(
            commentId = commentId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
        )
    }

    override suspend fun listReplies(
        commentId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
    ): CommentListResponse {
        return apiClient.comments.listReplies(
            commentId = commentId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
        )
    }

    override suspend fun createReply(
        commentId: String,
        request: CreateCommentRequest,
        altchaHeader: String?,
    ) {
        apiClient.comments.createReply(commentId, request, altchaHeader)
    }

    override suspend fun voteComment(commentId: String, value: Int): CommentVoteResponse {
        return apiClient.comments.vote(commentId, value)
    }

    override suspend fun reportPost(communityId: String, postId: String, request: CreateUserReportRequest) {
        apiClient.communities.reportPost(communityId, postId, request)
    }

    override suspend fun reportComment(communityId: String, commentId: String, request: CreateUserReportRequest) {
        apiClient.communities.reportComment(communityId, commentId, request)
    }
}

class ApiProfileRepository(
    private val apiClient: ApiClient,
) : ProfileRepository {
    override suspend fun getMe(): Profile = apiClient.profiles.getMe()

    override suspend fun getPostableCommunities(): PostableCommunitiesResponse =
        apiClient.profiles.getPostableCommunities()

    override suspend fun getByUserId(userId: String): Profile = apiClient.profiles.getByUserId(userId)

    override suspend fun getPublicByHandle(handleLabel: String): PublicProfileResolution {
        return apiClient.profiles.getPublicByHandle(handleLabel)
    }

    override suspend fun getPublicByWallet(walletAddress: String): PublicProfileResolution {
        return apiClient.profiles.getPublicByWallet(walletAddress)
    }

    override suspend fun updateMe(input: ProfileUpdateInput): Profile = apiClient.profiles.updateMe(input)

    override suspend fun publishXmtpInbox(inboxId: String): Profile {
        return apiClient.profiles.publishXmtpInbox(inboxId)
    }

    override suspend fun uploadMedia(kind: String, bytes: ByteArray, filename: String, mimeType: String): String {
        return apiClient.profiles.uploadMedia(kind, bytes, filename, mimeType).mediaRef
    }

    override suspend fun renameHandle(desiredLabel: String): RenameHandleResponse {
        return apiClient.profiles.renameHandle(desiredLabel)
    }
}

class ApiVerificationRepository(
    private val apiClient: ApiClient,
) : VerificationRepository {
    override suspend fun startSession(input: StartVerificationSessionRequest): VerificationSession {
        return apiClient.verification.startSession(input)
    }

    override suspend fun getSession(verificationSessionId: String): VerificationSession {
        return apiClient.verification.getSession(verificationSessionId)
    }

    override suspend fun completeSession(
        verificationSessionId: String,
        input: CompleteVerificationSessionRequest,
    ): VerificationSession {
        return apiClient.verification.completeSession(
            sessionId = verificationSessionId,
            attestationId = input.attestationId,
            proof = input.proof,
            proofHash = input.proofHash,
            providerPayloadRef = input.providerPayloadRef,
        )
    }

    override suspend fun startNamespaceSession(
        family: String,
        rootLabel: String,
    ): sc.pirate.app.api.model.NamespaceVerificationSession {
        return apiClient.verification.startNamespaceSession(family, rootLabel)
    }

    override suspend fun getNamespaceSession(sessionId: String): sc.pirate.app.api.model.NamespaceVerificationSession {
        return apiClient.verification.getNamespaceSession(sessionId)
    }

    override suspend fun completeNamespaceSession(
        sessionId: String,
        restartChallenge: Boolean?,
    ): sc.pirate.app.api.model.NamespaceVerificationSession {
        return apiClient.verification.completeNamespaceSession(sessionId, restartChallenge)
    }
}

class ApiNotificationRepository(
    private val apiClient: ApiClient,
) : NotificationRepository {
    override suspend fun getSummary(): NotificationSummary = apiClient.notifications.getSummary()

    override suspend fun getTasks(): NotificationTasksResponse = apiClient.notifications.getTasks()

    override suspend fun getFeed(limit: Int?, cursor: String?): NotificationFeedResponse {
        return apiClient.notifications.getFeed(limit = limit, cursor = cursor)
    }

    override suspend fun markRead(eventIds: List<String>) {
        apiClient.notifications.markRead(eventIds)
    }

    override suspend fun dismissTask(taskId: String): UserTask {
        return apiClient.notifications.dismissTask(taskId)
    }
}

data class AppRepositories(
    val authRepository: AuthRepository,
    val onboardingRepository: OnboardingRepository,
    val feedRepository: FeedRepository,
    val communityRepository: CommunityRepository,
    val postRepository: PostRepository,
    val profileRepository: ProfileRepository,
    val verificationRepository: VerificationRepository,
    val notificationRepository: NotificationRepository,
)
