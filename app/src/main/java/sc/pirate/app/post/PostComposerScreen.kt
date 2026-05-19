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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.PublishLiveRoomRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import java.util.UUID

data class PostComposerUiState(
    val postType: PostComposerMode = PostComposerMode.Text,
    val step: PostComposerStep = PostComposerStep.Write,
    val selectedCommunityId: String? = null,
    val selectedCommunityName: String? = null,
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val live: LiveComposerState = LiveComposerState(),
    val liveCoverUri: Uri? = null,
    val eligibility: JoinEligibility? = null,
    val viewerUserId: String? = null,
    val hasCommunityPostingRole: Boolean = false,
    val loadingEligibility: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val createdPostId: String? = null,
)

class PostComposerViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val profileRepository get() = app.repositories.profileRepository
    private val _state = MutableStateFlow(PostComposerUiState())
    val state: StateFlow<PostComposerUiState> = _state.asStateFlow()

    fun configureInitialCommunity(communityId: String?) {
        val id = communityId?.trim()?.takeIf { it.isNotBlank() }
        val current = _state.value
        if (id == null || current.selectedCommunityId == id) return
        _state.value = current.copy(
            selectedCommunityId = id,
            selectedCommunityName = null,
            eligibility = null,
            hasCommunityPostingRole = false,
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
                val viewerUserId = runCatching { profileRepository.getMe().userId }.getOrNull()
                _state.value = _state.value.copy(
                    selectedCommunityName = preview?.displayName ?: _state.value.selectedCommunityName,
                    eligibility = eligibility,
                    viewerUserId = viewerUserId,
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
        _state.value = _state.value.copy(
            postType = postType,
            error = null,
        )
    }

    fun goToNextStep() {
        val current = _state.value
        val draftValidation = validatePostComposerDraft(
            mode = current.postType,
            title = current.title,
            linkUrl = current.linkUrl,
            live = current.live,
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
        )
        if (!draftValidation.canSubmit) {
            _state.value = current.copy(error = draftValidation.errorMessage)
            return
        }
        if (communityId == null) {
            _state.value = current.copy(error = "Choose a community before posting.")
            return
        }
        if (current.eligibility?.status != "already_joined" && !current.hasCommunityPostingRole) {
            _state.value = current.copy(error = "Join this community before posting.")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(submitting = true, error = null)
            try {
                val createdPostId = if (current.postType == PostComposerMode.Live) {
                    submitLiveRoom(communityId, current)
                } else {
                    val createdPost = communityRepository.createPost(
                        communityId,
                        CreatePostRequest(
                            idempotencyKey = UUID.randomUUID().toString(),
                            title = current.title.trim().ifBlank { null },
                            body = current.body.trim().ifBlank { null },
                            postType = current.postType.apiValue,
                            linkUrl = if (current.postType == PostComposerMode.Link) {
                                normalizeHttpUrl(current.linkUrl)
                            } else {
                                null
                            },
                            identityMode = "public",
                            translationPolicy = "machine_allowed",
                            visibility = "public",
                        ),
                    )
                    createdPost.post.postId
                }
                _state.value = _state.value.copy(
                    submitting = false,
                    submitted = true,
                    createdPostId = createdPostId,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    submitting = false,
                    error = e.message ?: "Failed to create post",
                )
            }
        }
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
        val room = if (current.live.accessMode == LiveAccessMode.Paid) {
            val listingRequest = buildLiveRoomListingRequest(
                liveRoomId = null,
                paidLiveRoomPriceUsd = current.live.paidPriceUsd,
                regionalPricingEnabled = current.live.regionalPricingEnabled,
            ) ?: throw IllegalStateException("Enter a valid ticket price.")
            communityRepository.publishLiveRoom(
                communityId,
                PublishLiveRoomRequest(room = roomRequest, listing = listingRequest),
            ).room
        } else {
            communityRepository.createLiveRoom(communityId, roomRequest)
        }
        return room.anchorPost
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
    val canPublish = hasSession && hasSelectedCommunity &&
        (state.eligibility?.status == "already_joined" || state.hasCommunityPostingRole)
    val draftValidation = validatePostComposerDraft(
        mode = state.postType,
        title = state.title,
        linkUrl = state.linkUrl,
        live = state.live,
    )
    val eligibilityReady = !hasSelectedCommunity || !state.loadingEligibility
    val canAdvanceStep = eligibilityReady && canAdvancePostComposerStep(state.step, draftValidation)
    val bottomActionLabel = when (state.step) {
        PostComposerStep.Write,
        PostComposerStep.Settings -> "Next"
        PostComposerStep.Publish -> "Post"
    }
    val pageTitle = when (state.step) {
        PostComposerStep.Write -> "Create post"
        PostComposerStep.Settings -> "Post settings"
        PostComposerStep.Publish -> "Publish post"
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
                        text = bottomActionLabel,
                        onClick = {
                            when {
                                !hasSession -> onSignIn()
                                !hasSelectedCommunity -> viewModel.requireCommunity()
                                !canPublish -> state.selectedCommunityId?.let(onOpenCommunity) ?: viewModel.requireCommunity()
                                else -> viewModel.submit()
                            }
                        },
                        loading = state.submitting,
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
                            canPublish = canPublish,
                            communityLabel = state.communityLabel(),
                            hasSelectedCommunity = hasSelectedCommunity,
                            hasSession = hasSession,
                            onOpenCommunity = {
                                state.selectedCommunityId?.let(onOpenCommunity) ?: viewModel.requireCommunity()
                            },
                            state = state,
                            viewModel = viewModel,
                        )
                    }

                    PostComposerStep.Settings -> {
                        PostComposerSettingsContent(
                            communityLabel = state.communityLabel(),
                            onOpenCommunity = {
                                state.selectedCommunityId?.let(onOpenCommunity) ?: viewModel.requireCommunity()
                            },
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
    canPublish: Boolean,
    communityLabel: String,
    hasSelectedCommunity: Boolean,
    hasSession: Boolean,
    onOpenCommunity: () -> Unit,
    state: PostComposerUiState,
    viewModel: PostComposerViewModel,
) {
    val liveCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.selectLiveCover(uri, uri?.lastPathSegment)
    }

    CommunityContextPill(
        label = communityLabel,
        onClick = onOpenCommunity,
    )
    Spacer(modifier = Modifier.height(16.dp))

    ComposerTabs(
        selected = state.postType,
        onSelect = viewModel::selectPostType,
        enabled = !state.submitting,
    )
    Spacer(modifier = Modifier.height(12.dp))

    when {
        hasSelectedCommunity && state.loadingEligibility -> {
            StatusCard(
                title = "Checking posting access",
                description = "Loading community permissions.",
                tone = StatusTone.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        hasSession && hasSelectedCommunity && !canPublish -> {
            StatusCard(
                title = "Join before publishing",
                description = "You can keep editing this draft. Publishing is available after you join.",
                tone = StatusTone.Warning,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PirateButton(
                text = "Open community",
                onClick = onOpenCommunity,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

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
            PostComposerMode.Link -> "Comment"
            PostComposerMode.Live -> "Description"
            PostComposerMode.Text -> "Body"
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
    communityLabel: String,
    onOpenCommunity: () -> Unit,
) {
    CommunityContextPill(
        label = communityLabel,
        onClick = onOpenCommunity,
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Visibility",
        style = MaterialTheme.typography.labelLarge,
        color = PirateTokens.colors.textPrimary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = "Public",
        onValueChange = {},
        enabled = false,
        leadingIcon = {
            Icon(PhosphorIcons.Globe, contentDescription = null)
        },
        trailingIcon = {
            Icon(PhosphorIcons.CaretDown, contentDescription = null)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun PostComposerUiState.communityLabel(): String =
    selectedCommunityName?.takeIf { it.isNotBlank() }
        ?: selectedCommunityId?.takeIf { it.isNotBlank() }?.let { "c/$it" }
        ?: "Community"

@Composable
private fun PostComposerPublishContent(
    canPublish: Boolean,
    hasSession: Boolean,
    state: PostComposerUiState,
) {
    val hasSelectedCommunity = !state.selectedCommunityId.isNullOrBlank()
    StatusCard(
        title = when {
            hasSession && canPublish -> "Ready to publish"
            hasSession && !hasSelectedCommunity -> "Choose a community"
            else -> "Publishing needs access"
        },
        description = if (hasSession && canPublish) {
            "Publish this draft when ready."
        } else if (!hasSession) {
            "Sign in before publishing this draft."
        } else if (!hasSelectedCommunity) {
            "Choose a community before publishing this draft."
        } else {
            "Open the community to complete posting access."
        },
        tone = if (hasSession && canPublish) StatusTone.Default else StatusTone.Warning,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = state.title.trim().ifBlank { "Untitled" },
        style = MaterialTheme.typography.titleLarge,
        color = PirateTokens.colors.textPrimary,
    )
    if (state.postType == PostComposerMode.Link) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = normalizeHttpUrl(state.linkUrl) ?: state.linkUrl.trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
    } else if (state.postType == PostComposerMode.Live) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = listOf(
                state.live.roomKind.apiValue,
                state.live.accessMode.apiValue,
                state.live.visibility.apiValue,
            ).joinToString(" / "),
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        if (state.live.scheduleForLater && state.live.scheduleAt.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Starts ${state.live.scheduleAt}",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        if (state.live.accessMode == LiveAccessMode.Paid) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ticket ${state.live.paidPriceUsd.trim()} USD",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
    val body = state.body.trim()
    if (body.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = PirateTokens.colors.textPrimary,
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
        ComposerTab(PostComposerMode.Link, PhosphorIcons.Link, "Link", enabled = true),
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
