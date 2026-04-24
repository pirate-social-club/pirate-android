package sc.pirate.app.post

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class PostComposerUiState(
    val title: String = "",
    val body: String = "",
    val eligibility: JoinEligibility? = null,
    val loadingEligibility: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

class PostComposerViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val _state = MutableStateFlow(PostComposerUiState())
    val state: StateFlow<PostComposerUiState> = _state.asStateFlow()

    fun loadEligibility(communityId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingEligibility = true, error = null)
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

    fun submit(communityId: String) {
        val current = _state.value
        if (current.title.isBlank() || current.submitting) return
        if (current.eligibility?.status != "already_joined") {
            _state.value = current.copy(error = "Join this community before posting.")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(submitting = true, error = null)
            try {
                communityRepository.createPost(
                    communityId,
                    CreatePostRequest(
                        title = current.title.trim(),
                        body = current.body.trim().ifBlank { null },
                        postType = "text",
                    ),
                )
                _state.value = _state.value.copy(submitting = false, submitted = true)
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
    onPosted: () -> Unit,
    onOpenCommunity: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(communityId) {
        viewModel.loadEligibility(communityId)
    }

    LaunchedEffect(state.submitted) {
        if (state.submitted) {
            onPosted()
        }
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create post",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PirateTokens.colors.bgPage,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val canPost = state.eligibility?.status == "already_joined"
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
        ) {
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

                !canPost -> {
                    StatusCard(
                        title = "Join before posting",
                        description = "Posting is available after you join this community.",
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
                enabled = canPost,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.body,
                onValueChange = viewModel::updateBody,
                label = { Text("Body") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxLines = 12,
                enabled = canPost,
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                FormNote(message = state.error!!, tone = sc.pirate.app.ui.FormTone.Error)
            }

            Spacer(modifier = Modifier.height(16.dp))

            PirateButton(
                text = "Post",
                onClick = { viewModel.submit(communityId) },
                loading = state.submitting,
                enabled = canPost && state.title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
