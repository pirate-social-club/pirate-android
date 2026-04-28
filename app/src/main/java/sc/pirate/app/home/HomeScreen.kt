package sc.pirate.app.home

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import sc.pirate.app.api.model.HomeFeedCommunitySummary
import sc.pirate.app.api.model.HomeFeedItem
import sc.pirate.app.api.model.HomeFeedResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ChipOption
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateChipRow
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.adjustedVoteCount

data class HomeUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val feed: HomeFeedResponse? = null,
    val activeSort: String = "best",
    val topTimeRange: String = "day",
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
        load(sort = _state.value.activeSort, timeRange = _state.value.topTimeRange)
    }

    fun setSort(sort: String) {
        if (sort == _state.value.activeSort) return
        load(sort = sort, timeRange = _state.value.topTimeRange)
    }

    fun setTopTimeRange(timeRange: String) {
        if (timeRange == _state.value.topTimeRange) return
        load(sort = _state.value.activeSort, timeRange = timeRange)
    }

    private fun load(sort: String, timeRange: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                voteError = null,
                activeSort = sort,
                topTimeRange = timeRange,
            )
            try {
                _state.value = HomeUiState(
                    loading = false,
                    feed = feedRepository.home(
                        sort = sort,
                        timeRange = if (sort == "top") timeRange else null,
                    ),
                    activeSort = sort,
                    topTimeRange = timeRange,
                )
            } catch (e: Exception) {
                _state.value = HomeUiState(
                    loading = false,
                    activeSort = sort,
                    topTimeRange = timeRange,
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
                val nextPage = feedRepository.home(
                    cursor = cursor,
                    sort = currentState.activeSort,
                    timeRange = if (currentState.activeSort == "top") currentState.topTimeRange else null,
                )
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

private fun scoreText(item: HomeFeedItem): String {
    val score = item.post.upvoteCount - item.post.downvoteCount
    val comments = item.post.threadSnapshot?.commentCount ?: 0
    return "$score score | $comments comments"
}

private val homeSortOptions = listOf(
    ChipOption("best", "Best"),
    ChipOption("new", "New"),
    ChipOption("top", "Top"),
)

private val topTimeRangeOptions = listOf(
    ChipOption("day", "Day"),
    ChipOption("week", "Week"),
    ChipOption("month", "Month"),
    ChipOption("year", "Year"),
    ChipOption("all", "All"),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    hasSession: Boolean,
    onNavigateToCommunity: (String) -> Unit,
    onNavigateToPost: (String) -> Unit,
    onNavigateToCompose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val feed = state.feed

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Pirate",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                actions = {
                    if (hasSession) {
                        IconButton(onClick = onNavigateToCompose) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Create post",
                                tint = PirateTokens.colors.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PirateTokens.colors.bgPage,
                ),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.Start,
        ) {
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
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PirateChipRow(
                                options = homeSortOptions,
                                selectedValue = state.activeSort,
                                onSelected = viewModel::setSort,
                            )
                            if (state.activeSort == "top") {
                                PirateChipRow(
                                    options = topTimeRangeOptions,
                                    selectedValue = state.topTimeRange,
                                    onSelected = viewModel::setTopTimeRange,
                                )
                            }
                        }
                    }

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
                            TopCommunitiesRow(
                                communities = feed.topCommunities,
                                onNavigateToCommunity = onNavigateToCommunity,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    items(feed.items, key = { it.post.post.postId }) { item ->
                        val post = item.post.post
                        val isVoting = post.postId in state.votingPostIds
                        HomePostCard(
                            item = item,
                            isVoting = isVoting,
                            onOpenPost = { onNavigateToPost(post.postId) },
                            onOpenCommunity = { onNavigateToCommunity(item.community.communityId) },
                            onVote = { value -> viewModel.votePost(post.postId, value) },
                            modifier = Modifier.fillMaxWidth(),
                        )
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
}

@Composable
private fun TopCommunitiesRow(
    communities: List<HomeFeedCommunitySummary>,
    onNavigateToCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Top communities",
            style = MaterialTheme.typography.labelLarge,
            color = PirateTokens.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(communities.take(8), key = { it.communityId }) { community ->
                Surface(
                    shape = RoundedCornerShape(PirateTokens.radius.full),
                    color = PirateTokens.colors.bgElevated,
                    border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
                    modifier = Modifier.clickable {
                        onNavigateToCommunity(community.communityId)
                    },
                ) {
                    Text(
                        text = community.routeSlug?.let { "c/$it" } ?: community.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = PirateTokens.colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePostCard(
    item: HomeFeedItem,
    isVoting: Boolean,
    onOpenPost: () -> Unit,
    onOpenCommunity: () -> Unit,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val postResponse = item.post
    val post = postResponse.post
    val title = postResponse.translatedTitle
        ?: post.title
        ?: post.linkOgTitle
        ?: post.body
        ?: "Untitled post"
    val body = postResponse.translatedBody
        ?: post.body
        ?: post.caption
    val comments = postResponse.threadSnapshot?.commentCount ?: 0
    val score = postResponse.upvoteCount - postResponse.downvoteCount
    val routeLabel = item.community.routeSlug?.let { "c/$it" } ?: "c/${item.community.communityId}"
    val authorLabel = post.anonymousLabel
        ?: post.authorUserId?.take(8)?.let { "u/$it" }
        ?: "anonymous"

    Surface(
        modifier = modifier.clickable(onClick = onOpenPost),
        shape = RoundedCornerShape(PirateTokens.radius.lg),
        color = PirateTokens.colors.bgElevated,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = routeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textPrimary,
                    modifier = Modifier.clickable(onClick = onOpenCommunity),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "by $authorLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            body
                ?.takeIf { it.isNotBlank() && it != post.title && it != title }
                ?.let { bodyText ->
                    Text(
                        text = bodyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

            post.linkUrl?.takeIf { it.isNotBlank() }?.let { linkUrl ->
                Text(
                    text = linkUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.accentBrand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoteControl(
                    score = score,
                    viewerVote = postResponse.viewerVote,
                    enabled = !isVoting,
                    onVote = onVote,
                )
                Text(
                    text = "$comments comments",
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textSecondary,
                )
                Spacer(modifier = Modifier.weight(1f))
                post.postType?.takeIf { it.isNotBlank() && it != "text" }?.let { postType ->
                    Text(
                        text = postType,
                        style = MaterialTheme.typography.labelLarge,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteControl(
    score: Int,
    viewerVote: Int?,
    enabled: Boolean,
    onVote: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(PirateTokens.radius.full),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(
                onClick = { onVote(1) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Upvote",
                    tint = if (viewerVote == 1) PirateTokens.colors.accentBrand else PirateTokens.colors.textSecondary,
                )
            }
            Text(
                text = score.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
            IconButton(
                onClick = { onVote(-1) },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Downvote",
                    tint = if (viewerVote == -1) PirateTokens.colors.accentBrand else PirateTokens.colors.textSecondary,
                )
            }
        }
    }
}
