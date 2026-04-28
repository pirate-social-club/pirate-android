package sc.pirate.app.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.HomeFeedItem
import sc.pirate.app.api.model.HomeFeedResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.AuthRequiredSheet
import sc.pirate.app.ui.ChipOption
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateChipRow
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.VoteControl
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
    onNavigateToYourCommunities: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToInbox: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToCreateCommunity: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val feed = state.feed
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var authPromptAction by rememberSaveable { mutableStateOf<String?>(null) }

    authPromptAction?.let { action ->
        AuthRequiredSheet(
            actionLabel = action,
            onDismiss = { authPromptAction = null },
            onSignIn = {
                authPromptAction = null
                onSignIn()
            },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HomeNavigationDrawer(
                onClose = { scope.launch { drawerState.close() } },
                onHome = { scope.launch { drawerState.close() } },
                onPopular = {
                    scope.launch {
                        drawerState.close()
                        viewModel.setSort("top")
                    }
                },
                onYourCommunities = {
                    scope.launch {
                        drawerState.close()
                        onNavigateToYourCommunities()
                    }
                },
                onWallet = {
                    scope.launch {
                        drawerState.close()
                        onNavigateToWallet()
                    }
                },
                onChat = {
                    scope.launch {
                        drawerState.close()
                        onNavigateToChat()
                    }
                },
                onInbox = {
                    scope.launch {
                        drawerState.close()
                        onNavigateToInbox()
                    }
                },
                onProfile = {
                    scope.launch {
                        drawerState.close()
                        onNavigateToProfile()
                    }
                },
                onCreateCommunity = {
                    scope.launch {
                        drawerState.close()
                        if (hasSession) onNavigateToCreateCommunity() else authPromptAction = "Creating a community"
                    }
                },
            )
        },
    ) {
        Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(
                            imageVector = PhosphorIcons.List,
                            contentDescription = "Open navigation",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                title = {
                    Text(
                        text = "Pirate",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                actions = {
                    IconButton(onClick = {
                        if (hasSession) onNavigateToCompose() else authPromptAction = "Creating a post"
                    }) {
                        Icon(
                            imageVector = PhosphorIcons.Plus,
                            contentDescription = "Create post",
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(PirateTokens.colors.bgPage),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
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

                    if (state.paginationError != null) {
                        item {
                            StatusCard(
                            title = "More posts unavailable",
                            description = state.paginationError.orEmpty(),
                            tone = StatusTone.Warning,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
                            onVote = { value ->
                                if (hasSession) {
                                    viewModel.votePost(post.postId, value)
                                } else {
                                    authPromptAction = "Voting"
                                }
                            },
                            onComment = {
                                if (hasSession) {
                                    onNavigateToPost(post.postId)
                                } else {
                                    authPromptAction = "Commenting"
                                }
                            },
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
}

@Composable
private fun HomeNavigationDrawer(
    onClose: () -> Unit,
    onHome: () -> Unit,
    onPopular: () -> Unit,
    onYourCommunities: () -> Unit,
    onWallet: () -> Unit,
    onChat: () -> Unit,
    onInbox: () -> Unit,
    onProfile: () -> Unit,
    onCreateCommunity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = PirateTokens.colors.bgPage,
        drawerContentColor = PirateTokens.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Pirate",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PirateTokens.colors.textPrimary,
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = PhosphorIcons.CaretLeft,
                        contentDescription = "Close navigation",
                        tint = PirateTokens.colors.textPrimary,
                    )
                }
            }
            DrawerSectionLabel("Feed")
            DrawerRow("Popular", PhosphorIcons.Fire, onPopular)
            DrawerRow("Top", PhosphorIcons.TrendUp, onHome)
            DrawerSectionLabel("Pirate")
            DrawerRow("Home", PhosphorIcons.House, onHome)
            DrawerRow("Your Communities", PhosphorIcons.Flag, onYourCommunities)
            DrawerRow("Agents", PhosphorIcons.Robot, onChat)
            DrawerRow("Create Community", PhosphorIcons.Plus, onCreateCommunity)
            DrawerSectionLabel("Account")
            DrawerRow("Wallet", PhosphorIcons.Wallet, onWallet)
            DrawerRow("Inbox", PhosphorIcons.Bell, onInbox)
            DrawerRow("Profile", PhosphorIcons.UserCircle, onProfile)
        }
    }
}

@Composable
private fun DrawerSectionLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = PirateTokens.colors.textSecondary.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
private fun DrawerRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PirateTokens.colors.textSecondary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
    }
}

@Composable
private fun HomePostCard(
    item: HomeFeedItem,
    isVoting: Boolean,
    onOpenPost: () -> Unit,
    onOpenCommunity: () -> Unit,
    onVote: (Int) -> Unit,
    onComment: () -> Unit,
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
        color = PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedAvatar(label = item.community.displayName)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            text = "· ${relativeTimeLabel(post.createdAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PirateTokens.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (authorLabel != "anonymous") {
                        Text(
                            text = authorLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = PirateTokens.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
                    modifier = Modifier.clickable(onClick = onComment),
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

private fun relativeTimeLabel(timestamp: String): String {
    val createdAt = try {
        Instant.parse(timestamp)
    } catch (_: DateTimeParseException) {
        return ""
    }
    val duration = Duration.between(createdAt, Instant.now()).coerceAtLeast(Duration.ZERO)
    val minutes = duration.toMinutes()
    val hours = duration.toHours()
    val days = duration.toDays()
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 30 -> "${days}d"
        days < 365 -> "${days / 30}mo"
        else -> "${days / 365}y"
    }
}

@Composable
private fun FeedAvatar(label: String) {
    val colors = communityPlaceholderColors(label)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(PirateTokens.radius.full))
            .background(colors.first),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.trim().take(2).uppercase().ifBlank { "C" },
            style = MaterialTheme.typography.labelLarge,
            color = colors.second,
        )
    }
}

private fun communityPlaceholderColors(label: String): Pair<Color, Color> {
    val palette = listOf(
        Color(0xFF243F46) to Color(0xFFD9F0F2),
        Color(0xFF314936) to Color(0xFFE2F3DE),
        Color(0xFF3F3A5F) to Color(0xFFECE8FF),
        Color(0xFF4B4555) to Color(0xFFF0EAF6),
        Color(0xFF33465F) to Color(0xFFE6EEF8),
        Color(0xFF4C4A37) to Color(0xFFF4F0D9),
    )
    return palette[Math.floorMod(label.hashCode(), palette.size)]
}
