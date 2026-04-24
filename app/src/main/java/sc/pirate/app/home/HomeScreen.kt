package sc.pirate.app.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.HomeFeedItem
import sc.pirate.app.api.model.HomeFeedResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class HomeUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val feed: HomeFeedResponse? = null,
    val error: String? = null,
    val paginationError: String? = null,
    val voteError: String? = null,
    val votingPostIds: Set<String> = emptySet(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val feedRepository get() = app.repositories.feedRepository
    private val postRepository get() = app.repositories.postRepository

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null, voteError = null)
            try {
                _state.value = HomeUiState(
                    loading = false,
                    feed = feedRepository.home(sort = "best"),
                )
            } catch (e: Exception) {
                _state.value = HomeUiState(
                    loading = false,
                    error = e.message ?: "Could not load home feed",
                )
            }
        }
    }

    fun loadMore() {
        val currentState = _state.value
        val currentFeed = currentState.feed ?: return
        val cursor = currentFeed.nextCursor ?: return
        if (currentState.loadingMore) return

        _state.value = currentState.copy(
            loadingMore = true,
            paginationError = null,
        )

        viewModelScope.launch {
            try {
                val nextPage = feedRepository.home(cursor = cursor, sort = "best")
                _state.value = _state.value.copy(
                    loadingMore = false,
                    feed = _state.value.feed?.appendPage(nextPage) ?: currentFeed.appendPage(nextPage),
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingMore = false,
                    paginationError = e.message ?: "Could not load more posts",
                )
            }
        }
    }

    fun votePost(postId: String, value: Int) {
        val currentState = _state.value
        if (postId in currentState.votingPostIds) return

        val currentFeed = currentState.feed ?: return
        val previousItem = currentFeed.items.firstOrNull { it.post.post.postId == postId } ?: return
        if (previousItem.post.viewerVote == value) return

        _state.value = currentState.copy(
            feed = currentFeed.withPostVote(postId, value),
            voteError = null,
            votingPostIds = currentState.votingPostIds + postId,
        )

        viewModelScope.launch {
            try {
                val response = postRepository.votePost(postId, value)
                _state.value = _state.value.copy(
                    feed = _state.value.feed?.withPostVote(postId, response.value),
                    votingPostIds = _state.value.votingPostIds - postId,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    feed = _state.value.feed?.replacePostItem(previousItem),
                    voteError = e.message ?: "Could not update vote",
                    votingPostIds = _state.value.votingPostIds - postId,
                )
            }
        }
    }
}

private fun HomeFeedResponse.appendPage(nextPage: HomeFeedResponse): HomeFeedResponse =
    copy(
        items = (items + nextPage.items).distinctBy { it.post.post.postId },
        nextCursor = nextPage.nextCursor,
        topCommunities = if (topCommunities.isNotEmpty()) topCommunities else nextPage.topCommunities,
    )

private fun HomeFeedResponse.withPostVote(postId: String, value: Int): HomeFeedResponse =
    copy(
        items = items.map { item ->
            if (item.post.post.postId != postId) return@map item

            val previousValue = item.post.viewerVote
            if (previousValue == value) return@map item

            item.copy(
                post = item.post.copy(
                    upvoteCount = adjustedVoteCount(item.post.upvoteCount, previousValue, value, 1),
                    downvoteCount = adjustedVoteCount(item.post.downvoteCount, previousValue, value, -1),
                    viewerVote = value,
                ),
            )
        },
    )

private fun HomeFeedResponse.replacePostItem(previousItem: HomeFeedItem): HomeFeedResponse =
    copy(
        items = items.map { item ->
            if (item.post.post.postId == previousItem.post.post.postId) previousItem else item
        },
    )

private fun adjustedVoteCount(current: Int, previousValue: Int?, nextValue: Int, targetValue: Int): Int =
    current + (if (nextValue == targetValue) 1 else 0) - (if (previousValue == targetValue) 1 else 0)

private fun scoreText(item: HomeFeedItem): String {
    val score = item.post.upvoteCount - item.post.downvoteCount
    val comments = item.post.threadSnapshot?.commentCount ?: 0
    return "$score score | $comments comments"
}

@Composable
fun HomeScreen(
    onNavigateToCommunity: (String) -> Unit,
    onNavigateToPost: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val feed = state.feed

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        item {
            Text(
                text = "Home",
                style = MaterialTheme.typography.headlineMedium,
                color = PirateTokens.colors.textPrimary,
            )
        }

        when {
            state.loading -> {
                item {
                    StatusCard(
                        title = "Loading feed",
                        description = "Fetching the latest posts.",
                        tone = StatusTone.Default,
                    )
                }
            }

            state.error != null -> {
                item {
                    StatusCard(
                        title = "Feed unavailable",
                        description = state.error.orEmpty(),
                        tone = StatusTone.Warning,
                    )
                }
                item {
                    PirateButton(
                        text = "Retry",
                        onClick = viewModel::load,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            feed == null || feed.items.isEmpty() -> {
                item {
                    StatusCard(
                        title = "No posts yet",
                        description = "Join or create a community to start building your feed.",
                        tone = StatusTone.Default,
                    )
                }
            }

            else -> {
                if (state.voteError != null) {
                    item {
                        StatusCard(
                            title = "Vote unavailable",
                            description = state.voteError.orEmpty(),
                            tone = StatusTone.Warning,
                        )
                    }
                }

                if (state.paginationError != null) {
                    item {
                        StatusCard(
                            title = "More posts unavailable",
                            description = state.paginationError.orEmpty(),
                            tone = StatusTone.Warning,
                        )
                    }
                }

                if (feed.topCommunities.isNotEmpty()) {
                    item {
                        PirateCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Top communities",
                                style = MaterialTheme.typography.titleMedium,
                                color = PirateTokens.colors.textPrimary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                feed.topCommunities.take(5).forEach { community ->
                                    PirateButton(
                                        text = community.displayName,
                                        onClick = { onNavigateToCommunity(community.communityId) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }

                items(feed.items, key = { it.post.post.postId }) { item ->
                    val post = item.post.post
                    val isVoting = post.postId in state.votingPostIds
                    PirateCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = item.community.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = PirateTokens.colors.textSecondary,
                        )
                        Text(
                            text = item.post.translatedTitle ?: post.title ?: post.body ?: "Untitled post",
                            style = MaterialTheme.typography.titleMedium,
                            color = PirateTokens.colors.textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        post.body?.takeIf { it.isNotBlank() && it != post.title }?.let { body ->
                            Text(
                                text = item.post.translatedBody ?: body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = PirateTokens.colors.textSecondary,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = scoreText(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = PirateTokens.colors.textSecondary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PirateButton(
                                text = if (item.post.viewerVote == 1) "Upvoted" else "Upvote",
                                onClick = { viewModel.votePost(post.postId, 1) },
                                enabled = !isVoting,
                                modifier = Modifier.weight(1f),
                            )
                            PirateButton(
                                text = if (item.post.viewerVote == -1) "Downvoted" else "Downvote",
                                onClick = { viewModel.votePost(post.postId, -1) },
                                enabled = !isVoting,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        PirateButton(
                            text = "Open post",
                            onClick = { onNavigateToPost(post.postId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (feed.nextCursor != null) {
                    item {
                        PirateButton(
                            text = if (state.loadingMore) "Loading" else "Load more",
                            onClick = viewModel::loadMore,
                            loading = state.loadingMore,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
