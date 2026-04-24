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
    suspend fun getPreview(communityId: String, locale: String? = null): CommunityPreview
    suspend fun getJoinEligibility(communityId: String): JoinEligibility
    suspend fun joinCommunity(communityId: String): CommunityJoinResponse
    suspend fun listPosts(
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
    suspend fun votePost(postId: String, value: Int): PostVoteResponse
    suspend fun listComments(
        communityId: String,
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
        return ApiClient.Auth.sessionExchange(proof)
    }
}

class ApiOnboardingRepository(
    private val apiClient: ApiClient,
) : OnboardingRepository {
    override suspend fun getStatus(): OnboardingStatus = ApiClient.Onboarding.getStatus()

    override suspend fun dismiss(): OnboardingStatus = ApiClient.Onboarding.dismiss()

    override suspend fun startRedditVerification(username: String): RedditVerification {
        return ApiClient.Onboarding.startRedditVerification(username)
    }

    override suspend fun startRedditImport(username: String): String {
        return ApiClient.Onboarding.startRedditImport(username)
    }

    override suspend fun getLatestRedditImport(): RedditImportSummary {
        return ApiClient.Onboarding.getLatestRedditImport()
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
        return ApiClient.Feed.home(
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
        return ApiClient.Communities.create(request)
    }

    override suspend fun getCommunity(communityId: String): Community {
        return ApiClient.Communities.get(communityId)
    }

    override suspend fun getPreview(communityId: String, locale: String?): CommunityPreview {
        return ApiClient.Communities.preview(communityId, locale)
    }

    override suspend fun getJoinEligibility(communityId: String): JoinEligibility {
        return ApiClient.Communities.getJoinEligibility(communityId)
    }

    override suspend fun joinCommunity(communityId: String): CommunityJoinResponse {
        return ApiClient.Communities.join(communityId)
    }

    override suspend fun listPosts(
        communityId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
        flairId: String?,
    ): PostListResponse {
        return ApiClient.Communities.listPosts(
            communityId = communityId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
            flairId = flairId,
        )
    }

    override suspend fun createPost(communityId: String, request: CreatePostRequest): LocalizedPostResponse {
        return ApiClient.Communities.createPost(communityId, request)
    }
}

class ApiPostRepository(
    private val apiClient: ApiClient,
) : PostRepository {
    override suspend fun getPost(postId: String): LocalizedPostResponse = ApiClient.Posts.get(postId)

    override suspend fun votePost(postId: String, value: Int): PostVoteResponse = ApiClient.Posts.vote(postId, value)

    override suspend fun listComments(
        communityId: String,
        postId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
    ): CommentListResponse {
        return ApiClient.Communities.listComments(
            communityId = communityId,
            postId = postId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
        )
    }

    override suspend fun createComment(communityId: String, postId: String, request: CreateCommentRequest) {
        ApiClient.Communities.createComment(communityId, postId, request)
    }

    override suspend fun listReplies(
        commentId: String,
        limit: Int?,
        cursor: String?,
        locale: String?,
        sort: String?,
    ): CommentListResponse {
        return ApiClient.Comments.listReplies(
            commentId = commentId,
            limit = limit,
            cursor = cursor,
            locale = locale,
            sort = sort,
        )
    }

    override suspend fun createReply(commentId: String, request: CreateCommentRequest) {
        ApiClient.Comments.createReply(commentId, request)
    }

    override suspend fun voteComment(commentId: String, value: Int): CommentVoteResponse {
        return ApiClient.Comments.vote(commentId, value)
    }
}

class ApiProfileRepository(
    private val apiClient: ApiClient,
) : ProfileRepository {
    override suspend fun getMe(): Profile = ApiClient.Profiles.getMe()

    override suspend fun getByUserId(userId: String): Profile = ApiClient.Profiles.getByUserId(userId)

    override suspend fun getPublicByHandle(handleLabel: String): PublicProfileResolution {
        return ApiClient.Profiles.getPublicByHandle(handleLabel)
    }

    override suspend fun updateMe(input: ProfileUpdateInput): Profile = ApiClient.Profiles.updateMe(input)

    override suspend fun renameHandle(desiredLabel: String): RenameHandleResponse {
        return ApiClient.Profiles.renameHandle(desiredLabel)
    }
}

class ApiVerificationRepository(
    private val apiClient: ApiClient,
) : VerificationRepository {
    override suspend fun startSession(input: StartVerificationSessionRequest): VerificationSession {
        return ApiClient.Verification.startSession(input)
    }

    override suspend fun getSession(verificationSessionId: String): VerificationSession {
        return ApiClient.Verification.getSession(verificationSessionId)
    }

    override suspend fun completeSession(
        verificationSessionId: String,
        input: CompleteVerificationSessionRequest,
    ): VerificationSession {
        return ApiClient.Verification.completeSession(
            sessionId = verificationSessionId,
            attestationId = input.attestationId,
            proof = input.proof,
            proofHash = input.proofHash,
            providerPayloadRef = input.providerPayloadRef,
        )
    }
}

class ApiNotificationRepository(
    private val apiClient: ApiClient,
) : NotificationRepository {
    override suspend fun getTasks(): NotificationTasksResponse = ApiClient.Notifications.getTasks()

    override suspend fun getFeed(limit: Int?, cursor: String?): NotificationFeedResponse {
        return ApiClient.Notifications.getFeed(limit = limit, cursor = cursor)
    }

    override suspend fun markRead(eventIds: List<String>) {
        ApiClient.Notifications.markRead(eventIds)
    }

    override suspend fun dismissTask(taskId: String): UserTask {
        return ApiClient.Notifications.dismissTask(taskId)
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
