package sc.pirate.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

class ApiClient(private val sessionStore: SessionStore) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
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
    ): String {
        val response = withContext(Dispatchers.IO) {
            val url = "$baseUrl$path"
            val requestBuilder = Request.Builder().url(url)

            if (requireAuth) {
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
                message = errorResponse?.message ?: "Request failed with status ${response.status}",
                status = response.status,
                retryable = errorResponse?.retryable == true,
            )
        }

        return response.body
    }

    private suspend fun getString(path: String, requireAuth: Boolean = true): String =
        request(path, "GET", requireAuth = requireAuth)

    private suspend fun postString(path: String, body: String? = null, requireAuth: Boolean = true): String =
        request(path, "POST", body, requireAuth)

    private suspend fun patchString(path: String, body: String): String =
        request(path, "PATCH", body)

    private fun buildQueryPath(path: String, params: List<Pair<String, String?>>): String {
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

    object Auth {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun sessionExchange(proof: SessionExchangeProof): SessionExchangeResponse {
            val body = json.encodeToString(SessionExchangeRequest.serializer(), SessionExchangeRequest(proof))
            val response = client.postString("/auth/session/exchange", body, requireAuth = false)
            return json.decodeFromString(SessionExchangeResponse.serializer(), response)
        }
    }

    object Onboarding {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun getStatus(): OnboardingStatus {
            val response = client.getString("/onboarding/status")
            return json.decodeFromString(OnboardingStatus.serializer(), response)
        }

        suspend fun dismiss(): OnboardingStatus {
            val response = client.postString("/onboarding/dismiss", "{}")
            return json.decodeFromString(OnboardingStatus.serializer(), response)
        }

        suspend fun startRedditVerification(username: String): RedditVerification {
            val body = json.encodeToString(StartRedditVerificationRequest.serializer(), StartRedditVerificationRequest(username))
            val response = client.postString("/onboarding/reddit-verification", body)
            return json.decodeFromString(RedditVerification.serializer(), response)
        }

        suspend fun startRedditImport(username: String): String {
            val body = json.encodeToString(StartRedditImportRequest.serializer(), StartRedditImportRequest(username))
            val response = client.postString("/onboarding/reddit-imports", body)
            return response
        }

        suspend fun getLatestRedditImport(): RedditImportSummary {
            val response = client.getString("/onboarding/reddit-imports/latest")
            return json.decodeFromString(RedditImportSummary.serializer(), response)
        }
    }

    object Verification {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun startSession(input: StartVerificationSessionRequest): VerificationSession {
            val body = json.encodeToString(StartVerificationSessionRequest.serializer(), input)
            val response = client.postString("/verification-sessions", body)
            return json.decodeFromString(VerificationSession.serializer(), response)
        }

        suspend fun getSession(sessionId: String): VerificationSession {
            val response = client.getString("/verification-sessions/${sessionId}")
            return json.decodeFromString(VerificationSession.serializer(), response)
        }

        suspend fun completeSession(
            sessionId: String,
            attestationId: String? = null,
            proof: String? = null,
            proofHash: String? = null,
            providerPayloadRef: String? = null,
        ): VerificationSession {
            val body = json.encodeToString(
                CompleteVerificationSessionRequest.serializer(),
                CompleteVerificationSessionRequest(attestationId, proof, proofHash, providerPayloadRef),
            )
            val response = client.postString("/verification-sessions/$sessionId/complete", body)
            return json.decodeFromString(VerificationSession.serializer(), response)
        }

        suspend fun startNamespaceSession(family: String, rootLabel: String): NamespaceVerificationSession {
            val body = json.encodeToString(
                StartNamespaceVerificationSessionRequest.serializer(),
                StartNamespaceVerificationSessionRequest(family, rootLabel),
            )
            val response = client.postString("/namespace-verification-sessions", body)
            return json.decodeFromString(NamespaceVerificationSession.serializer(), response)
        }

        suspend fun completeNamespaceSession(sessionId: String, restartChallenge: Boolean? = null): NamespaceVerificationSession {
            val body = json.encodeToString(
                CompleteNamespaceVerificationSessionRequest.serializer(),
                CompleteNamespaceVerificationSessionRequest(restartChallenge),
            )
            val response = client.postString("/namespace-verification-sessions/$sessionId/complete", body)
            return json.decodeFromString(NamespaceVerificationSession.serializer(), response)
        }
    }

    object Feed {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun home(
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
            timeRange: String? = null,
        ): HomeFeedResponse {
            val path = client.buildQueryPath(
                "/feed/home",
                listOf(
                    "cursor" to cursor,
                    "locale" to locale,
                    "sort" to sort,
                    "time_range" to timeRange,
                ),
            )
            val response = client.getString(path)
            return json.decodeFromString(HomeFeedResponse.serializer(), response)
        }
    }

    object Communities {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun get(communityId: String): Community {
            val response = client.getString("/communities/$communityId")
            return json.decodeFromString(Community.serializer(), response)
        }

        suspend fun preview(communityId: String, locale: String? = null): CommunityPreview {
            val path = client.buildQueryPath(
                "/communities/$communityId/preview",
                listOf("locale" to locale),
            )
            val response = client.getString(path)
            return json.decodeFromString(CommunityPreview.serializer(), response)
        }

        suspend fun getJoinEligibility(communityId: String): JoinEligibility {
            val response = client.getString("/communities/$communityId/join-eligibility")
            return json.decodeFromString(JoinEligibility.serializer(), response)
        }

        suspend fun join(communityId: String): CommunityJoinResponse {
            val response = client.postString("/communities/$communityId/join")
            return json.decodeFromString(CommunityJoinResponse.serializer(), response)
        }

        suspend fun listPosts(
            communityId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
            flairId: String? = null,
        ): PostListResponse {
            val path = client.buildQueryPath(
                "/communities/$communityId/posts",
                listOf(
                    "cursor" to cursor,
                    "flair_id" to flairId,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = client.getString(path)
            return json.decodeFromString(PostListResponse.serializer(), response)
        }

        suspend fun createPost(communityId: String, request: CreatePostRequest): LocalizedPostResponse {
            val body = json.encodeToString(CreatePostRequest.serializer(), request)
            val response = client.postString("/communities/$communityId/posts", body)
            return json.decodeFromString(LocalizedPostResponse.serializer(), response)
        }

        suspend fun listComments(
            communityId: String,
            postId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
        ): CommentListResponse {
            val path = client.buildQueryPath(
                "/communities/$communityId/posts/$postId/comments",
                listOf(
                    "cursor" to cursor,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = client.getString(path)
            return json.decodeFromString(CommentListResponse.serializer(), response)
        }

        suspend fun createComment(
            communityId: String,
            postId: String,
            request: CreateCommentRequest,
        ) {
            val body = json.encodeToString(CreateCommentRequest.serializer(), request)
            client.postString("/communities/$communityId/posts/$postId/comments", body)
        }
    }

    object Posts {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun get(postId: String): LocalizedPostResponse {
            val response = client.getString("/posts/$postId")
            return json.decodeFromString(LocalizedPostResponse.serializer(), response)
        }

        suspend fun vote(postId: String, value: Int): PostVoteResponse {
            val body = json.encodeToString(PostVoteRequest.serializer(), PostVoteRequest(value))
            val response = client.postString("/posts/$postId/vote", body)
            return json.decodeFromString(PostVoteResponse.serializer(), response)
        }
    }

    object Comments {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun listReplies(
            commentId: String,
            limit: Int? = null,
            cursor: String? = null,
            locale: String? = null,
            sort: String? = null,
        ): CommentListResponse {
            val path = client.buildQueryPath(
                "/comments/$commentId/replies",
                listOf(
                    "cursor" to cursor,
                    "limit" to limit?.toString(),
                    "locale" to locale,
                    "sort" to sort,
                ),
            )
            val response = client.getString(path)
            return json.decodeFromString(CommentListResponse.serializer(), response)
        }

        suspend fun createReply(commentId: String, request: CreateCommentRequest) {
            val body = json.encodeToString(CreateCommentRequest.serializer(), request)
            client.postString("/comments/$commentId/replies", body)
        }

        suspend fun vote(commentId: String, value: Int): CommentVoteResponse {
            val body = json.encodeToString(CommentVoteRequest.serializer(), CommentVoteRequest(value))
            val response = client.postString("/comments/$commentId/vote", body)
            return json.decodeFromString(CommentVoteResponse.serializer(), response)
        }
    }

    object Profiles {
        private lateinit var client: ApiClient
        private val json get() = client.json

        fun init(c: ApiClient) { client = c }

        suspend fun getMe(): Profile {
            val response = client.getString("/profiles/me")
            return json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun getByUserId(userId: String): Profile {
            val response = client.getString("/profiles/$userId")
            return json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun updateMe(input: ProfileUpdateInput): Profile {
            val body = json.encodeToString(ProfileUpdateInput.serializer(), input)
            val response = client.patchString("/profiles/me", body)
            return json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun renameHandle(desiredLabel: String): RenameHandleResponse {
            val body = json.encodeToString(RenameHandleRequest.serializer(), RenameHandleRequest(desiredLabel))
            val response = client.postString("/profiles/me/global-handle/rename", body)
            return json.decodeFromString(RenameHandleResponse.serializer(), response)
        }
    }

    fun initEndpoints() {
        Auth.init(this)
        Onboarding.init(this)
        Verification.init(this)
        Feed.init(this)
        Communities.init(this)
        Posts.init(this)
        Comments.init(this)
        Profiles.init(this)
    }

    init {
        initEndpoints()
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
