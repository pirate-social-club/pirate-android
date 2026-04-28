package sc.pirate.app.post

import android.app.Application
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
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

    val canPublish = hasSession && state.eligibility?.status == "already_joined"
    val hasDraft = when (state.postType) {
        "link" -> state.linkUrl.isNotBlank()
        "text" -> state.title.isNotBlank()
        else -> false
    }
    val canAttemptPost = !state.loadingEligibility && hasDraft

    Scaffold(
        modifier = modifier,
        containerColor = PirateTokens.colors.bgPage,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        PhosphorIcons.X,
                        contentDescription = "Close",
                        tint = PirateTokens.colors.textPrimary,
                    )
                }
                Text(
                    text = "Create post",
                    style = MaterialTheme.typography.titleMedium,
                    color = PirateTokens.colors.textPrimary,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        },
        bottomBar = {
            Surface(
                color = PirateTokens.colors.bgPage,
                border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
            ) {
                PirateButton(
                    text = "Post",
                    onClick = {
                        when {
                            !hasSession -> onSignIn()
                            !canPublish -> onOpenCommunity()
                            else -> viewModel.submit(communityId)
                        }
                    },
                    loading = state.submitting,
                    enabled = canAttemptPost && !state.submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            CommunitySelectorPill(
                label = if (communityId.isBlank()) "Choose a community" else "c/$communityId",
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

            Text(
                text = if (state.postType == "link") "Comment" else "Body",
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BodyEditorChrome {
                OutlinedTextField(
                    value = state.body,
                    onValueChange = viewModel::updateBody,
                    placeholder = { Text(if (state.postType == "link") "Add context" else "Write your post") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    maxLines = 12,
                    enabled = !state.submitting,
                )
            }

            if (state.postType == "image" || state.postType == "song") {
                Spacer(modifier = Modifier.height(8.dp))
                FormNote(
                    message = "This post type is visible for parity with mobile web. Native upload wiring is next; use Text or Link for this build.",
                    tone = FormTone.Warning,
                )
            }

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
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .padding(top = 16.dp),
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                FormNote(message = state.error!!, tone = FormTone.Error)
            }
        }
    }
}

@Composable
private fun CommunitySelectorPill(
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
            Icon(
                PhosphorIcons.CaretDown,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ComposerTabs(
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean,
) {
    val tabs = listOf(
        ComposerTab("text", PhosphorIcons.TextT, "Text"),
        ComposerTab("image", PhosphorIcons.ImageSquare, "Image"),
        ComposerTab("link", PhosphorIcons.Link, "Link"),
        ComposerTab("song", PhosphorIcons.MusicNote, "Music"),
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
                    .clickable(enabled = enabled) { onSelect(tab.id) }
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = tab.label,
                    tint = if (active) PirateTokens.colors.textPrimary else PirateTokens.colors.textSecondary,
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
    val id: String,
    val icon: ImageVector,
    val label: String,
)

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
