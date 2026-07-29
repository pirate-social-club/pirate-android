package sc.pirate.app.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import sc.pirate.app.api.model.*
import java.net.URLEncoder
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ApiError(
    val code: String,
    message: String,
    val status: Int,
    val retryable: Boolean = false,
    val details: GateFailureDetails? = null,
) : Exception(message)

private fun displayApiErrorMessage(error: ErrorResponse?, status: Int): String {
    val code = error?.code
    return when (code) {
        "auth_error" -> "Sign in to continue."
        "internal_error" -> "Something went wrong. Please try again."
        else -> error?.message ?: "Request failed with status $status"
    }
}

private fun String?.toAltchaHeader(): Map<String, String> =
    if (isNullOrBlank()) emptyMap() else mapOf("X-Pirate-Altcha" to this)

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
        headers: Map<String, String> = emptyMap(),
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
            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }

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
                details = errorResponse?.details,
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

    internal suspend fun postString(
        path: String,
        body: String? = null,
        requireAuth: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): String = request(path, "POST", body, requireAuth, headers = headers)

    private suspend fun patchString(path: String, body: String): String =
        request(path, "PATCH", body)

    private suspend fun postMultipartString(
        path: String,
        parts: MultipartBody,
        requireAuth: Boolean = true,
    ): String {
        val response = withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url("$baseUrl$path")
            if (requireAuth) {
                val token = sessionStore.getAccessToken()
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }
            client.newCall(
                requestBuilder
                    .post(parts)
                    .build(),
            ).execute().use { rawResponse ->
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
                details = errorResponse?.details,
            )
        }

        return response.body
    }

    private suspend fun putBytesString(
        path: String,
        bytes: ByteArray,
        contentType: String = "application/octet-stream",
        requireAuth: Boolean = true,
    ): String {
        val response = withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url("$baseUrl$path")
            if (requireAuth) {
                val token = sessionStore.getAccessToken()
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
            }
            client.newCall(
                requestBuilder
                    .put(bytes.toRequestBody(contentType.toMediaTypeOrNull()))
                    .build(),
            ).execute().use { rawResponse ->
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
                details = errorResponse?.details,
            )
        }

        return response.body
    }

    private suspend fun putExternalBytesEtag(url: String, bytes: ByteArray, contentType: String): String {
        return withContext(Dispatchers.IO) {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .put(bytes.toRequestBody(contentType.toMediaTypeOrNull()))
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Multipart part upload failed with status ${response.code}")
                }
                response.header("ETag")?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Multipart part upload did not return an ETag")
            }
        }
    }

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
    val study = StudyEndpoints(this)

    /**
     * Song study. Every path is community-scoped because study access is decided by community
     * membership and entitlement, not by the post alone.
     */
    class StudyEndpoints internal constructor(private val api: ApiClient) {
        suspend fun getPostStudy(
            communityId: String,
            postId: String,
            targetLanguage: String? = null,
        ): SongStudyPayload {
            val path = api.buildQueryPath(
                "/communities/${api.encodePathSegment(communityId)}/posts/${api.encodePathSegment(postId)}/study",
                listOf("target_language" to targetLanguage),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(SongStudyPayload.serializer(), response)
        }

        suspend fun submitAttempt(
            communityId: String,
            postId: String,
            request: SongStudyAttemptRequest,
        ): SongStudyAttemptResult {
            val body = api.json.encodeToString(SongStudyAttemptRequest.serializer(), request)
            val response = api.postString(
                "/communities/${api.encodePathSegment(communityId)}/posts/${api.encodePathSegment(postId)}/study/attempts",
                body,
            )
            return api.json.decodeFromString(SongStudyAttemptResult.serializer(), response)
        }

        /** Say-it-back audio. The form field is `file`, matching the web client. */
        suspend fun transcribe(
            communityId: String,
            postId: String,
            bytes: ByteArray,
            filename: String,
            mimeType: String,
        ): SongStudyTranscriptionResponse {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                .build()
            val response = api.postMultipartString(
                "/communities/${api.encodePathSegment(communityId)}/posts/${api.encodePathSegment(postId)}/study/transcriptions",
                body,
            )
            return api.json.decodeFromString(SongStudyTranscriptionResponse.serializer(), response)
        }

        suspend fun getStreakLeaderboard(
            communityId: String,
            postId: String,
            limit: Int? = null,
        ): SongStreakLeaderboard {
            val path = api.buildQueryPath(
                "/communities/${api.encodePathSegment(communityId)}/posts/${api.encodePathSegment(postId)}/streaks/leaderboard",
                listOf("limit" to limit?.toString()),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(SongStreakLeaderboard.serializer(), response)
        }
    }

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
    }

    class VerificationEndpoints internal constructor(private val api: ApiClient) {
        suspend fun getAltchaChallenge(scope: String, action: String): AltchaChallenge {
            val path = api.buildQueryPath(
                "/verification/altcha/challenge",
                listOf("scope" to scope, "action" to action),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(AltchaChallenge.serializer(), response)
        }

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
            providerPayloadRef: JsonElement? = null,
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

        suspend fun join(communityId: String, altchaHeader: String? = null): CommunityJoinResponse {
            val response = api.postString(
                "/communities/$communityId/join",
                headers = altchaHeader.toAltchaHeader(),
            )
            return api.json.decodeFromString(CommunityJoinResponse.serializer(), response)
        }

        suspend fun follow(communityId: String): CommunityFollowResponse {
            val response = api.request("/communities/$communityId/follow", "POST", "{}")
            return api.json.decodeFromString(CommunityFollowResponse.serializer(), response)
        }

        suspend fun unfollow(communityId: String): CommunityFollowResponse {
            val response = api.request("/communities/$communityId/unfollow", "POST", "{}")
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

        suspend fun createPost(
            communityId: String,
            request: CreatePostRequest,
            altchaHeader: String? = null,
        ): LocalizedPostResponse {
            val body = api.json.encodeToString(CreatePostRequest.serializer(), request)
            val response = api.postString(
                "/communities/$communityId/posts",
                body,
                headers = altchaHeader.toAltchaHeader(),
            )
            return api.json.decodeFromString(LocalizedPostResponse.serializer(), response)
        }

        suspend fun uploadMedia(
            kind: String,
            bytes: ByteArray,
            filename: String,
            mimeType: String,
        ): CommunityMediaUploadResponse {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("kind", kind)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                )
                .build()
            val response = api.postMultipartString("/community-media", body)
            return api.json.decodeFromString(CommunityMediaUploadResponse.serializer(), response)
        }

        suspend fun createArtifactUpload(
            communityId: String,
            request: CreateSongArtifactUploadRequest,
        ): SongArtifactUpload {
            val body = api.json.encodeToString(CreateSongArtifactUploadRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/song-artifact-uploads", body)
            return api.json.decodeFromString(SongArtifactUpload.serializer(), response)
        }

        suspend fun uploadArtifactContent(
            communityId: String,
            uploadId: String,
            bytes: ByteArray,
        ): SongArtifactUpload {
            val response = api.putBytesString(
                "/communities/$communityId/song-artifact-uploads/${api.encodePathSegment(uploadId)}/content",
                bytes,
            )
            return api.json.decodeFromString(SongArtifactUpload.serializer(), response)
        }

        suspend fun uploadArtifactMultipart(
            communityId: String,
            intent: SongArtifactUpload,
            bytes: ByteArray,
            mimeType: String,
        ): SongArtifactUpload {
            val session = intent.uploadSession
                ?: throw IllegalStateException("Artifact upload did not return a multipart session")
            val parts = mutableListOf<SongArtifactCompletedPart>()
            try {
                for (partNumber in 1..session.totalParts) {
                    val start = ((partNumber - 1) * session.partSizeBytes).toInt()
                    val end = minOf(bytes.size.toLong(), start.toLong() + session.partSizeBytes).toInt()
                    val signedResponse = api.getString(
                        "/communities/$communityId/song-artifact-uploads/${api.encodePathSegment(intent.id)}" +
                            "/sessions/${api.encodePathSegment(session.id)}/parts/$partNumber/signed-url",
                    )
                    val signed = api.json.decodeFromString(SongArtifactPartSignedUrl.serializer(), signedResponse)
                    val etag = api.putExternalBytesEtag(signed.url, bytes.copyOfRange(start, end), mimeType)
                    parts += SongArtifactCompletedPart(partNumber = partNumber, etag = etag)
                }
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(bytes)
                    .joinToString("") { byte -> "%02x".format(byte) }
                val completion = CompleteSongArtifactUploadRequest(
                    uploadId = session.uploadId,
                    parts = parts,
                    contentHash = "0x$hash",
                )
                val body = api.json.encodeToString(CompleteSongArtifactUploadRequest.serializer(), completion)
                val response = api.postString(
                    "/communities/$communityId/song-artifact-uploads/${api.encodePathSegment(intent.id)}" +
                        "/sessions/${api.encodePathSegment(session.id)}/complete",
                    body,
                )
                return api.json.decodeFromString(SongArtifactUpload.serializer(), response)
            } catch (error: Throwable) {
                runCatching {
                    api.postString(
                        "/communities/$communityId/song-artifact-uploads/${api.encodePathSegment(intent.id)}" +
                            "/sessions/${api.encodePathSegment(session.id)}/abort",
                        "{}",
                    )
                }
                throw error
            }
        }

        suspend fun createSongArtifactBundle(
            communityId: String,
            request: CreateSongArtifactBundleRequest,
        ): SongArtifactBundle {
            val body = api.json.encodeToString(CreateSongArtifactBundleRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/song-artifacts", body)
            return api.json.decodeFromString(SongArtifactBundle.serializer(), response)
        }

        suspend fun getSongArtifactBundle(communityId: String, bundleId: String): SongArtifactBundle {
            val response = api.getString("/communities/$communityId/song-artifacts/${api.encodePathSegment(bundleId)}")
            return api.json.decodeFromString(SongArtifactBundle.serializer(), response)
        }

        suspend fun listDerivativeSources(
            communityId: String,
            kind: String? = null,
            scope: String? = null,
            query: String? = null,
            limit: Int? = null,
        ): DerivativeSourceListResponse {
            val path = api.buildQueryPath(
                "/communities/$communityId/derivative-sources",
                listOf(
                    "kind" to kind,
                    "scope" to scope,
                    "q" to query,
                    "limit" to limit?.toString(),
                ),
            )
            val response = api.getString(path)
            return api.json.decodeFromString(DerivativeSourceListResponse.serializer(), response)
        }

        suspend fun createLiveRoom(
            communityId: String,
            request: CreateLiveRoomRequest,
            altchaHeader: String? = null,
        ): LiveRoom {
            val body = api.json.encodeToString(CreateLiveRoomRequest.serializer(), request)
            val response = api.postString(
                "/communities/$communityId/live-rooms",
                body,
                headers = altchaHeader.toAltchaHeader(),
            )
            return api.json.decodeFromString(LiveRoom.serializer(), response)
        }

        suspend fun publishLiveRoom(
            communityId: String,
            request: PublishLiveRoomRequest,
            altchaHeader: String? = null,
        ): PublishLiveRoomResponse {
            val body = api.json.encodeToString(PublishLiveRoomRequest.serializer(), request)
            val response = api.postString(
                "/communities/$communityId/live-rooms/publish",
                body,
                headers = altchaHeader.toAltchaHeader(),
            )
            return api.json.decodeFromString(PublishLiveRoomResponse.serializer(), response)
        }

        suspend fun getLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
            val response = api.getString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}")
            return api.json.decodeFromString(LiveRoom.serializer(), response)
        }

        suspend fun getLiveRoomAccess(communityId: String, liveRoomId: String): LiveRoomAccessResponse {
            val response = api.getString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/access")
            return api.json.decodeFromString(LiveRoomAccessResponse.serializer(), response)
        }

        suspend fun viewerAttachLiveRoom(communityId: String, liveRoomId: String): LiveRoomViewerAttachResponse {
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/viewer_attach")
            return api.json.decodeFromString(LiveRoomViewerAttachResponse.serializer(), response)
        }

        suspend fun viewerRenewLiveRoom(
            communityId: String,
            liveRoomId: String,
            request: LiveRoomViewerRenewRequest,
        ): LiveRoomViewerAttachResponse {
            val body = api.json.encodeToString(LiveRoomViewerRenewRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/viewer_renew", body)
            return api.json.decodeFromString(LiveRoomViewerAttachResponse.serializer(), response)
        }

        suspend fun hostAttachLiveRoom(
            communityId: String,
            liveRoomId: String,
            request: LiveRoomAttachRequest = LiveRoomAttachRequest(),
        ): LiveRoomHostAttachResponse {
            val body = api.json.encodeToString(LiveRoomAttachRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/host_attach", body)
            return api.json.decodeFromString(LiveRoomHostAttachResponse.serializer(), response)
        }

        suspend fun guestAcceptLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/guest_accept")
            return api.json.decodeFromString(LiveRoom.serializer(), response)
        }

        suspend fun guestAttachLiveRoom(
            communityId: String,
            liveRoomId: String,
            request: LiveRoomAttachRequest = LiveRoomAttachRequest(),
        ): LiveRoomGuestAttachResponse {
            val body = api.json.encodeToString(LiveRoomAttachRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/guest_attach", body)
            return api.json.decodeFromString(LiveRoomGuestAttachResponse.serializer(), response)
        }

        suspend fun guestRevokeLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/guest_revoke")
            return api.json.decodeFromString(LiveRoom.serializer(), response)
        }

        suspend fun cancelLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/cancel")
            return api.json.decodeFromString(LiveRoom.serializer(), response)
        }

        suspend fun endLiveRoom(communityId: String, liveRoomId: String): LiveRoom {
            val response = api.postString("/communities/$communityId/live-rooms/${api.encodePathSegment(liveRoomId)}/end")
            return api.json.decodeFromString(LiveRoom.serializer(), response)
        }

        suspend fun getAsset(communityId: String, assetId: String): Asset {
            val response = api.getString("/communities/$communityId/assets/${api.encodePathSegment(assetId)}")
            return api.json.decodeFromString(Asset.serializer(), response)
        }

        suspend fun resolveAssetAccess(communityId: String, assetId: String): AssetAccessResponse {
            val response = api.getString("/communities/$communityId/assets/${api.encodePathSegment(assetId)}/access")
            return api.json.decodeFromString(AssetAccessResponse.serializer(), response)
        }

        suspend fun listListings(communityId: String): CommunityListingListResponse {
            val response = api.getString("/communities/$communityId/listings")
            return api.json.decodeFromString(CommunityListingListResponse.serializer(), response)
        }

        suspend fun createListing(communityId: String, request: CreateCommunityListingRequest): CommunityListing {
            val body = api.json.encodeToString(CreateCommunityListingRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/listings", body)
            return api.json.decodeFromString(CommunityListing.serializer(), response)
        }

        suspend fun getListing(communityId: String, listingId: String): CommunityListing {
            val response = api.getString("/communities/$communityId/listings/${api.encodePathSegment(listingId)}")
            return api.json.decodeFromString(CommunityListing.serializer(), response)
        }

        suspend fun updateListing(
            communityId: String,
            listingId: String,
            request: UpdateCommunityListingRequest,
        ): CommunityListing {
            val body = api.json.encodeToString(UpdateCommunityListingRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/listings/${api.encodePathSegment(listingId)}", body)
            return api.json.decodeFromString(CommunityListing.serializer(), response)
        }

        suspend fun listPurchases(communityId: String): CommunityPurchaseListResponse {
            val response = api.getString("/communities/$communityId/purchases")
            return api.json.decodeFromString(CommunityPurchaseListResponse.serializer(), response)
        }

        suspend fun getPurchase(communityId: String, purchaseId: String): CommunityPurchase {
            val response = api.getString("/communities/$communityId/purchases/${api.encodePathSegment(purchaseId)}")
            return api.json.decodeFromString(CommunityPurchase.serializer(), response)
        }

        suspend fun preflightPurchaseQuote(
            communityId: String,
            request: CommunityPurchaseQuotePreflightRequest,
        ): CommunityPurchaseQuotePreflight {
            val body = api.json.encodeToString(CommunityPurchaseQuotePreflightRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/purchase-quote-preflight", body)
            return api.json.decodeFromString(CommunityPurchaseQuotePreflight.serializer(), response)
        }

        suspend fun createPurchaseQuote(
            communityId: String,
            request: CommunityPurchaseQuoteRequest,
        ): CommunityPurchaseQuote {
            val body = api.json.encodeToString(CommunityPurchaseQuoteRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/purchase-quotes", body)
            return api.json.decodeFromString(CommunityPurchaseQuote.serializer(), response)
        }

        suspend fun settlePurchase(
            communityId: String,
            request: CommunityPurchaseSettlementRequest,
        ): CommunityPurchaseSettlement {
            val body = api.json.encodeToString(CommunityPurchaseSettlementRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/purchase-settlements", body)
            return api.json.decodeFromString(CommunityPurchaseSettlement.serializer(), response)
        }

        suspend fun failPurchase(
            communityId: String,
            request: CommunityPurchaseSettlementFailureRequest,
        ): CommunityPurchaseSettlementFailure {
            val body = api.json.encodeToString(CommunityPurchaseSettlementFailureRequest.serializer(), request)
            val response = api.postString("/communities/$communityId/fail-purchase-settlement", body)
            return api.json.decodeFromString(CommunityPurchaseSettlementFailure.serializer(), response)
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
            altchaHeader: String? = null,
        ) {
            val body = api.json.encodeToString(CreateCommentRequest.serializer(), request)
            api.postString(
                "/communities/$communityId/posts/$postId/comments",
                body,
                headers = altchaHeader.toAltchaHeader(),
            )
        }
    }

    class PublicCommunitiesEndpoints internal constructor(private val api: ApiClient) {
        suspend fun search(query: String, limit: Int? = null): PublicCommunitySearchResponse {
            val path = api.buildQueryPath(
                "/public-communities",
                listOf(
                    "query" to query,
                    "limit" to limit?.toString(),
                ),
            )
            val response = api.getString(path, requireAuth = false)
            return api.json.decodeFromString(PublicCommunitySearchResponse.serializer(), response)
        }

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

        suspend fun getLiveRoomAccess(communityId: String, liveRoomId: String): LiveRoomAccessResponse {
            val response = api.getString(
                "/public-communities/${api.encodePathSegment(communityId)}/live-rooms/${api.encodePathSegment(liveRoomId)}/access",
                requireAuth = false,
            )
            return api.json.decodeFromString(LiveRoomAccessResponse.serializer(), response)
        }

        suspend fun viewerAttachLiveRoom(communityId: String, liveRoomId: String): LiveRoomViewerAttachResponse {
            val response = api.postString(
                "/public-communities/${api.encodePathSegment(communityId)}/live-rooms/${api.encodePathSegment(liveRoomId)}/viewer_attach",
                requireAuth = false,
            )
            return api.json.decodeFromString(LiveRoomViewerAttachResponse.serializer(), response)
        }

        suspend fun viewerRenewLiveRoom(
            communityId: String,
            liveRoomId: String,
            request: LiveRoomViewerRenewRequest,
        ): LiveRoomViewerAttachResponse {
            val body = api.json.encodeToString(LiveRoomViewerRenewRequest.serializer(), request)
            val response = api.postString(
                "/public-communities/${api.encodePathSegment(communityId)}/live-rooms/${api.encodePathSegment(liveRoomId)}/viewer_renew",
                body,
                requireAuth = false,
            )
            return api.json.decodeFromString(LiveRoomViewerAttachResponse.serializer(), response)
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

        suspend fun createReply(
            commentId: String,
            request: CreateCommentRequest,
            altchaHeader: String? = null,
        ) {
            val body = api.json.encodeToString(CreateCommentRequest.serializer(), request)
            api.postString(
                "/comments/$commentId/replies",
                body,
                headers = altchaHeader.toAltchaHeader(),
            )
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

        suspend fun getPostableCommunities(): PostableCommunitiesResponse {
            val response = api.getString("/profiles/me/postable-communities")
            return api.json.decodeFromString(PostableCommunitiesResponse.serializer(), response)
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

        suspend fun getPublicByWallet(walletAddress: String): PublicProfileResolution {
            val response = api.getString(
                "/public-profiles/by-wallet/${api.encodePathSegment(walletAddress)}",
                requireAuth = false,
            )
            return api.json.decodeFromString(PublicProfileResolution.serializer(), response)
        }

        suspend fun updateMe(input: ProfileUpdateInput): Profile {
            val body = api.json.encodeToString(ProfileUpdateInput.serializer(), input)
            val response = api.postString("/profiles/me", body)
            return api.json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun publishXmtpInbox(inboxId: String): Profile {
            val body = api.json.encodeToString(XmtpInboxUpdateInput.serializer(), XmtpInboxUpdateInput(inboxId))
            val response = api.postString("/profiles/me/xmtp-inbox", body)
            return api.json.decodeFromString(Profile.serializer(), response)
        }

        suspend fun uploadMedia(
            kind: String,
            bytes: ByteArray,
            filename: String,
            mimeType: String,
        ): ProfileMediaUploadResponse {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("kind", kind)
                .addFormDataPart(
                    "file",
                    filename,
                    bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                )
                .build()
            val response = api.postMultipartString("/profile-media", body)
            return api.json.decodeFromString(ProfileMediaUploadResponse.serializer(), response)
        }

        suspend fun renameHandle(desiredLabel: String): RenameHandleResponse {
            val body = api.json.encodeToString(RenameHandleRequest.serializer(), RenameHandleRequest(desiredLabel))
            val response = api.postString("/profiles/me/global-handle/rename", body)
            return api.json.decodeFromString(RenameHandleResponse.serializer(), response)
        }
    }

    class NotificationsEndpoints internal constructor(private val api: ApiClient) {
        suspend fun getSummary(): NotificationSummary {
            val response = api.getString("/notifications/summary")
            return api.json.decodeFromString(NotificationSummary.serializer(), response)
        }

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
    @kotlinx.serialization.SerialName("avatar_source") val avatarSource: String? = null,
    @kotlinx.serialization.SerialName("cover_ref") val coverRef: String? = null,
    @kotlinx.serialization.SerialName("cover_source") val coverSource: String? = null,
    val bio: String? = null,
    @kotlinx.serialization.SerialName("bio_source") val bioSource: String? = null,
    @kotlinx.serialization.SerialName("preferred_locale") val preferredLocale: String? = null,
    @kotlinx.serialization.SerialName("display_verified_nationality_badge") val displayVerifiedNationalityBadge: Boolean? = null,
)

@kotlinx.serialization.Serializable
data class XmtpInboxUpdateInput(
    @kotlinx.serialization.SerialName("xmtp_inbox") val xmtpInbox: String,
)

@kotlinx.serialization.Serializable
data class ProfileMediaUploadResponse(
    val kind: String,
    @kotlinx.serialization.SerialName("media_ref") val mediaRef: String,
    @kotlinx.serialization.SerialName("ipfs_cid") val ipfsCid: String? = null,
    @kotlinx.serialization.SerialName("mime_type") val mimeType: String,
    @kotlinx.serialization.SerialName("size_bytes") val sizeBytes: Long,
    @kotlinx.serialization.SerialName("storage_bucket") val storageBucket: String,
    @kotlinx.serialization.SerialName("storage_object_key") val storageObjectKey: String,
)

@kotlinx.serialization.Serializable
data class CommunityMediaUploadResponse(
    val kind: String,
    @kotlinx.serialization.SerialName("media_ref") val mediaRef: String,
    @kotlinx.serialization.SerialName("ipfs_cid") val ipfsCid: String? = null,
    @kotlinx.serialization.SerialName("mime_type") val mimeType: String,
    @kotlinx.serialization.SerialName("size_bytes") val sizeBytes: Long,
    @kotlinx.serialization.SerialName("storage_bucket") val storageBucket: String,
    @kotlinx.serialization.SerialName("storage_object_key") val storageObjectKey: String,
)
