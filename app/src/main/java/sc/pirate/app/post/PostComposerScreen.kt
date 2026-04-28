package sc.pirate.app.post

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import java.util.UUID

data class PostComposerUiState(
    val postType: String = "text",
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val eligibility: JoinEligibility? = null,
    val loadingEligibility: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
    val createdPostId: String? = null,
)

class PostComposerViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val _state = MutableStateFlow(PostComposerUiState())
    val state: StateFlow<PostComposerUiState> = _state.asStateFlow()

    fun loadEligibility(communityId: String, hasSession: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingEligibility = true, error = null)
            if (!hasSession) {
                _state.value = _state.value.copy(
                    eligibility = null,
                    loadingEligibility = false,
                )
                return@launch
            }
            try {
                _state.value = _state.value.copy(
                    eligibility = communityRepository.getJoinEligibility(communityId),
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
        _state.value = _state.value.copy(title = title)
    }

    fun updateBody(body: String) {
        _state.value = _state.value.copy(body = body)
    }

    fun updateLinkUrl(linkUrl: String) {
        _state.value = _state.value.copy(linkUrl = linkUrl)
    }

    fun selectPostType(postType: String) {
        _state.value = _state.value.copy(
            postType = postType,
            error = null,
        )
    }

    fun submit(communityId: String) {
        val current = _state.value
        if (current.submitting) return
        if (current.postType == "text" && current.title.isBlank()) return
        if (current.postType == "link" && current.linkUrl.isBlank()) {
            _state.value = current.copy(error = "Enter a link before posting.")
            return
        }
        if (current.eligibility?.status != "already_joined") {
            _state.value = current.copy(error = "Join this community before posting.")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(submitting = true, error = null)
            try {
                val createdPost = communityRepository.createPost(
                    communityId,
                    CreatePostRequest(
                        idempotencyKey = UUID.randomUUID().toString(),
                        title = current.title.trim().ifBlank { null },
                        body = current.body.trim().ifBlank { null },
                        postType = current.postType,
                        linkUrl = current.linkUrl.trim().ifBlank { null },
                        identityMode = "public",
                        translationPolicy = "machine_allowed",
                        visibility = "public",
                    ),
                )
                _state.value = _state.value.copy(
                    submitting = false,
                    submitted = true,
                    createdPostId = createdPost.post.postId,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    submitting = false,
                    error = e.message ?: "Failed to create post",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerScreen(
    viewModel: PostComposerViewModel,
    communityId: String,
    hasSession: Boolean,
    onSignIn: () -> Unit,
    onPosted: (String) -> Unit,
    onOpenCommunity: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(communityId, hasSession) {
        viewModel.loadEligibility(communityId, hasSession)
    }

    LaunchedEffect(state.submitted) {
        val createdPostId = state.createdPostId
        if (state.submitted && createdPostId != null) {
            onPosted(createdPostId)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onBack,
        sheetState = sheetState,
        containerColor = PirateTokens.colors.bgPage,
        modifier = modifier,
    ) {
        val canPublish = hasSession && state.eligibility?.status == "already_joined"
        val hasDraft = when (state.postType) {
            "link" -> state.linkUrl.isNotBlank()
            else -> state.title.isNotBlank()
        }
        val submitLabel = if (hasSession && !canPublish) "Open community" else "Post"
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Create post",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PirateTokens.colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onBack) {
                    Icon(
                        PhosphorIcons.CaretLeft,
                        contentDescription = "Close",
                        tint = PirateTokens.colors.textPrimary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            when {
                state.loadingEligibility -> {
                    StatusCard(
                        title = "Checking posting access",
                        description = "Loading community permissions.",
                        tone = StatusTone.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                hasSession && !canPublish -> {
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

            Text(
                text = "Post type",
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PirateButton(
                    text = if (state.postType == "text") "Text selected" else "Text",
                    onClick = { viewModel.selectPostType("text") },
                    enabled = state.postType != "text" && !state.submitting,
                    modifier = Modifier.weight(1f),
                )
                PirateButton(
                    text = if (state.postType == "link") "Link selected" else "Link",
                    onClick = { viewModel.selectPostType("link") },
                    enabled = state.postType != "link" && !state.submitting,
                    modifier = Modifier.weight(1f),
                )
            }

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

            if (state.postType == "link") {
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

            OutlinedTextField(
                value = state.body,
                onValueChange = viewModel::updateBody,
                label = { Text(if (state.postType == "link") "Comment" else "Body") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxLines = 12,
                enabled = !state.submitting,
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                FormNote(message = state.error!!, tone = sc.pirate.app.ui.FormTone.Error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            PirateButton(
                text = submitLabel,
                onClick = {
                    when {
                        !hasSession -> onSignIn()
                        !canPublish -> onOpenCommunity()
                        else -> viewModel.submit(communityId)
                    }
                },
                loading = state.submitting,
                enabled = when {
                    state.loadingEligibility -> false
                    hasSession && !canPublish -> true
                    else -> hasDraft
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
