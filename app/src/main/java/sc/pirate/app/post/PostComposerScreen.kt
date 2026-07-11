package sc.pirate.app.post

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sc.pirate.app.api.PoWGate
import sc.pirate.app.api.model.CreateLiveRoomRequest
import sc.pirate.app.api.model.CreateSongArtifactBundleRequest
import sc.pirate.app.api.model.CreateSongArtifactUploadRequest
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.LiveRoom
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.PostMediaRef
import sc.pirate.app.api.model.PublishLiveRoomRequest
import sc.pirate.app.api.model.SongArtifactBundle
import sc.pirate.app.api.model.SongArtifactUpload
import sc.pirate.app.api.model.SongArtifactUploadRef
import sc.pirate.app.api.model.SongPreviewWindow
import sc.pirate.app.shared.buildDefaultUserAvatarSrc
import sc.pirate.app.shared.formatCommunityRouteLabel
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.VoteControl
import java.util.UUID

data class PostComposerUiState(
    val draftIdempotencyKey: String = UUID.randomUUID().toString(),
    val postType: PostComposerMode = PostComposerMode.Text,
    val step: PostComposerStep = PostComposerStep.Write,
    val selectedCommunityId: String? = null,
    val selectedCommunityName: String? = null,
    val selectedCommunityRouteSlug: String? = null,
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val live: LiveComposerState = LiveComposerState(),
    val song: SongComposerState = SongComposerState(),
    val liveCoverUri: Uri? = null,
    val mediaUri: Uri? = null,
    val mediaLabel: String = "",
    val mediaMimeType: String? = null,
    val mediaSizeBytes: Long? = null,
    val videoUpstreamAssetRefs: List<String> = emptyList(),
    val songPrimaryAudioUri: Uri? = null,
    val songCoverUri: Uri? = null,
    val songCanvasVideoUri: Uri? = null,
    val songInstrumentalAudioUri: Uri? = null,
    val songVocalAudioUri: Uri? = null,
    val eligibility: JoinEligibility? = null,
    val viewerUserId: String? = null,
    val publicHandle: String? = null,
    val publicAvatarRef: String? = null,
    val identityMode: PostComposerIdentityMode = PostComposerIdentityMode.Public,
    val allowAnonymousIdentity: Boolean = false,
    val anonymousIdentityScope: String = "community_stable",
    val hasCommunityPostingRole: Boolean = false,
    val loadingEligibility: Boolean = false,
    val submitting: Boolean = false,
    val solvingProofOfWork: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val createdPostId: String? = null,
)

enum class PostComposerIdentityMode(val apiValue: String) {
    Public("public"),
    Anonymous("anonymous"),
}

class PostComposerViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val profileRepository get() = app.repositories.profileRepository
    private val powGate by lazy { PoWGate(app.apiClient) }
    private val _state = MutableStateFlow(PostComposerUiState())
    val state: StateFlow<PostComposerUiState> = _state.asStateFlow()

    fun configureInitialCommunity(communityId: String?) {
        val id = communityId?.trim()?.takeIf { it.isNotBlank() }
        val current = _state.value
        if (id == null || current.selectedCommunityId == id) return
        val knownCommunity = app.knownCommunitiesStore.getRecent().firstOrNull { it.communityId == id }
        _state.value = current.copy(
            selectedCommunityId = id,
            selectedCommunityName = knownCommunity?.displayName,
            selectedCommunityRouteSlug = knownCommunity?.routeSlug,
            eligibility = null,
            hasCommunityPostingRole = true,
            loadingEligibility = false,
            error = null,
        )
    }

    fun requireCommunity() {
        _state.value = _state.value.copy(error = "Choose a community before posting.")
    }

    fun loadEligibility(communityId: String?, hasSession: Boolean) {
        viewModelScope.launch {
            val selectedCommunityId = communityId?.trim()?.takeIf { it.isNotBlank() }
            if (selectedCommunityId == null) {
                _state.value = _state.value.copy(
                    eligibility = null,
                    hasCommunityPostingRole = false,
                    loadingEligibility = false,
                    error = null,
                )
                return@launch
            }
            _state.value = _state.value.copy(loadingEligibility = true, error = null)
            if (!hasSession) {
                _state.value = _state.value.copy(
                    eligibility = null,
                    hasCommunityPostingRole = false,
                    loadingEligibility = false,
                )
                return@launch
            }
            try {
                val eligibility = communityRepository.getJoinEligibility(selectedCommunityId)
                val preview = runCatching { communityRepository.getPreview(selectedCommunityId) }.getOrNull()
                val profile = runCatching { profileRepository.getMe() }.getOrNull()
                val viewerUserId = profile?.userId
                val allowAnonymous = preview?.allowAnonymousIdentity == true && _state.value.postType.anonymousEligible()
                val nextIdentityMode = if (allowAnonymous) {
                    _state.value.identityMode
                } else {
                    PostComposerIdentityMode.Public
                }
                _state.value = _state.value.copy(
                    selectedCommunityName = preview?.displayName ?: _state.value.selectedCommunityName,
                    selectedCommunityRouteSlug = preview?.routeSlug ?: _state.value.selectedCommunityRouteSlug,
                    eligibility = eligibility,
                    viewerUserId = viewerUserId,
                    publicHandle = profile?.displayPirateHandle() ?: _state.value.publicHandle,
                    publicAvatarRef = profile?.avatarRef ?: _state.value.publicAvatarRef,
                    identityMode = nextIdentityMode,
                    allowAnonymousIdentity = allowAnonymous,
                    anonymousIdentityScope = preview?.anonymousIdentityScope?.takeIf { it.isNotBlank() } ?: "community_stable",
                    hasCommunityPostingRole = viewerHasCommunityPostingRole(viewerUserId, preview),
                    loadingEligibility = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingEligibility = false,
                    error = e.message ?: "Could not load posting eligibility",
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _state.value = _state.value.copy(title = normalizePostComposerTitleInput(title))
    }

    fun updateBody(body: String) {
        _state.value = _state.value.copy(body = body)
    }

    fun updateLinkUrl(linkUrl: String) {
        _state.value = _state.value.copy(linkUrl = linkUrl)
    }

    fun updateLive(live: LiveComposerState) {
        _state.value = _state.value.copy(live = live, error = null)
    }

    fun updateSong(song: SongComposerState) {
        _state.value = _state.value.copy(song = song, error = null)
    }

    fun selectMedia(uri: Uri?) {
        val label = uri?.displayName().orEmpty()
        _state.value = _state.value.copy(
            mediaUri = uri,
            mediaLabel = label,
            mediaMimeType = uri?.let { app.contentResolver.getType(it) },
            mediaSizeBytes = uri?.sizeBytes(),
            error = null,
        )
    }

    fun setDerivativeRefs(refs: List<String>) {
        val normalized = refs.mapNotNull { it.trim().takeIf { value -> value.isNotBlank() } }.distinct()
        val current = _state.value
        _state.value = when (current.postType) {
            PostComposerMode.Video -> current.copy(videoUpstreamAssetRefs = normalized, error = null)
            else -> current.copy(
                song = current.song.copy(upstreamAssetRefs = normalized),
                error = null,
            )
        }
    }

    private fun setSolvingProofOfWork(solving: Boolean) {
        _state.value = _state.value.copy(solvingProofOfWork = solving)
    }

    fun updateLiveRoomKind(roomKind: LiveRoomKind) {
        val currentLive = _state.value.live
        val allocations = when (roomKind) {
            LiveRoomKind.Solo -> listOf(LivePerformerAllocationState(role = "host", sharePct = 100))
            LiveRoomKind.Duet -> if (currentLive.roomKind == LiveRoomKind.Duet) {
                currentLive.performerAllocations
            } else {
                listOf(
                    LivePerformerAllocationState(role = "host", sharePct = 50),
                    LivePerformerAllocationState(role = "guest", sharePct = 50),
                )
            }
        }
        updateLive(currentLive.copy(roomKind = roomKind, performerAllocations = allocations))
    }

    fun selectLiveCover(uri: Uri?, label: String?) {
        _state.value = _state.value.copy(
            liveCoverUri = uri,
            live = _state.value.live.copy(coverLabel = label.orEmpty()),
            error = null,
        )
    }

    fun selectSongPrimaryAudio(uri: Uri?, label: String?) {
        _state.value = _state.value.copy(
            songPrimaryAudioUri = uri,
            song = _state.value.song.copy(primaryAudioLabel = label.orEmpty()),
            error = null,
        )
    }

    fun selectSongCover(uri: Uri?, label: String?) {
        _state.value = _state.value.copy(
            songCoverUri = uri,
            song = _state.value.song.copy(coverLabel = label.orEmpty()),
            error = null,
        )
    }

    fun selectSongCanvasVideo(uri: Uri?, label: String?) {
        _state.value = _state.value.copy(
            songCanvasVideoUri = uri,
            song = _state.value.song.copy(canvasVideoLabel = label.orEmpty()),
            error = null,
        )
    }

    fun selectSongInstrumentalAudio(uri: Uri?, label: String?) {
        _state.value = _state.value.copy(
            songInstrumentalAudioUri = uri,
            song = _state.value.song.copy(instrumentalAudioLabel = label.orEmpty()),
            error = null,
        )
    }

    fun selectSongVocalAudio(uri: Uri?, label: String?) {
        _state.value = _state.value.copy(
            songVocalAudioUri = uri,
            song = _state.value.song.copy(vocalAudioLabel = label.orEmpty()),
            error = null,
        )
    }

    fun addLiveSetlistItem() {
        val live = _state.value.live
        updateLive(live.copy(setlistItems = live.setlistItems + LiveSetlistItemState()))
    }

    fun updateLiveSetlistItem(index: Int, item: LiveSetlistItemState) {
        val live = _state.value.live
        if (index !in live.setlistItems.indices) return
        updateLive(live.copy(setlistItems = live.setlistItems.mapIndexed { i, current -> if (i == index) item else current }))
    }

    fun removeLiveSetlistItem(index: Int) {
        val live = _state.value.live
        if (index !in live.setlistItems.indices) return
        updateLive(live.copy(setlistItems = live.setlistItems.filterIndexed { i, _ -> i != index }))
    }

    fun selectPostType(postType: PostComposerMode) {
        val identityMode = if (postType.anonymousEligible()) {
            _state.value.identityMode
        } else {
            PostComposerIdentityMode.Public
        }
        _state.value = _state.value.copy(
            postType = postType,
            identityMode = identityMode,
            mediaUri = if (postType == _state.value.postType) _state.value.mediaUri else null,
            mediaLabel = if (postType == _state.value.postType) _state.value.mediaLabel else "",
            mediaMimeType = if (postType == _state.value.postType) _state.value.mediaMimeType else null,
            mediaSizeBytes = if (postType == _state.value.postType) _state.value.mediaSizeBytes else null,
            error = null,
        )
    }

    fun selectIdentityMode(identityMode: PostComposerIdentityMode) {
        val current = _state.value
        if (identityMode == PostComposerIdentityMode.Anonymous && !current.canUseAnonymousIdentity()) return
        _state.value = current.copy(identityMode = identityMode, error = null)
    }

    fun goToNextStep() {
        val current = _state.value
        val draftValidation = validatePostComposerDraft(
            mode = current.postType,
            title = current.title,
            linkUrl = current.linkUrl,
            live = current.live,
            song = current.song,
            hasMedia = current.mediaUri != null,
        )
        _state.value = current.copy(
            step = getNextPostComposerStep(current.step, draftValidation),
            error = null,
        )
    }

    fun goToPreviousStep() {
        val current = _state.value
        val previous = getPreviousPostComposerStep(current.step) ?: return
        _state.value = current.copy(step = previous, error = null)
    }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val communityId = current.selectedCommunityId?.trim()?.takeIf { it.isNotBlank() }
        val draftValidation = validatePostComposerDraft(
            mode = current.postType,
            title = current.title,
            linkUrl = current.linkUrl,
            live = current.live,
            song = current.song,
            hasMedia = current.mediaUri != null,
        )
        if (!draftValidation.canSubmit) {
            _state.value = current.copy(error = draftValidation.errorMessage)
            return
        }
        if (communityId == null) {
            _state.value = current.copy(error = "Choose a community before posting.")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(submitting = true, error = null)
            try {
                val createdPostId = when (current.postType) {
                    PostComposerMode.Live -> submitLiveRoom(communityId, current)
                    PostComposerMode.Song -> submitSong(communityId, current)
                    PostComposerMode.Image,
                    PostComposerMode.Video,
                    PostComposerMode.Link,
                    PostComposerMode.Text -> {
                        val mediaRefs = if (current.postType == PostComposerMode.Image || current.postType == PostComposerMode.Video) {
                            listOf(uploadPostMediaRef(communityId, current))
                        } else {
                            null
                        }
                        val request = if (current.postType == PostComposerMode.Video && mediaRefs != null) {
                            buildVideoPostRequest(
                                anonymousScope = if (current.resolvedIdentityMode() == PostComposerIdentityMode.Anonymous) {
                                    current.anonymousIdentityScope
                                } else {
                                    null
                                },
                                idempotencyKey = current.draftIdempotencyKey,
                                title = current.title,
                                caption = current.body,
                                identityMode = current.resolvedIdentityMode().apiValue,
                                mediaRefs = mediaRefs,
                                upstreamAssetRefs = current.videoUpstreamAssetRefs,
                            )
                        } else {
                            CreatePostRequest(
                                idempotencyKey = current.draftIdempotencyKey,
                                title = current.title.trim().ifBlank { null },
                                body = if (current.postType == PostComposerMode.Image || current.postType == PostComposerMode.Video) {
                                    null
                                } else {
                                    current.body.trim().ifBlank { null }
                                },
                                caption = if (current.postType == PostComposerMode.Image || current.postType == PostComposerMode.Video) {
                                    current.body.trim().ifBlank { null }
                                } else {
                                    null
                                },
                                postType = current.postType.apiValue,
                                linkUrl = if (current.postType == PostComposerMode.Link) {
                                    normalizeHttpUrl(current.linkUrl)
                                } else {
                                    null
                                },
                                mediaRefs = mediaRefs,
                                identityMode = current.resolvedIdentityMode().apiValue,
                                anonymousScope = if (current.resolvedIdentityMode() == PostComposerIdentityMode.Anonymous) {
                                    current.anonymousIdentityScope
                                } else {
                                    null
                                },
                                translationPolicy = "machine_allowed",
                                visibility = "public",
                            )
                        }
                        val createdPost = createPostWithProofOfWork(
                            communityId,
                            request,
                        )
                        createdPost.post.postId
                    }
                }
                _state.value = _state.value.copy(
                    submitting = false,
                    submitted = true,
                    createdPostId = createdPostId,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(
                    submitting = false,
                    solvingProofOfWork = false,
                    error = e.message ?: "Failed to create post",
                )
            }
        }
    }

    private suspend fun createPostWithProofOfWork(
        communityId: String,
        request: CreatePostRequest,
    ): LocalizedPostResponse =
        powGate.execute(
            scope = "post_create",
            action = communityAltchaAction(communityId),
            onSolvingProofOfWorkChanged = ::setSolvingProofOfWork,
        ) { altchaHeader ->
            communityRepository.createPost(
                communityId = communityId,
                request = request,
                altchaHeader = altchaHeader,
            )
        }

    private suspend fun submitLiveRoom(communityId: String, current: PostComposerUiState): String {
        val hostUserId = current.viewerUserId?.trim()?.takeIf { it.isNotBlank() }
            ?: profileRepository.getMe().userId.trim().takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in before creating a live room.")
        val resolvedGuestUserId = if (current.live.roomKind == LiveRoomKind.Duet) {
            resolveLiveRoomGuestUserId(current.live.guestUserId)
        } else {
            null
        }
        val coverRef = current.liveCoverUri?.uploadCommunityMedia("post_image")
        val roomRequest = buildLiveRoomRequest(
            coverRef = coverRef,
            description = current.body,
            hostUserId = hostUserId,
            live = current.live,
            resolvedGuestUserId = resolvedGuestUserId,
            title = current.title,
        )
        val room = createLiveRoomWithProofOfWork(communityId, current, roomRequest)
        return room.anchorPost
    }

    private suspend fun createLiveRoomWithProofOfWork(
        communityId: String,
        current: PostComposerUiState,
        roomRequest: CreateLiveRoomRequest,
    ): LiveRoom =
        powGate.execute(
            scope = "post_create",
            action = communityAltchaAction(communityId),
            onSolvingProofOfWorkChanged = ::setSolvingProofOfWork,
        ) { altchaHeader ->
            if (current.live.accessMode == LiveAccessMode.Paid) {
                val listingRequest = buildLiveRoomListingRequest(
                    liveRoomId = null,
                    paidLiveRoomPriceUsd = current.live.paidPriceUsd,
                    regionalPricingEnabled = current.live.regionalPricingEnabled,
                ) ?: throw IllegalStateException("Enter a valid ticket price.")
                communityRepository.publishLiveRoom(
                    communityId = communityId,
                    request = PublishLiveRoomRequest(room = roomRequest, listing = listingRequest),
                    altchaHeader = altchaHeader,
                ).room
            } else {
                communityRepository.createLiveRoom(
                    communityId = communityId,
                    request = roomRequest,
                    altchaHeader = altchaHeader,
                )
            }
        }

    private suspend fun submitSong(communityId: String, current: PostComposerUiState): String {
        val song = current.song
        val isLocked = song.paidSongPriceUsd.isNotBlank()
        val pendingBundleId = song.pendingBundleId?.trim()?.takeIf { it.isNotBlank() }
        val primaryAudio = if (pendingBundleId == null) {
            uploadSongArtifact(communityId, "primary_audio", current.songPrimaryAudioUri)
                ?: throw IllegalStateException("Primary audio is required.")
        } else {
            null
        }
        var bundle = if (pendingBundleId == null) {
            val coverArt = uploadSongArtifact(communityId, "cover_art", current.songCoverUri)
            val canvasVideo = uploadSongArtifact(communityId, "canvas_video", current.songCanvasVideoUri)
            val instrumentalAudio = uploadSongArtifact(communityId, "instrumental_audio", current.songInstrumentalAudioUri)
            val vocalAudio = uploadSongArtifact(communityId, "vocal_audio", current.songVocalAudioUri)
            communityRepository.createSongArtifactBundle(
                communityId,
                CreateSongArtifactBundleRequest(
                    primaryAudio = SongArtifactUploadRef(primaryAudio!!.id),
                    title = song.songTitle.trim(),
                    lyrics = song.lyrics.trim(),
                    geniusAnnotationsUrl = song.geniusAnnotationsUrl.trim().ifBlank { null },
                    coverArt = coverArt?.let { SongArtifactUploadRef(it.id) },
                    previewWindow = if (isLocked) {
                        SongPreviewWindow(
                            startMs = parseSongPreviewStartMs(song.previewStartSeconds) ?: 0L,
                            durationMs = 30_000L,
                        )
                    } else {
                        null
                    },
                    canvasVideo = canvasVideo?.let { SongArtifactUploadRef(it.id) },
                    instrumentalAudio = instrumentalAudio?.let { SongArtifactUploadRef(it.id) },
                    vocalAudio = vocalAudio?.let { SongArtifactUploadRef(it.id) },
                ),
            )
        } else {
            communityRepository.getSongArtifactBundle(communityId, pendingBundleId)
        }
        if (songBundleRequiresSourceReference(bundle)) {
            throw IllegalStateException("Your uploaded song is too similar to an existing song.")
        }
        if (isLocked) {
            bundle = waitForSongPreview(communityId, bundle.id)
        }
        val postResponse = createPostWithProofOfWork(
            communityId,
            buildSongPostRequest(
                bundleId = bundle.id,
                caption = current.body,
                idempotencyKey = current.draftIdempotencyKey,
                song = song,
                title = current.title,
                visibility = "public",
            ),
        )
        if (isLocked) {
            val assetId = postResponse.post.assetId
                ?: throw IllegalStateException("Song published but asset not created.")
            val listingRequest = buildSongListingRequest(
                assetId = assetId,
                paidSongPriceUsd = song.paidSongPriceUsd,
                pricingPolicyRegionalPricingEnabled = true,
                regionalPricingEnabled = song.regionalPricingEnabled,
            ) ?: throw IllegalStateException("Invalid song price.")
            communityRepository.createListing(communityId, listingRequest)
        }
        return postResponse.post.postId
    }

    private suspend fun uploadPostMediaRef(communityId: String, current: PostComposerUiState): PostMediaRef {
        val uri = current.mediaUri ?: throw IllegalStateException(
            if (current.postType == PostComposerMode.Video) "Choose a video before posting." else "Choose an image before posting.",
        )
        return if (current.postType == PostComposerMode.Video) {
            val upload = uploadVideoArtifact(communityId, uri)
            PostMediaRef(
                storageRef = upload.storageRef,
                mimeType = upload.mimeType.ifBlank { current.mediaMimeType ?: "video/mp4" },
                sizeBytes = upload.sizeBytes ?: current.mediaSizeBytes,
                contentHash = upload.contentHash,
            )
        } else {
            PostMediaRef(
                storageRef = uri.uploadCommunityMedia("post_image"),
                mimeType = current.mediaMimeType ?: app.contentResolver.getType(uri) ?: "image/jpeg",
                sizeBytes = current.mediaSizeBytes,
            )
        }
    }

    private suspend fun uploadVideoArtifact(
        communityId: String,
        uri: Uri,
    ): SongArtifactUpload {
        val contentResolver = app.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "video/mp4"
        val name = uri.displayName()
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read selected video: $name")
        val intent = communityRepository.createArtifactUpload(
            communityId,
            CreateSongArtifactUploadRequest(
                artifactKind = "primary_video",
                mimeType = mimeType,
                filename = name,
                sizeBytes = bytes.size.toLong(),
            ),
        )
        return communityRepository.uploadArtifactContent(communityId, intent.id, bytes)
    }

    private suspend fun uploadSongArtifact(
        communityId: String,
        kind: String,
        uri: Uri?,
    ): SongArtifactUpload? {
        if (uri == null) return null
        val contentResolver = app.contentResolver
        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
        val name = uri.displayName()
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read selected file: $name")
        val intent = communityRepository.createArtifactUpload(
            communityId,
            CreateSongArtifactUploadRequest(
                artifactKind = kind,
                mimeType = mimeType,
                filename = name,
                sizeBytes = bytes.size.toLong(),
            ),
        )
        return communityRepository.uploadArtifactContent(communityId, intent.id, bytes)
    }

    private suspend fun waitForSongPreview(communityId: String, bundleId: String): SongArtifactBundle {
        repeat(30) {
            val bundle = communityRepository.getSongArtifactBundle(communityId, bundleId)
            if (bundle.previewStatus == "completed") return bundle
            if (bundle.previewStatus == "failed") {
                throw IllegalStateException(bundle.previewError ?: "Song preview generation failed.")
            }
            delay(2_000L)
        }
        throw IllegalStateException("Song preview is still processing. Try again in a moment.")
    }

    private suspend fun resolveLiveRoomGuestUserId(value: String): String? {
        val rawGuestUserId = value.trim()
        if (rawGuestUserId.isBlank()) return null
        if (isPirateUserId(rawGuestUserId)) return rawGuestUserId
        val handleLabel = normalizeLiveRoomGuestHandle(rawGuestUserId)
        if (handleLabel.isBlank()) return null
        val resolution = try {
            profileRepository.getPublicByHandle(handleLabel)
        } catch (_: Exception) {
            throw IllegalStateException("Could not find a Pirate user for \"$rawGuestUserId\".")
        }
        val resolvedUserId = resolution.profile.userId.trim()
        if (!isPirateUserId(resolvedUserId)) {
            throw IllegalStateException("The cohost \"$rawGuestUserId\" did not resolve to a Pirate user id.")
        }
        return resolvedUserId
    }

    private fun Uri.displayName(): String {
        app.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    return cursor.getString(index)
                }
            }
        }
        return lastPathSegment ?: "Selected image"
    }

    private fun Uri.sizeBytes(): Long? {
        app.contentResolver.query(this, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) {
                    return cursor.getLong(index)
                }
            }
        }
        return null
    }

    private suspend fun Uri.uploadCommunityMedia(kind: String): String {
        val contentResolver = app.contentResolver
        val mimeType = contentResolver.getType(this) ?: "image/jpeg"
        val name = displayName()
        val bytes = contentResolver.openInputStream(this)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not read selected image.")
        return communityRepository.uploadMedia(kind, bytes, name, mimeType)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerScreen(
    viewModel: PostComposerViewModel,
    communityId: String?,
    hasSession: Boolean,
    onSignIn: () -> Unit,
    onOpenDerivativeSourceSearch: (List<String>) -> Unit,
    onPosted: (String) -> Unit,
    onOpenCommunity: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(communityId) {
        viewModel.configureInitialCommunity(communityId)
    }

    LaunchedEffect(state.selectedCommunityId, hasSession) {
        viewModel.loadEligibility(state.selectedCommunityId, hasSession)
    }

    LaunchedEffect(state.submitted) {
        val createdPostId = state.createdPostId
        if (state.submitted && createdPostId != null) {
            onPosted(createdPostId)
        }
    }

    val hasSelectedCommunity = !state.selectedCommunityId.isNullOrBlank()
    val canPublish = hasSession && hasSelectedCommunity
    val draftValidation = validatePostComposerDraft(
        mode = state.postType,
        title = state.title,
        linkUrl = state.linkUrl,
        live = state.live,
        song = state.song,
        hasMedia = state.mediaUri != null,
    )
    val canAdvanceStep = canAdvancePostComposerStep(state.step, draftValidation)
    val bottomActionLabel = when (state.step) {
        PostComposerStep.Write,
        PostComposerStep.Settings -> "Next"
        PostComposerStep.Publish -> "Publish"
    }
    val pageTitle = when (state.step) {
        PostComposerStep.Write -> "Create post"
        PostComposerStep.Settings -> "Post settings"
        PostComposerStep.Publish -> "Preview"
    }

    BackHandler(enabled = state.step != PostComposerStep.Write) {
        viewModel.goToPreviousStep()
    }

    Scaffold(
        modifier = modifier,
        containerColor = PirateTokens.colors.bgPage,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = pageTitle,
                        color = PirateTokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.step == PostComposerStep.Write) {
                                onBack()
                            } else {
                                viewModel.goToPreviousStep()
                            }
                        },
                    ) {
                        Icon(
                            if (state.step == PostComposerStep.Write) PhosphorIcons.X else PhosphorIcons.CaretLeft,
                            contentDescription = if (state.step == PostComposerStep.Write) "Close" else "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (state.step != PostComposerStep.Publish) {
                        TextButton(
                            enabled = canAdvanceStep && !state.submitting,
                            onClick = viewModel::goToNextStep,
                        ) {
                            Text("Next")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PirateTokens.colors.bgPage,
                ),
            )
        },
        bottomBar = {
            if (state.step == PostComposerStep.Publish) {
                Surface(
                    color = PirateTokens.colors.bgPage,
                    border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
                ) {
                    PirateButton(
                        onClick = {
                            when {
                                !hasSession -> onSignIn()
                                !hasSelectedCommunity -> viewModel.requireCommunity()
                                else -> viewModel.submit()
                            }
                        },
                        loading = state.submitting,
                        text = if (state.solvingProofOfWork) {
                            "Verifying access..."
                        } else {
                            bottomActionLabel
                        },
                        enabled = canAdvanceStep && !state.submitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            item {
                when (state.step) {
                    PostComposerStep.Write -> {
                        PostComposerWriteContent(
                            onOpenDerivativeSourceSearch = onOpenDerivativeSourceSearch,
                            state = state,
                            viewModel = viewModel,
                        )
                    }

                    PostComposerStep.Settings -> {
                        PostComposerSettingsContent(
                            state = state,
                            onIdentityModeChange = viewModel::selectIdentityMode,
                        )
                    }

                    PostComposerStep.Publish -> {
                        PostComposerPublishContent(
                            canPublish = canPublish,
                            hasSession = hasSession,
                            state = state,
                        )
                    }
                }
            }

            if (state.error != null) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    FormNote(message = state.error!!, tone = FormTone.Error)
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PostComposerWriteContent(
    onOpenDerivativeSourceSearch: (List<String>) -> Unit,
    state: PostComposerUiState,
    viewModel: PostComposerViewModel,
) {
    val liveCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectLiveCover(uri, uri?.lastPathSegment)
    }
    val songPrimaryAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectSongPrimaryAudio(uri, uri?.lastPathSegment)
    }
    val songCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectSongCover(uri, uri?.lastPathSegment)
    }
    val songCanvasVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectSongCanvasVideo(uri, uri?.lastPathSegment)
    }
    val songInstrumentalAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectSongInstrumentalAudio(uri, uri?.lastPathSegment)
    }
    val songVocalAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectSongVocalAudio(uri, uri?.lastPathSegment)
    }
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectMedia(uri)
    }

    ComposerTabs(
        selected = state.postType,
        onSelect = viewModel::selectPostType,
        enabled = !state.submitting,
    )
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = state.title,
        onValueChange = viewModel::updateTitle,
        label = { Text("Title") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !state.submitting,
    )

    Spacer(modifier = Modifier.height(12.dp))

    if (state.postType == PostComposerMode.Link) {
        OutlinedTextField(
            value = state.linkUrl,
            onValueChange = viewModel::updateLinkUrl,
            label = { Text("Link URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.submitting,
        )

        Spacer(modifier = Modifier.height(12.dp))
    }

    Text(
        text = when (state.postType) {
            PostComposerMode.Image -> "Caption"
            PostComposerMode.Link -> "Comment"
            PostComposerMode.Live -> "Description"
            PostComposerMode.Song -> "Caption"
            PostComposerMode.Text -> "Body"
            PostComposerMode.Video -> "Caption"
        },
        style = MaterialTheme.typography.labelLarge,
        color = PirateTokens.colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    if (state.postType == PostComposerMode.Live) {
        OutlinedTextField(
            value = state.body,
            onValueChange = viewModel::updateBody,
            placeholder = { Text("Describe the live room") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 6,
            enabled = !state.submitting,
        )
        Spacer(modifier = Modifier.height(16.dp))
        LiveComposerFields(
            live = state.live,
            coverLabel = state.live.coverLabel,
            enabled = !state.submitting,
            onAddSetlistItem = viewModel::addLiveSetlistItem,
            onCoverSelect = { liveCoverPicker.launch("image/*") },
            onLiveChange = viewModel::updateLive,
            onRemoveSetlistItem = viewModel::removeLiveSetlistItem,
            onRoomKindChange = viewModel::updateLiveRoomKind,
            onSetlistItemChange = viewModel::updateLiveSetlistItem,
        )
    } else if (state.postType == PostComposerMode.Song) {
        BodyEditorChrome {
            OutlinedTextField(
                value = state.body,
                onValueChange = viewModel::updateBody,
                placeholder = { Text("Add a caption") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                maxLines = 12,
                enabled = !state.submitting,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        SongComposerFields(
            enabled = !state.submitting,
            onCanvasVideoSelect = { songCanvasVideoPicker.launch("video/*") },
            onChange = viewModel::updateSong,
            onCoverSelect = { songCoverPicker.launch("image/*") },
            onInstrumentalAudioSelect = { songInstrumentalAudioPicker.launch("audio/*") },
            onOpenSourceSearch = { onOpenDerivativeSourceSearch(state.song.upstreamAssetRefs) },
            onPrimaryAudioSelect = { songPrimaryAudioPicker.launch("audio/*") },
            onVocalAudioSelect = { songVocalAudioPicker.launch("audio/*") },
            song = state.song,
        )
    } else if (state.postType == PostComposerMode.Image || state.postType == PostComposerMode.Video) {
        MediaComposerFields(
            enabled = !state.submitting,
            mediaLabel = state.mediaLabel,
            mediaUri = state.mediaUri,
            mode = state.postType,
            onMediaSelect = {
                mediaPicker.launch(if (state.postType == PostComposerMode.Video) "video/*" else "image/*")
            },
            onOpenVideoSourceSearch = { onOpenDerivativeSourceSearch(state.videoUpstreamAssetRefs) },
            videoUpstreamAssetRefs = state.videoUpstreamAssetRefs,
        )
        Spacer(modifier = Modifier.height(16.dp))
        BodyEditorChrome {
            OutlinedTextField(
                value = state.body,
                onValueChange = viewModel::updateBody,
                placeholder = {
                    Text(if (state.postType == PostComposerMode.Image) "Add an image caption" else "Add a video caption")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                maxLines = 12,
                enabled = !state.submitting,
            )
        }
    } else {
        BodyEditorChrome {
            OutlinedTextField(
                value = state.body,
                onValueChange = viewModel::updateBody,
                placeholder = {
                    Text(if (state.postType == PostComposerMode.Link) "Add context" else "Write your post")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                maxLines = 12,
                enabled = !state.submitting,
            )
        }
    }
}

@Composable
private fun MediaComposerFields(
    enabled: Boolean,
    mediaLabel: String,
    mediaUri: Uri?,
    mode: PostComposerMode,
    onMediaSelect: () -> Unit,
    onOpenVideoSourceSearch: () -> Unit,
    videoUpstreamAssetRefs: List<String>,
) {
    PirateButton(
        text = when {
            mediaLabel.isNotBlank() && mode == PostComposerMode.Image -> "Replace image"
            mediaLabel.isNotBlank() -> "Replace video"
            mode == PostComposerMode.Image -> "Upload image"
            else -> "Upload video"
        },
        onClick = onMediaSelect,
        enabled = enabled,
        variant = ButtonVariant.Outline,
        modifier = Modifier.fillMaxWidth(),
    )
    if (mode == PostComposerMode.Image && mediaUri != null) {
        Spacer(modifier = Modifier.height(8.dp))
        AsyncImage(
            model = mediaUri,
            contentDescription = "Selected image",
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .border(BorderStroke(1.dp, PirateTokens.colors.borderSoft), RoundedCornerShape(8.dp)),
        )
    }
    if (mediaLabel.isNotBlank()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = mediaLabel,
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (mode == PostComposerMode.Video) {
        Spacer(modifier = Modifier.height(12.dp))
        PirateButton(
            text = if (videoUpstreamAssetRefs.isEmpty()) {
                "Uses song"
            } else {
                "Source songs (${videoUpstreamAssetRefs.size})"
            },
            onClick = onOpenVideoSourceSearch,
            enabled = enabled,
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LiveComposerFields(
    live: LiveComposerState,
    coverLabel: String,
    enabled: Boolean,
    onAddSetlistItem: () -> Unit,
    onCoverSelect: () -> Unit,
    onLiveChange: (LiveComposerState) -> Unit,
    onRemoveSetlistItem: (Int) -> Unit,
    onRoomKindChange: (LiveRoomKind) -> Unit,
    onSetlistItemChange: (Int, LiveSetlistItemState) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PirateButton(
            text = if (coverLabel.isBlank()) "Upload event cover" else "Replace event cover",
            onClick = onCoverSelect,
            enabled = enabled,
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        if (coverLabel.isNotBlank()) {
            Text(
                text = coverLabel,
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }

        LiveChoiceSection(title = "Room type") {
            LiveChoiceChip(
                label = "Solo",
                selected = live.roomKind == LiveRoomKind.Solo,
                enabled = enabled,
                onClick = { onRoomKindChange(LiveRoomKind.Solo) },
            )
            LiveChoiceChip(
                label = "Duet",
                selected = live.roomKind == LiveRoomKind.Duet,
                enabled = enabled,
                onClick = { onRoomKindChange(LiveRoomKind.Duet) },
            )
        }

        if (live.roomKind == LiveRoomKind.Duet) {
            OutlinedTextField(
                value = live.guestUserId,
                onValueChange = { onLiveChange(live.copy(guestUserId = it)) },
                label = { Text("Guest performer") },
                placeholder = { Text("@handle or usr_...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
        }

        LiveChoiceSection(title = "Access") {
            LiveChoiceChip("Free", live.accessMode == LiveAccessMode.Free, enabled) {
                onLiveChange(live.copy(accessMode = LiveAccessMode.Free))
            }
            LiveChoiceChip("Gated", live.accessMode == LiveAccessMode.Gated, enabled) {
                onLiveChange(live.copy(accessMode = LiveAccessMode.Gated))
            }
            LiveChoiceChip("Paid", live.accessMode == LiveAccessMode.Paid, enabled) {
                onLiveChange(live.copy(accessMode = LiveAccessMode.Paid))
            }
        }

        if (live.accessMode == LiveAccessMode.Paid) {
            OutlinedTextField(
                value = live.paidPriceUsd,
                onValueChange = { onLiveChange(live.copy(paidPriceUsd = it)) },
                label = { Text("Ticket price USD") },
                placeholder = { Text("5.00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
        }

        LiveChoiceSection(title = "Visibility") {
            LiveChoiceChip("Public", live.visibility == LiveVisibility.Public, enabled) {
                onLiveChange(live.copy(visibility = LiveVisibility.Public))
            }
            LiveChoiceChip("Unlisted", live.visibility == LiveVisibility.Unlisted, enabled) {
                onLiveChange(live.copy(visibility = LiveVisibility.Unlisted))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Checkbox(
                checked = live.scheduleForLater,
                onCheckedChange = { checked ->
                    onLiveChange(live.copy(scheduleForLater = checked))
                },
                enabled = enabled,
            )
            Text(
                text = "Schedule for later",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textPrimary,
            )
        }
        if (live.scheduleForLater) {
            OutlinedTextField(
                value = live.scheduleAt,
                onValueChange = { onLiveChange(live.copy(scheduleAt = it)) },
                label = { Text("Start time") },
                placeholder = { Text("2026-06-01T20:00") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
        }

        OutlinedTextField(
            value = live.storeUrl,
            onValueChange = { onLiveChange(live.copy(storeUrl = it)) },
            label = { Text("Store URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )
        OutlinedTextField(
            value = live.storeLabel,
            onValueChange = { onLiveChange(live.copy(storeLabel = it)) },
            label = { Text("Store label") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Setlist",
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
            )
            TextButton(onClick = onAddSetlistItem, enabled = enabled) {
                Text("Add song")
            }
        }
        if (live.setlistItems.isEmpty()) {
            Text(
                text = "No setlist songs yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        live.setlistItems.forEachIndexed { index, item ->
            LiveSetlistItemEditor(
                item = item,
                index = index,
                enabled = enabled,
                onChange = { onSetlistItemChange(index, it) },
                onRemove = { onRemoveSetlistItem(index) },
            )
        }
    }
}

@Composable
private fun SongComposerFields(
    enabled: Boolean,
    onCanvasVideoSelect: () -> Unit,
    onChange: (SongComposerState) -> Unit,
    onCoverSelect: () -> Unit,
    onInstrumentalAudioSelect: () -> Unit,
    onOpenSourceSearch: () -> Unit,
    onPrimaryAudioSelect: () -> Unit,
    onVocalAudioSelect: () -> Unit,
    song: SongComposerState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = song.songTitle,
            onValueChange = { onChange(song.copy(songTitle = normalizePostComposerTitleInput(it))) },
            label = { Text("Song title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )

        SongUploadButton(
            label = "Primary audio",
            selectedLabel = song.primaryAudioLabel,
            enabled = enabled,
            required = true,
            onClick = onPrimaryAudioSelect,
        )
        SongUploadButton(
            label = "Cover art",
            selectedLabel = song.coverLabel,
            enabled = enabled,
            required = false,
            onClick = onCoverSelect,
        )

        OutlinedTextField(
            value = song.lyrics,
            onValueChange = { onChange(song.copy(lyrics = it)) },
            label = { Text("Lyrics") },
            placeholder = { Text("Paste lyrics or leave blank for instrumental") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
            enabled = enabled,
        )
        OutlinedTextField(
            value = song.geniusAnnotationsUrl,
            onValueChange = { onChange(song.copy(geniusAnnotationsUrl = it)) },
            label = { Text("Genius annotations URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )

        LiveChoiceSection(title = "Song mode") {
            LiveChoiceChip("Original", song.songMode == SongMode.Original, enabled) {
                onChange(song.copy(songMode = SongMode.Original, upstreamAssetRefs = emptyList()))
            }
            LiveChoiceChip("Remix", song.songMode == SongMode.Remix, enabled) {
                onChange(song.copy(songMode = SongMode.Remix))
            }
        }
        if (song.songMode == SongMode.Remix) {
            PirateButton(
                text = if (song.upstreamAssetRefs.isEmpty()) {
                    "Select source track"
                } else {
                    "Source tracks (${song.upstreamAssetRefs.size})"
                },
                onClick = onOpenSourceSearch,
                enabled = enabled,
                variant = ButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LiveChoiceSection(title = "License") {
            LiveChoiceChip("Non-commercial", song.licensePreset == AssetLicensePreset.NonCommercial, enabled) {
                onChange(song.copy(licensePreset = AssetLicensePreset.NonCommercial, commercialRevSharePct = ""))
            }
            LiveChoiceChip("Commercial", song.licensePreset == AssetLicensePreset.CommercialUse, enabled) {
                onChange(song.copy(licensePreset = AssetLicensePreset.CommercialUse, commercialRevSharePct = ""))
            }
        }
        LiveChoiceSection(title = "Remix license") {
            LiveChoiceChip("Commercial remix", song.licensePreset == AssetLicensePreset.CommercialRemix, enabled) {
                onChange(song.copy(licensePreset = AssetLicensePreset.CommercialRemix))
            }
        }
        if (song.licensePreset == AssetLicensePreset.CommercialRemix) {
            OutlinedTextField(
                value = song.commercialRevSharePct,
                onValueChange = { onChange(song.copy(commercialRevSharePct = it)) },
                label = { Text("Remix revenue share %") },
                placeholder = { Text("25") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
        }

        OutlinedTextField(
            value = song.paidSongPriceUsd,
            onValueChange = { onChange(song.copy(paidSongPriceUsd = it)) },
            label = { Text("Unlock price USD") },
            placeholder = { Text("Leave blank for free") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = enabled,
        )
        if (song.paidSongPriceUsd.isNotBlank()) {
            OutlinedTextField(
                value = song.previewStartSeconds,
                onValueChange = { onChange(song.copy(previewStartSeconds = it)) },
                label = { Text("Preview start seconds") },
                placeholder = { Text("0") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(
                    checked = song.regionalPricingEnabled,
                    onCheckedChange = { checked -> onChange(song.copy(regionalPricingEnabled = checked)) },
                    enabled = enabled,
                )
                Text(
                    text = "Regional pricing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textPrimary,
                )
            }
        }

        Text(
            text = "Stems",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        SongUploadButton(
            label = "Instrumental",
            selectedLabel = song.instrumentalAudioLabel,
            enabled = enabled,
            required = false,
            onClick = onInstrumentalAudioSelect,
        )
        SongUploadButton(
            label = "Vocal",
            selectedLabel = song.vocalAudioLabel,
            enabled = enabled,
            required = false,
            onClick = onVocalAudioSelect,
        )
        SongUploadButton(
            label = "Canvas video",
            selectedLabel = song.canvasVideoLabel,
            enabled = enabled,
            required = false,
            onClick = onCanvasVideoSelect,
        )
    }
}

@Composable
private fun SongUploadButton(
    label: String,
    selectedLabel: String,
    enabled: Boolean,
    required: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PirateButton(
            text = when {
                selectedLabel.isBlank() && required -> "Upload $label"
                selectedLabel.isBlank() -> "Add $label"
                else -> "Replace $label"
            },
            onClick = onClick,
            enabled = enabled,
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        if (selectedLabel.isNotBlank()) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun LiveSetlistItemEditor(
    item: LiveSetlistItemState,
    index: Int,
    enabled: Boolean,
    onChange: (LiveSetlistItemState) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.lg),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Song ${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textPrimary,
                )
                TextButton(onClick = onRemove, enabled = enabled) {
                    Text("Remove")
                }
            }
            OutlinedTextField(
                value = item.titleText,
                onValueChange = { onChange(item.copy(titleText = it)) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
            OutlinedTextField(
                value = item.artistText,
                onValueChange = { onChange(item.copy(artistText = it)) },
                label = { Text("Artist") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = enabled,
            )
            LiveChoiceSection(title = "Performance") {
                LiveChoiceChip("Original", item.performanceKind == LiveSetlistItemKind.Original, enabled) {
                    onChange(item.copy(performanceKind = LiveSetlistItemKind.Original))
                }
                LiveChoiceChip("Cover", item.performanceKind == LiveSetlistItemKind.Cover, enabled) {
                    onChange(item.copy(performanceKind = LiveSetlistItemKind.Cover))
                }
                LiveChoiceChip("Unknown", item.performanceKind == LiveSetlistItemKind.Unknown, enabled) {
                    onChange(item.copy(performanceKind = LiveSetlistItemKind.Unknown))
                }
            }
        }
    }
}

@Composable
private fun LiveChoiceSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = PirateTokens.colors.textPrimary,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun LiveChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        color = if (selected) PirateTokens.colors.accentDanger.copy(alpha = 0.12f) else PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(PirateTokens.radius.full),
        border = BorderStroke(
            1.dp,
            if (selected) PirateTokens.colors.accentDanger else PirateTokens.colors.borderSoft,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) PirateTokens.colors.accentDanger else PirateTokens.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BodyEditorChrome(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .border(1.dp, PirateTokens.colors.borderSoft, RoundedCornerShape(PirateTokens.radius.lg)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf("B", "I", "S", "''", "#").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = PirateTokens.colors.textSecondary,
                )
            }
            Icon(
                PhosphorIcons.Link,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun PostComposerSettingsContent(
    state: PostComposerUiState,
    onIdentityModeChange: (PostComposerIdentityMode) -> Unit,
) {
    Text(
        text = "Post as",
        style = MaterialTheme.typography.titleMedium,
        color = PirateTokens.colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(12.dp))
    ComposerSettingsOptionRow(
        checked = state.resolvedIdentityMode() == PostComposerIdentityMode.Public,
        title = state.publicAuthorLabel(),
        description = "Post from your public identity",
        icon = {
            AuthorPreviewAvatar(
                label = state.publicAuthorLabel(),
                avatarSrc = state.publicAvatarSrc(),
                anonymous = false,
            )
        },
        onClick = { onIdentityModeChange(PostComposerIdentityMode.Public) },
    )
    if (state.canUseAnonymousIdentity()) {
        Spacer(modifier = Modifier.height(8.dp))
        ComposerSettingsOptionRow(
            checked = state.resolvedIdentityMode() == PostComposerIdentityMode.Anonymous,
            title = state.anonymousAuthorLabel(),
            description = "Same identity across this community",
            icon = {
                AuthorPreviewAvatar(
                    label = state.anonymousAuthorLabel(),
                    avatarSrc = null,
                    anonymous = true,
                )
            },
            onClick = { onIdentityModeChange(PostComposerIdentityMode.Anonymous) },
        )
    }
    Spacer(modifier = Modifier.height(28.dp))
    Text(
        text = "Visibility",
        style = MaterialTheme.typography.titleMedium,
        color = PirateTokens.colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(12.dp))
    ComposerSettingsOptionRow(
        checked = true,
        title = "Public",
        description = "Visible to everyone",
        icon = {
            Icon(
                PhosphorIcons.Globe,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
                modifier = Modifier.size(24.dp),
            )
        },
        onClick = {},
    )
}

private fun PostComposerUiState.communityLabel(): String =
    selectedCommunityId?.takeIf { it.isNotBlank() }?.let { communityId ->
        formatCommunityRouteLabel(
            communityId = communityId,
            routeSlug = selectedCommunityRouteSlug,
        )
    } ?: selectedCommunityName?.takeIf { it.isNotBlank() }
        ?: "Community"

private fun communityAltchaAction(communityId: String): String {
    val trimmed = communityId.trim()
    val publicCommunityId = if (trimmed.startsWith("com_")) trimmed else "com_$trimmed"
    return "community:$publicCommunityId"
}

@Composable
private fun ComposerSettingsOptionRow(
    checked: Boolean,
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (checked) PirateTokens.colors.surfaceSubtle else PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (checked) 1.5.dp else 1.dp,
            color = if (checked) PirateTokens.colors.accentBrand else PirateTokens.colors.borderSoft,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = PirateTokens.colors.bgPage,
                shape = RoundedCornerShape(PirateTokens.radius.full),
                border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PirateTokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (checked) {
                Icon(
                    PhosphorIcons.CheckCircle,
                    contentDescription = null,
                    tint = PirateTokens.colors.accentBrand,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun PostComposerPublishContent(
    canPublish: Boolean,
    hasSession: Boolean,
    state: PostComposerUiState,
) {
    PostComposerPreviewCard(
        state = state,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.solvingProofOfWork) {
        Spacer(modifier = Modifier.height(12.dp))
        FormNote(
            message = "Verifying community access...",
            tone = FormTone.Warning,
        )
    } else if (!hasSession) {
        Spacer(modifier = Modifier.height(12.dp))
        FormNote(
            message = "Sign in before publishing this draft.",
            tone = FormTone.Error,
        )
    } else if (!canPublish) {
        Spacer(modifier = Modifier.height(12.dp))
        FormNote(
            message = "Finish the required fields before publishing.",
            tone = FormTone.Error,
        )
    }
}

@Composable
private fun PostComposerPreviewCard(
    state: PostComposerUiState,
    modifier: Modifier = Modifier,
) {
    val title = state.previewTitle()
    val body = state.body.trim()
    Surface(
        modifier = modifier,
        color = PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PostPreviewHeader(state = state)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (body.isNotBlank() && body != title) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PostPreviewAttachment(state = state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoteControl(
                    score = 0,
                    viewerVote = 0,
                    enabled = false,
                    onVote = {},
                )
                PreviewCommentPill(count = 0)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PostPreviewHeader(state: PostComposerUiState) {
    val authorLabel = state.previewAuthorLabel()
    val avatarSrc = state.previewAuthorAvatarSrc()
    val anonymous = state.resolvedIdentityMode() == PostComposerIdentityMode.Anonymous
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AuthorPreviewAvatar(
            label = authorLabel,
            avatarSrc = avatarSrc,
            anonymous = anonymous,
            modifier = Modifier.size(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = authorLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "· Public",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 1,
                )
            }
            Text(
                text = state.communityLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = {}, enabled = false) {
            Icon(
                imageVector = PhosphorIcons.DotsThree,
                contentDescription = "Post options",
                tint = PirateTokens.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PostPreviewAttachment(state: PostComposerUiState) {
    when (state.postType) {
        PostComposerMode.Text -> Unit
        PostComposerMode.Link -> {
            val url = normalizeHttpUrl(state.linkUrl) ?: state.linkUrl.trim()
            if (url.isNotBlank()) {
                PreviewAttachmentCard(
                    icon = PhosphorIcons.Link,
                    title = url,
                    subtitle = "Link",
                )
            }
        }
        PostComposerMode.Image -> PreviewAttachmentCard(
            icon = PhosphorIcons.Image,
            title = state.mediaLabel.ifBlank { "Image post" },
            subtitle = "Image attachment",
        )
        PostComposerMode.Video -> PreviewAttachmentCard(
            icon = PhosphorIcons.VideoCamera,
            title = state.mediaLabel.ifBlank { "Video post" },
            subtitle = "Video attachment",
        )
        PostComposerMode.Song -> {
            val paid = state.song.paidSongPriceUsd.trim()
            PreviewAttachmentCard(
                icon = PhosphorIcons.MusicNotes,
                title = state.song.songTitle.trim().ifBlank { "Untitled song" },
                subtitle = listOf(
                    state.song.songMode.apiValue,
                    state.song.licensePreset.apiValue,
                    if (paid.isBlank()) "public" else "locked $paid USD",
                ).joinToString(" / "),
            )
        }
        PostComposerMode.Live -> {
            val schedule = if (state.live.scheduleForLater && state.live.scheduleAt.isNotBlank()) {
                " / starts ${state.live.scheduleAt}"
            } else {
                ""
            }
            val paid = if (state.live.accessMode == LiveAccessMode.Paid && state.live.paidPriceUsd.isNotBlank()) {
                " / ticket ${state.live.paidPriceUsd.trim()} USD"
            } else {
                ""
            }
            PreviewAttachmentCard(
                icon = PhosphorIcons.Microphone,
                title = "Live room",
                subtitle = "${state.live.roomKind.apiValue} / ${state.live.accessMode.apiValue} / ${state.live.visibility.apiValue}$schedule$paid",
            )
        }
    }
}

@Composable
private fun PreviewAttachmentCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = PirateTokens.colors.bgPage,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PirateTokens.colors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PreviewCommentPill(count: Int) {
    Surface(
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.full),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PhosphorIcons.ChatCircle,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
        }
    }
}

private fun PostComposerUiState.previewTitle(): String =
    when (postType) {
        PostComposerMode.Song -> song.songTitle.trim().ifBlank { title.trim().ifBlank { "Untitled song" } }
        else -> title.trim().ifBlank { "Untitled" }
    }

@Composable
private fun AuthorPreviewAvatar(
    label: String,
    avatarSrc: String?,
    anonymous: Boolean,
    modifier: Modifier = Modifier.size(24.dp),
) {
    Surface(
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.full),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (avatarSrc != null && !anonymous) {
                AsyncImage(
                    model = avatarSrc,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (anonymous) {
                Icon(
                    imageVector = PhosphorIcons.UserCircle,
                    contentDescription = null,
                    tint = PirateTokens.colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = label.firstOrNull()?.uppercase() ?: "P",
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textPrimary,
                )
            }
        }
    }
}

private fun PostComposerUiState.canUseAnonymousIdentity(): Boolean =
    allowAnonymousIdentity && postType.anonymousEligible()

private fun PostComposerUiState.resolvedIdentityMode(): PostComposerIdentityMode =
    if (identityMode == PostComposerIdentityMode.Anonymous && canUseAnonymousIdentity()) {
        PostComposerIdentityMode.Anonymous
    } else {
        PostComposerIdentityMode.Public
    }

private fun PostComposerUiState.publicAuthorLabel(): String =
    publicHandle?.takeIf { it.isNotBlank() } ?: "name.pirate"

private fun PostComposerUiState.anonymousAuthorLabel(): String =
    "Pseudonym"

private fun PostComposerUiState.previewAuthorLabel(): String =
    if (resolvedIdentityMode() == PostComposerIdentityMode.Anonymous) anonymousAuthorLabel() else publicAuthorLabel()

private fun PostComposerUiState.publicAvatarSrc(): String? =
    resolvePublicMediaSrc(publicAvatarRef) ?: buildDefaultUserAvatarSrc(publicAuthorLabel()).takeIf { it.isNotBlank() }

private fun PostComposerUiState.previewAuthorAvatarSrc(): String? =
    if (resolvedIdentityMode() == PostComposerIdentityMode.Anonymous) null else publicAvatarSrc()

private fun PostComposerMode.anonymousEligible(): Boolean =
    this == PostComposerMode.Text ||
        this == PostComposerMode.Image ||
        this == PostComposerMode.Video ||
        this == PostComposerMode.Link

private fun sc.pirate.app.api.model.Profile.displayPirateHandle(): String {
    val label = primaryPublicHandle?.label ?: globalHandle?.label.orEmpty()
    if (label.isBlank()) return ""
    return if (label.contains(".")) label else "$label.pirate"
}

@Composable
private fun PostingToLabel(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            PhosphorIcons.Users,
            contentDescription = null,
            tint = PirateTokens.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Posting to $label",
            style = MaterialTheme.typography.labelLarge,
            color = PirateTokens.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CommunityContextPill(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.full),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                PhosphorIcons.Users,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ComposerTabs(
    selected: PostComposerMode,
    onSelect: (PostComposerMode) -> Unit,
    enabled: Boolean,
) {
    val tabs = listOf(
        ComposerTab(PostComposerMode.Text, PhosphorIcons.TextT, "Text", enabled = true),
        ComposerTab(PostComposerMode.Image, PhosphorIcons.Image, "Image", enabled = true),
        ComposerTab(PostComposerMode.Video, PhosphorIcons.VideoCamera, "Video", enabled = true),
        ComposerTab(PostComposerMode.Link, PhosphorIcons.Link, "Link", enabled = true),
        ComposerTab(PostComposerMode.Song, PhosphorIcons.MusicNotes, "Song", enabled = true),
        ComposerTab(PostComposerMode.Live, PhosphorIcons.Microphone, "Live", enabled = true),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val active = selected == tab.id
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled && tab.enabled && tab.id != null) { tab.id?.let(onSelect) }
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = when {
                        active -> PirateTokens.colors.accentDanger
                        tab.enabled -> PirateTokens.colors.textSecondary.copy(alpha = 0.72f)
                        else -> PirateTokens.colors.textSecondary.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(22.dp),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(if (active) PirateTokens.colors.accentDanger else androidx.compose.ui.graphics.Color.Transparent),
                )
            }
        }
    }
}

private data class ComposerTab(
    val id: PostComposerMode?,
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean,
)

private fun viewerHasCommunityPostingRole(
    viewerUserId: String?,
    community: CommunityPreview?,
): Boolean {
    val userId = viewerUserId?.trim()?.takeIf { it.isNotBlank() } ?: return false
    if (community == null) return false
    if (sameUserId(userId, community.owner?.user)) return true
    return community.moderators.any { moderator ->
        sameUserId(userId, moderator.user) &&
            moderator.role in setOf("owner", "admin", "moderator")
    }
}

private fun sameUserId(left: String?, right: String?): Boolean {
    val leftId = left?.trim()?.takeIf { it.isNotBlank() } ?: return false
    val rightId = right?.trim()?.takeIf { it.isNotBlank() } ?: return false
    return leftId == rightId
}
