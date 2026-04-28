package sc.pirate.app.shared.api

import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.CompleteVerificationSessionRequest
import sc.pirate.app.api.CreateCommentRequest
import sc.pirate.app.api.ProfileUpdateInput
import sc.pirate.app.api.RenameHandleResponse
import sc.pirate.app.api.SessionExchangeProof
import sc.pirate.app.api.StartVerificationSessionRequest
import sc.pirate.app.api.model.Community
import sc.pirate.app.api.model.CommunityCreateAcceptedResponse
import sc.pirate.app.api.model.CommunityFollowResponse
import sc.pirate.app.api.model.CommunityJoinResponse
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CommentListResponse
import sc.pirate.app.api.model.CommentVoteResponse
import sc.pirate.app.api.model.CreateCommunityRequest
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.api.model.HomeFeedResponse
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.NotificationFeedResponse
import sc.pirate.app.api.model.NotificationTasksResponse
import sc.pirate.app.api.model.OnboardingStatus
import sc.pirate.app.api.model.PostListResponse
import sc.pirate.app.api.model.PostVoteResponse
import sc.pirate.app.api.model.Profile
import sc.pirate.app.api.model.PublicProfileResolution
import sc.pirate.app.api.model.RedditImportSummary
import sc.pirate.app.api.model.RedditVerification
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.api.model.UserTask
import sc.pirate.app.api.model.VerificationSession

interface AuthRepository {
    suspend fun exchangeSession(proof: SessionExchangeProof): SessionExchangeResponse
}

interface OnboardingRepository {
    suspend fun getStatus(): OnboardingStatus
    suspend fun dismiss(): OnboardingStatus
    suspend fun startRedditVerification(username: String): RedditVerification
    suspend fun startRedditImport(username: String): String
    suspend fun getLatestRedditImport(): RedditImportSummary
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
    suspend fun getJoinEligibility(communityId: String): JoinEligibility
    suspend fun joinCommunity(communityId: String): CommunityJoinResponse
    suspend fun followCommunity(communityId: String): CommunityFollowResponse
    suspend fun unfollowCommunity(communityId: String): CommunityFollowResponse
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
    suspend fun createPost(communityId: String, request: CreatePostRequest): LocalizedPostResponse
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
    suspend fun createComment(communityId: String, postId: String, request: CreateCommentRequest)
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
    suspend fun createReply(commentId: String, request: CreateCommentRequest)
    suspend fun voteComment(commentId: String, value: Int): CommentVoteResponse
}

interface ProfileRepository {
    suspend fun getMe(): Profile
    suspend fun getByUserId(userId: String): Profile
    suspend fun getPublicByHandle(handleLabel: String): PublicProfileResolution
    suspend fun updateMe(input: ProfileUpdateInput): Profile
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

    override suspend fun startRedditVerification(username: String): RedditVerification {
        return apiClient.onboarding.startRedditVerification(username)
    }

    override suspend fun startRedditImport(username: String): String {
        return apiClient.onboarding.startRedditImport(username)
    }

    override suspend fun getLatestRedditImport(): RedditImportSummary {
        return apiClient.onboarding.getLatestRedditImport()
    }
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

    override suspend fun getJoinEligibility(communityId: String): JoinEligibility {
        return apiClient.communities.getJoinEligibility(communityId)
    }

    override suspend fun joinCommunity(communityId: String): CommunityJoinResponse {
        return apiClient.communities.join(communityId)
    }

    override suspend fun followCommunity(communityId: String): CommunityFollowResponse {
        return apiClient.communities.follow(communityId)
    }

    override suspend fun unfollowCommunity(communityId: String): CommunityFollowResponse {
        return apiClient.communities.unfollow(communityId)
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

    override suspend fun createPost(communityId: String, request: CreatePostRequest): LocalizedPostResponse {
        return apiClient.communities.createPost(communityId, request)
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

    override suspend fun createComment(communityId: String, postId: String, request: CreateCommentRequest) {
        apiClient.communities.createComment(communityId, postId, request)
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

    override suspend fun createReply(commentId: String, request: CreateCommentRequest) {
        apiClient.comments.createReply(commentId, request)
    }

    override suspend fun voteComment(commentId: String, value: Int): CommentVoteResponse {
        return apiClient.comments.vote(commentId, value)
    }
}

class ApiProfileRepository(
    private val apiClient: ApiClient,
) : ProfileRepository {
    override suspend fun getMe(): Profile = apiClient.profiles.getMe()

    override suspend fun getByUserId(userId: String): Profile = apiClient.profiles.getByUserId(userId)

    override suspend fun getPublicByHandle(handleLabel: String): PublicProfileResolution {
        return apiClient.profiles.getPublicByHandle(handleLabel)
    }

    override suspend fun updateMe(input: ProfileUpdateInput): Profile = apiClient.profiles.updateMe(input)

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
