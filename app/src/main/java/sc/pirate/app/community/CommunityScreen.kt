package sc.pirate.app.community

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.model.Community
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.EmptyFeedState
import sc.pirate.app.ui.FormNote

data class CommunityUiState(
    val community: Community? = null,
    val posts: List<LocalizedPostResponse> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class CommunityViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()

    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    fun loadCommunity(communityId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val community = ApiClient.Communities.get(communityId)
                val posts = ApiClient.Communities.listPosts(communityId)
                _state.value = CommunityUiState(
                    community = community,
                    posts = posts.items,
                    loading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load community",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel,
    communityId: String,
    onNavigateToPost: (String) -> Unit,
    onNavigateToCompose: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(communityId) {
        viewModel.loadCommunity(communityId)
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.community?.displayName ?: "Community",
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToCompose,
                containerColor = PirateTokens.colors.accentBrand,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create post")
            }
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
            } else if (state.community != null) {
                val c = state.community!!
                PirateCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = c.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = PirateTokens.colors.textPrimary,
                    )
                    if (c.description != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = c.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = PirateTokens.colors.textSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${c.memberCount ?: 0} members",
                        style = MaterialTheme.typography.labelMedium,
                        color = PirateTokens.colors.textSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (state.posts.isEmpty()) {
                    EmptyFeedState(message = "No posts yet.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.posts, key = { it.post.postId }) { postResp ->
                            PostRow(
                                title = postResp.post.title ?: "Untitled",
                                body = postResp.post.body,
                                onClick = { onNavigateToPost(postResp.post.postId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRow(
    title: String,
    body: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PirateCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        if (body != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body.take(200),
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}
