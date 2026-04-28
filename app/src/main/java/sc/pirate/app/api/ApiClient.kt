package sc.pirate.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import sc.pirate.app.api.model.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class ApiError(
    val code: String,
    message: String,
    val status: Int,
    val retryable: Boolean = false,
) : Exception(message)

private fun displayApiErrorMessage(error: ErrorResponse?, status: Int): String {
    val code = error?.code
    return when (code) {
        "auth_error" -> "Sign in to continue."
        "internal_error" -> "Something went wrong. Please try again."
        else -> error?.message ?: "Request failed with status $status"
    }
}

class ApiClient(private val sessionStore: SessionStore) {

    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var baseUrl: String = sc.pirate.app.BuildConfig.API_BASE_URL

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        requireAuth: Boolean = true,
        optionalAuth: Boolean = false,
    ): String {
        val response = withContext(Dispatchers.IO) {
            val url = "$baseUrl$path"
            val requestBuilder = Request.Builder().url(url)

            if (requireAuth || optionalAuth) {
                val token = sessionStore.getAccessToken()
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }

            requestBuilder.header("Content-Type", "application/json")

            when {
                method == "GET" -> { /* default */ }
                body != null -> {
                    requestBuilder.method(method, body.toRequestBody(JSON_MEDIA_TYPE))
                }
                method != "GET" -> {
                    requestBuilder.method(method, "".toRequestBody(JSON_MEDIA_TYPE))
                }
            }

            client.newCall(requestBuilder.build()).execute().use { rawResponse ->
                ApiResponse(
                    successful = rawResponse.isSuccessful,
                    status = rawResponse.code,
                    body = rawResponse.body?.string().orEmpty(),
                )
            }
        }

        if (!response.successful) {
            val errorResponse = try {
                json.decodeFromString<ErrorResponse>(response.body)
            } catch (_: Exception) {
                null
            }
            throw ApiError(
                code = errorResponse?.code ?: "internal_error",
                message = displayApiErrorMessage(errorResponse, response.status),
                status = response.status,
                retryable = errorResponse?.retryable == true,
            )
        }

        return response.body
    }

    internal suspend fun getString(path: String, requireAuth: Boolean = true): String =
        request(path, "GET", requireAuth = requireAuth)

    private suspend fun getStringOptionalAuth(path: String): String {
        return try {
            request(path, "GET", requireAuth = false, optionalAuth = true)
        } catch (error: ApiError) {
            if (error.status == 401 && error.code == "auth_error") {
                request(path, "GET", requireAuth = false)
            } else {
                throw error
            }
        }
    }

    internal suspend fun postString(path: String, body: String? = null, requireAuth: Boolean = true): String =
        request(path, "POST", body, requireAuth)

    private suspend fun patchString(path: String, body: String): String =
        request(path, "PATCH", body)

    internal fun buildQueryPath(path: String, params: List<Pair<String, String?>>): String {
        val query = params
            .mapNotNull { (key, value) ->
                value?.takeIf { it.isNotBlank() }?.let {
                    "${encodeQueryValue(key)}=${encodeQueryValue(it)}"
                }
            }
            .joinToString("&")
        return if (query.isBlank()) path else "$path?$query"
    }

    private fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    internal fun encodePathSegment(value: String): String =
        "https://pirate.local/$value".toHttpUrl().encodedPathSegments.last()

    val auth = AuthEndpoints(this)
    val onboarding = OnboardingEndpoints(this)
    val verification = VerificationEndpoints(this)
    val feed = FeedEndpoints(this)
    val communities = CommunitiesEndpoints(this)
    val publicCommunities = PublicCommunitiesEndpoints(this)
    val posts = PostsEndpoints(this)
    val publicPosts = PublicPostsEndpoints(this)
    val comments = CommentsEndpoints(this)
    val publicComments = PublicCommentsEndpoints(this)
    val profiles = ProfilesEndpoints(this)
    val notifications = NotificationsEndpoints(this)

    class AuthEndpoints internal constructor(private val api: ApiClient) {
        suspend fun sessionExchange(proof: SessionExchangeProof): SessionExchangeResponse {
            val body = api.json.encodeToString(SessionExchangeRequest.serializer(), SessionExchangeRequest(proof))
            val response = api.postString("/auth/session/exchange", body, requireAuth = false)
            return api.json.decodeFromString(SessionExchangeResponse.serializer(), response)
        }
    }

    class OnboardingEndpoints internal constructor(private val api: ApiClient) {
        suspend fun getStatus(): OnboardingStatus {
            val response = api.getString("/onboarding/status")
            return api.json.decodeFromString(OnboardingStatus.serializer(), response)
        }

        suspend fun dismiss(): OnboardingStatus {
            val response = api.postString("/onboarding/dismiss", "{}")
            return api.json.decodeFromString(OnboardingStatus.serializer(), response)
        }

        suspend fun startRedditVerification(username: String): RedditVerification {
            val body = api.json.encodeToString(StartRedditVerificationRequest.serializer(), StartRedditVerificationRequest(username))
            val response = api.postString("/onboarding/reddit-verification", body)
            return api.json.decodeFromString(RedditVerification.serializer(), response)
        }

        suspend fun startRedditImport(username: String): String {
            val body = api.json.encodeToString(StartRedditImportRequest.serializer(), StartRedditImportRequest(username))
            val response = api.postString("/onboarding/reddit-imports", body)
            return response
        }

        suspend fun getLatestRedditImport(): RedditImportSummary {
            val response = api.getString("/onboarding/reddit-imports/latest")
            return api.json.decodeFromString(RedditImportSummary.serializer(), response)
        }
    }

    class VerificationEndpoints internal constructor(private val api: ApiClient) {
        suspend fun startSession(input: StartVerificationSessionRequest): VerificationSession {
            val body = api.json.encodeToString(StartVerificationSessionRequest.serializer(), input)
            val response = api.postString("/verification-sessions", body)
            return api.json.decodeFromString(VerificationSession.serializer(), response)
        }

        suspend fun getSession(sessionId: String): VerificationSession {
            val response = api.getString("/verification-sessions/${sessionId}")
            return api.json.decodeFromString(VerificationSession.serializer(), response)
        }

        suspend fun completeSession(
            sessionId: String,
            attestationId: String? = null,
            proof: String? = null,
            proofHash: String? = null,
            providerPayloadRef: String? = null,
        ): VerificationSession {
            val body = api.json.encodeToString(
                CompleteVerificationSessionRequest.serializer(),
                CompleteVerificationSessionRequest(attestationId, proof, proofHash, providerPayloadRef),
            )
            val response = api.postString("/verification-sessions/$sessionId/complete", body)
            return api.json.decodeFromString(VerificationSession.serializer(), response)
        }

        suspend fun startNamespaceSession(family: String, rootLabel: String): NamespaceVerificationSession {
            val body = api.json.encodeToString(
                StartNamespaceVerificationSessionRequest.serializer(),
                StartNamespaceVerificationSessionRequest(family, rootLabel),
            )
            val response = api.postString("/namespace-verification-sessions", body)
            return api.json.decodeFromString(NamespaceVerificationSession.serializer(), response)
        }

        suspend fun completeNamespaceSession(sessionId: String, restartChallenge: Boolean? = null): NamespaceVerificationSession {
            val body = api.json.encodeToString(
                CompleteNamespaceVerificationSessionRequest.serializer(),
                CompleteNamespaceVerificationSessionRequest(restartChallenge),
            )
            val response = api.postString("/namespace-verification-sessions/$sessionId/complete", body)
            return api.json.decodeFromString(NamespaceVerificationSession.serializer(), response)
        }

        suspend fun getNamespaceSession(sessionId: String): NamespaceVerificationSession {
            val response = api.getString("/namespace-verification-sessions/$sessionId")
            return api.json.decodeFromString(NamespaceVerificationSession.serializer(), response)
        }
    }

    class FeedEndpoints internal constructor(private val api: ApiClient) {
        suspend fun home(
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
            timeRange: String? = null,
        ): HomeFeedResponse {
            val path = api.buildQueryPath(
                "/feed/home",
                listOf(
                    "cursor" to cursor,
                    "locale" to locale,
                    "sort" to sort,
                    "time_range" to timeRange,
                ),
            )
            val response = api.getStringOptionalAuth(path)
            return api.json.decodeFromString(HomeFeedResponse.serializer(), response)
        }
    }

    class CommunitiesEndpoints internal constructor(private val api: ApiClient) {
        suspend fun create(request: CreateCommunityRequest): CommunityCreateAcceptedResponse {
            val body = api.json.encodeToString(CreateCommunityRequest.serializer(), request)
            val response = api.postString("/communities", body)
            return api.json.decodeFromString(CommunityCreateAcceptedResponse.serializer(), response)
        }

        suspend fun get(communityId: String): Community {
            val response = api.getString("/communities/$communityId")
            return api.json.decodeFromString(Community.serializer(), response)
        }

        suspend fun preview(communityId: String, locale: String? = null): CommunityPreview {
            val path = api.buildQueryPath(
                "/communities/$communityId/preview",
                listOf("locale" to locale),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(CommunityPreview.serializer(), response)
        }

        suspend fun getJoinEligibility(communityId: String): JoinEligibility {
            val response = api.getString("/communities/$communityId/join-eligibility")
            return api.json.decodeFromString(JoinEligibility.serializer(), response)
        }

        suspend fun join(communityId: String): CommunityJoinResponse {
            val response = api.postString("/communities/$communityId/join")
            return api.json.decodeFromString(CommunityJoinResponse.serializer(), response)
        }

        suspend fun follow(communityId: String): CommunityFollowResponse {
            val response = api.request("/communities/$communityId/follow", "PUT", "{}")
            return api.json.decodeFromString(CommunityFollowResponse.serializer(), response)
        }

        suspend fun unfollow(communityId: String): CommunityFollowResponse {
            val response = api.request("/communities/$communityId/follow", "DELETE")
            return api.json.decodeFromString(CommunityFollowResponse.serializer(), response)
        }

        suspend fun attachNamespace(communityId: String, namespaceVerificationId: String): Community {
            val body = api.json.encodeToString(
                AttachNamespaceRequest.serializer(),
                AttachNamespaceRequest(namespaceVerificationId),
            )
            val response = api.postString("/communities/$communityId/namespace", body)
            return api.json.decodeFromString(Community.serializer(), response)
        }

        suspend fun setPendingNamespaceSession(communityId: String, sessionId: String?): Community {
            val body = api.json.encodeToString(
                SetPendingNamespaceSessionRequest.serializer(),
                SetPendingNamespaceSessionRequest(sessionId),
            )
            val response = api.request("/communities/$communityId/pending-namespace-session", "PUT", body)
            return api.json.decodeFromString(Community.serializer(), response)
        }

        suspend fun listPosts(
            communityId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
            flairId: String? = null,
        ): PostListResponse {
            val path = api.buildQueryPath(
                "/communities/$communityId/posts",
                listOf(
                    "cursor" to cursor,
                    "flair_id" to flairId,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(PostListResponse.serializer(), response)
        }

        suspend fun createPost(communityId: String, request: CreatePostRequest): LocalizedPostResponse {
            val body = api.json.encodeToString(CreatePostRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/posts", body)
            return api.json.decodeFromString(LocalizedPostResponse.serializer(), response)
        }

        suspend fun listComments(
            communityId: String,
            postId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
        ): CommentListResponse {
            val path = api.buildQueryPath(
                "/communities/$communityId/posts/$postId/comments",
                listOf(
                    "cursor" to cursor,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(CommentListResponse.serializer(), response)
        }

        suspend fun createComment(
            communityId: String,
            postId: String,
            request: CreateCommentRequest,
        ) {
            val body = api.json.encodeToString(CreateCommentRequest.serializer(), request)
            api.postString("/communities/$communityId/posts/$postId/comments", body)
        }
    }

    class PublicCommunitiesEndpoints internal constructor(private val api: ApiClient) {
        suspend fun preview(communityId: String, locale: String? = null): CommunityPreview {
            val path = api.buildQueryPath(
                "/public-communities/${api.encodePathSegment(communityId)}",
                listOf("locale" to locale),
            )
            val response = api.getString(path, requireAuth = false)
            return api.json.decodeFromString(CommunityPreview.serializer(), response)
        }

        suspend fun listPosts(
            communityId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
            flairId: String? = null,
        ): PostListResponse {
            val path = api.buildQueryPath(
                "/public-communities/${api.encodePathSegment(communityId)}/posts",
                listOf(
                    "cursor" to cursor,
                    "flair_id" to flairId,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = api.getString(path, requireAuth = false)
            return api.json.decodeFromString(PostListResponse.serializer(), response)
        }
    }

    class PostsEndpoints internal constructor(private val api: ApiClient) {
        suspend fun get(postId: String): LocalizedPostResponse {
            val response = api.getString("/posts/$postId")
            return api.json.decodeFromString(LocalizedPostResponse.serializer(), response)
        }

        suspend fun vote(postId: String, value: Int): PostVoteResponse {
            val body = api.json.encodeToString(PostVoteRequest.serializer(), PostVoteRequest(value))
            val response = api.postString("/posts/$postId/vote", body)
            return api.json.decodeFromString(PostVoteResponse.serializer(), response)
        }
    }

    class PublicPostsEndpoints internal constructor(private val api: ApiClient) {
        suspend fun get(postId: String, locale: String? = null): LocalizedPostResponse {
            val path = api.buildQueryPath(
                "/public-posts/${api.encodePathSegment(postId)}",
                listOf("locale" to locale),
            )
            val response = api.getString(path, requireAuth = false)
            return api.json.decodeFromString(LocalizedPostResponse.serializer(), response)
        }
    }

    class CommentsEndpoints internal constructor(private val api: ApiClient) {
        suspend fun listReplies(
            commentId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
        ): CommentListResponse {
            val path = api.buildQueryPath(
                "/comments/$commentId/replies",
                listOf(
                    "cursor" to cursor,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(CommentListResponse.serializer(), response)
        }

        suspend fun createReply(commentId: String, request: CreateCommentRequest) {
            val body = api.json.encodeToString(CreateCommentRequest.serializer(), request)
            api.postString("/comments/$commentId/replies", body)
        }

        suspend fun vote(commentId: String, value: Int): CommentVoteResponse {
            val body = api.json.encodeToString(CommentVoteRequest.serializer(), CommentVoteRequest(value))
            val response = api.postString("/comments/$commentId/vote", body)
            return api.json.decodeFromString(CommentVoteResponse.serializer(), response)
        }
    }

    class PublicCommentsEndpoints internal constructor(private val api: ApiClient) {
        suspend fun listPostComments(
            postId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
        ): CommentListResponse {
            val path = api.buildQueryPath(
                "/public-comments/posts/${api.encodePathSegment(postId)}/comments",
                listOf(
                    "cursor" to cursor,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = api.getString(path, requireAuth = false)
            return api.json.decodeFromString(CommentListResponse.serializer(), response)
        }

        suspend fun listReplies(
            commentId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
        ): CommentListResponse {
            val path = api.buildQueryPath(
                "/public-comments/${api.encodePathSegment(commentId)}/replies",
                listOf(
                    "cursor" to cursor,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = api.getString(path, requireAuth = false)
            return api.json.decodeFromString(CommentListResponse.serializer(), response)
        }
    }

    class ProfilesEndpoints internal constructor(private val api: ApiClient) {
        suspend fun getMe(): Profile {
            val response = api.getString("/profiles/me")
            return api.json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun getByUserId(userId: String): Profile {
            val response = api.getString("/profiles/$userId")
            return api.json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun getPublicByHandle(handleLabel: String): PublicProfileResolution {
            val response = api.getString(
                "/public-profiles/${api.encodePathSegment(handleLabel)}",
                requireAuth = false,
            )
            return api.json.decodeFromString(PublicProfileResolution.serializer(), response)
        }

        suspend fun updateMe(input: ProfileUpdateInput): Profile {
            val body = api.json.encodeToString(ProfileUpdateInput.serializer(), input)
            val response = api.patchString("/profiles/me", body)
            return api.json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun renameHandle(desiredLabel: String): RenameHandleResponse {
            val body = api.json.encodeToString(RenameHandleRequest.serializer(), RenameHandleRequest(desiredLabel))
            val response = api.postString("/profiles/me/global-handle/rename", body)
            return api.json.decodeFromString(RenameHandleResponse.serializer(), response)
        }
    }

    class NotificationsEndpoints internal constructor(private val api: ApiClient) {
        suspend fun getTasks(): NotificationTasksResponse {
            val response = api.getString("/notifications/tasks")
            return api.json.decodeFromString(NotificationTasksResponse.serializer(), response)
        }

        suspend fun getFeed(limit: Int? = null, cursor: String? = null): NotificationFeedResponse {
            val path = api.buildQueryPath(
                "/notifications/feed",
                listOf(
                    "cursor" to cursor,
                    "limit" to limit?.toString(),
                ),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(NotificationFeedResponse.serializer(), response)
        }

        suspend fun markRead(eventIds: List<String> = emptyList()) {
            val body = api.json.encodeToString(MarkNotificationsReadRequest.serializer(), MarkNotificationsReadRequest(eventIds))
            api.postString("/notifications/mark-read", body)
        }

        suspend fun dismissTask(taskId: String): UserTask {
            val body = api.json.encodeToString(DismissNotificationTaskRequest.serializer(), DismissNotificationTaskRequest(taskId))
            val response = api.postString("/notifications/dismiss-task", body)
            return api.json.decodeFromString(UserTask.serializer(), response)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private data class ApiResponse(
    val successful: Boolean,
    val status: Int,
    val body: String,
)

@kotlinx.serialization.Serializable
data class ProfileUpdateInput(
    @kotlinx.serialization.SerialName("display_name") val displayName: String? = null,
    @kotlinx.serialization.SerialName("avatar_ref") val avatarRef: String? = null,
    val bio: String? = null,
    @kotlinx.serialization.SerialName("preferred_locale") val preferredLocale: String? = null,
)
