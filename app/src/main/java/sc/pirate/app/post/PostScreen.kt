package sc.pirate.app.post

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.PirateCard

data class PostUiState(
    val post: LocalizedPostResponse? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state

    fun loadPost(postId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val post = ApiClient.Posts.get(postId)
                _state.value = PostUiState(post = post, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load post",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    postId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PostViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(postId) {
        viewModel.loadPost(postId)
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.post?.post?.title ?: "Post",
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
        Column(modifier = Modifier.padding(innerPadding).padding(horizontal = 16.dp)) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                }
            } else if (state.error != null) {
                FormNote(message = state.error!!, tone = sc.pirate.app.ui.FormTone.Error)
            } else if (state.post != null) {
                val post = state.post!!.post
                PirateCard {
                    if (post.title != null) {
                        Text(
                            text = post.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = PirateTokens.colors.textPrimary,
                        )
                    }
                    if (post.body != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = post.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = PirateTokens.colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}
