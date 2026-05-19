package sc.pirate.app.submit

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.PostableCommunitySummary
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

enum class CommunityListAction {
    Compose,
    OpenCommunity,
}

data class CommunityListItem(
    val avatarRef: String? = null,
    val action: CommunityListAction = CommunityListAction.Compose,
    val communityId: String,
    val displayName: String,
    val routeSlug: String? = null,
)

data class PostableCommunityListUiState(
    val communities: List<CommunityListItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class PostableCommunityListViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository
    private val _state = MutableStateFlow(PostableCommunityListUiState())
    val state: StateFlow<PostableCommunityListUiState> = _state.asStateFlow()

    fun load(hasSession: Boolean) {
        if (!hasSession) {
            _state.value = PostableCommunityListUiState()
            return
        }

        _state.value = PostableCommunityListUiState(loading = true)
        viewModelScope.launch {
            loadPostableCommunities()
        }
    }

    fun rememberSelected(community: CommunityListItem) {
        app.knownCommunitiesStore.remember(
            communityId = community.communityId,
            displayName = community.displayName,
            avatarRef = community.avatarRef,
            routeSlug = community.routeSlug,
        )
    }

    private suspend fun loadPostableCommunities() {
        try {
            val communities = profileRepository
                .getPostableCommunities()
                .communities
                .mapNotNull { it.toListItem() }
            _state.value = PostableCommunityListUiState(communities = communities)
        } catch (e: Exception) {
            _state.value = PostableCommunityListUiState(
                error = e.message ?: "Could not load communities.",
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostableCommunityListScreen(
    hasSession: Boolean,
    viewModel: PostableCommunityListViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onSelectCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(hasSession) {
        viewModel.load(hasSession)
    }

    Scaffold(
        modifier = modifier,
        containerColor = PirateTokens.colors.bgPage,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Choose a community",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhosphorIcons.X,
                            contentDescription = "Close",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PirateTokens.colors.bgPage,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (!hasSession) {
                Spacer(modifier = Modifier.height(16.dp))
                StatusCard(
                    title = "Sign in to post",
                    description = "Choose a community after signing in.",
                    tone = StatusTone.Warning,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                PirateButton(
                    text = "Sign in",
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            when {
                state.communities.isNotEmpty() -> CommunityList(
                    communities = state.communities,
                    onSelect = { community ->
                        viewModel.rememberSelected(community)
                        onSelectCommunity(community.communityId)
                    },
                )
                state.loading -> CommunityListSkeleton()
                state.error != null -> CommunityListError(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load(hasSession) },
                )
                state.communities.isEmpty() -> CommunityListEmpty()
            }
        }
    }

}

@Composable
private fun CommunityList(
    communities: List<CommunityListItem>,
    onSelect: (CommunityListItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(communities, key = { it.communityId }) { community ->
            CommunityListRow(
                community = community,
                onClick = { onSelect(community) },
            )
        }
    }
}

@Composable
private fun CommunityListSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(5) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PirateTokens.colors.bgElevated,
                shape = RoundedCornerShape(PirateTokens.radius.lg),
                border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(PirateTokens.radius.full))
                            .background(PirateTokens.colors.surfaceSubtle),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(PirateTokens.radius.sm))
                                .background(PirateTokens.colors.surfaceSubtle),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityListError(
    message: String,
    onRetry: () -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    StatusCard(
        title = "Communities unavailable",
        description = message,
        tone = StatusTone.Warning,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(modifier = Modifier.height(12.dp))
    PirateButton(
        text = "Retry",
        onClick = onRetry,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CommunityListEmpty() {
    Spacer(modifier = Modifier.height(16.dp))
    StatusCard(
        title = "No communities available",
        description = "Join or unlock a community before creating a post.",
        tone = StatusTone.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CommunityListRow(
    community: CommunityListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = PirateTokens.colors.bgElevated,
        shape = RoundedCornerShape(PirateTokens.radius.lg),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityAvatar(community)
            Text(
                text = community.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = PhosphorIcons.CaretRight,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CommunityAvatar(community: CommunityListItem) {
    val initials = community.displayName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "C" }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(PirateTokens.radius.full))
            .background(PirateTokens.colors.surfaceSubtle),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        resolvePublicMediaSrc(community.avatarRef)?.let { avatarSrc ->
            AsyncImage(
                model = avatarSrc,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun PostableCommunitySummary.toListItem(): CommunityListItem? {
    val listAction = when (action) {
        "compose" -> CommunityListAction.Compose
        "unlock" -> CommunityListAction.OpenCommunity
        else -> return null
    }
    return CommunityListItem(
        avatarRef = avatarRef,
        action = listAction,
        communityId = communityId,
        displayName = displayName,
        routeSlug = routeSlug,
    )
}
