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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.PirateButton

data class PostComposerUiState(
    val title: String = "",
    val body: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val submitted: Boolean = false,
)

class PostComposerViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PostComposerUiState())
    val state: StateFlow<PostComposerUiState> = _state

    fun updateTitle(title: String) {
        _state.value = _state.value.copy(title = title)
    }

    fun updateBody(body: String) {
        _state.value = _state.value.copy(body = body)
    }

    fun submit(communityId: String) {
        val current = _state.value
        if (current.title.isBlank() || current.submitting) return

        viewModelScope.launch {
            _state.value = current.copy(submitting = true, error = null)
            try {
                ApiClient.Communities.createPost(
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    if (state.submitted) {
        onPosted()
        return
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
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    viewModel.updateTitle(it)
                },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = body,
                onValueChange = {
                    body = it
                    viewModel.updateBody(it)
                },
                label = { Text("Body") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxLines = 12,
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
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
