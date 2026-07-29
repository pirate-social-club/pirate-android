package sc.pirate.app.live

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.LiveRoomReplayDraft
import sc.pirate.app.api.model.PublishLiveRoomReplayDraftRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FeedSkeletons
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class ReplayDraftUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val publishing: Boolean = false,
    val draft: LiveRoomReplayDraft? = null,
    val title: String = "",
    val caption: String = "",
    val error: String? = null,
    val message: String? = null,
)

class ReplayDraftViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val repository get() = app.repositories.communityRepository
    private val _state = MutableStateFlow(ReplayDraftUiState())
    val state: StateFlow<ReplayDraftUiState> = _state.asStateFlow()
    private var communityId: String? = null
    private var liveRoomId: String? = null

    fun load(communityId: String, liveRoomId: String) {
        val normalizedCommunity = communityId.trim()
        val normalizedRoom = liveRoomId.trim()
        if (normalizedCommunity.isBlank() || normalizedRoom.isBlank()) return
        this.communityId = normalizedCommunity
        this.liveRoomId = normalizedRoom
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                applyDraft(repository.getLiveRoomReplayDraft(normalizedCommunity, normalizedRoom))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "Could not load the replay draft.",
                )
            }
        }
    }

    fun updateTitle(value: String) {
        if (value.length <= 140) _state.value = _state.value.copy(title = value, error = null)
    }

    fun updateCaption(value: String) {
        if (value.length <= 2000) _state.value = _state.value.copy(caption = value, error = null)
    }

    fun save() {
        mutate(publish = false)
    }

    fun publish() {
        mutate(publish = true)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun mutate(publish: Boolean) {
        val community = communityId ?: return
        val room = liveRoomId ?: return
        val current = _state.value
        val validation = validateReplayDraftForFreePublish(current.draft, current.title, current.caption)
        if (!validation.canPublish) {
            _state.value = current.copy(error = validation.message)
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(
                saving = !publish,
                publishing = publish,
                error = null,
                message = null,
            )
            if (!app.termsAcceptanceManager.requireForUgc()) {
                _state.value = _state.value.copy(saving = false, publishing = false)
                return@launch
            }
            try {
                val saved = repository.updateLiveRoomReplayDraft(
                    community,
                    room,
                    buildFreeReplayDraftUpdate(current.title, current.caption),
                )
                val result = if (publish) {
                    repository.publishLiveRoomReplayDraft(
                        community,
                        room,
                        PublishLiveRoomReplayDraftRequest(accessMode = "free"),
                    )
                } else {
                    saved
                }
                applyDraft(result, if (publish) "Replay published." else "Replay draft saved.")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    saving = false,
                    publishing = false,
                    error = error.message ?: if (publish) "Could not publish the replay." else "Could not save the replay draft.",
                )
            }
        }
    }

    private fun applyDraft(draft: LiveRoomReplayDraft, message: String? = null) {
        _state.value = ReplayDraftUiState(
            loading = false,
            draft = draft,
            title = draft.replayAsset?.title.orEmpty(),
            caption = draft.replayAsset?.caption.orEmpty(),
            message = message,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReplayDraftScreen(
    communityId: String,
    liveRoomId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ReplayDraftViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(communityId, liveRoomId) { viewModel.load(communityId, liveRoomId) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    val validation = validateReplayDraftForFreePublish(state.draft, state.title, state.caption)
    val editable = state.draft?.status == "ready" && state.draft?.replayStatus == "review_pending"

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = PirateTokens.colors.bgPage,
        topBar = {
            TopAppBar(
                title = { Text("Review recording", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PhosphorIcons.CaretLeft, contentDescription = "Back", tint = PirateTokens.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
    ) { padding ->
        when {
            state.loading -> FeedSkeletons(count = 3, modifier = Modifier.padding(padding).fillMaxSize())
            state.draft == null -> Column(Modifier.padding(padding).padding(16.dp)) {
                FormNote(message = state.error ?: "Replay draft unavailable.", tone = FormTone.Error)
                Spacer(Modifier.height(12.dp))
                PirateButton("Try again", onClick = { viewModel.load(communityId, liveRoomId) })
            }
            else -> ReplayDraftContent(
                state = state,
                editable = editable,
                validation = validation,
                onCaptionChange = viewModel::updateCaption,
                onPublish = viewModel::publish,
                onRefresh = { viewModel.load(communityId, liveRoomId) },
                onSave = viewModel::save,
                onTitleChange = viewModel::updateTitle,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ReplayDraftContent(
    state: ReplayDraftUiState,
    editable: Boolean,
    validation: ReplayDraftValidation,
    onTitleChange: (String) -> Unit,
    onCaptionChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSave: () -> Unit,
    onPublish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = requireNotNull(state.draft)
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { ReplayDraftStatusCard(draft) }
        if (editable || draft.status == "published") {
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("Replay title") },
                    enabled = editable && !state.saving && !state.publishing,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = state.caption,
                    onValueChange = onCaptionChange,
                    label = { Text("Caption") },
                    enabled = editable && !state.saving && !state.publishing,
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                StatusCard(
                    title = "Free replay",
                    description = "Paid replay publishing stays unavailable on Android until the Play billing policy is resolved.",
                    tone = StatusTone.Default,
                )
            }
            if (draft.replayAsset?.allocations?.isNotEmpty() == true) {
                item { ReplayAllocationSummary(draft) }
            }
        }
        state.error?.let { error -> item { FormNote(message = error, tone = FormTone.Error) } }
        if (editable && !validation.canPublish && state.error == null) {
            validation.message?.let { message ->
                item { FormNote(message = message, tone = FormTone.Warning) }
            }
        }
        if (!editable && draft.status != "published") {
            item {
                PirateButton(
                    text = "Refresh status",
                    onClick = onRefresh,
                    variant = ButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (editable) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PirateButton(
                        text = "Save draft",
                        onClick = onSave,
                        loading = state.saving,
                        enabled = validation.canPublish && !state.publishing,
                        variant = ButtonVariant.Outline,
                        modifier = Modifier.weight(1f),
                    )
                    PirateButton(
                        text = "Publish replay",
                        onClick = onPublish,
                        loading = state.publishing,
                        enabled = validation.canPublish && !state.saving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ReplayDraftStatusCard(draft: LiveRoomReplayDraft) {
    val (title, description, tone) = when (draft.status) {
        "ready" -> Triple("Recording ready", "Review the details and publish when ready.", StatusTone.Success)
        "published" -> Triple("Replay published", "The replay is available from the live post.", StatusTone.Success)
        "failed" -> Triple("Recording failed", draft.recording?.failureReason ?: "The recording could not be prepared.", StatusTone.Danger)
        "processing" -> Triple("Processing recording", "This can take a few minutes after the livestream ends.", StatusTone.Default)
        else -> Triple("No recording", "Recording was not enabled for this livestream.", StatusTone.Warning)
    }
    StatusCard(title = title, description = description, tone = tone)
}

@Composable
private fun ReplayAllocationSummary(draft: LiveRoomReplayDraft) {
    Surface(
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.md),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Royalty split", style = MaterialTheme.typography.titleSmall, color = PirateTokens.colors.textPrimary)
            draft.replayAsset?.allocations.orEmpty().forEach { allocation ->
                val recipient = allocation.participantUser ?: allocation.externalPartyRef ?: "Missing recipient"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = recipient,
                        style = MaterialTheme.typography.bodySmall,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${allocation.shareBps / 100.0}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = PirateTokens.colors.textPrimary,
                    )
                }
            }
        }
    }
}
