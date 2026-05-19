package sc.pirate.app.home

import android.app.Application
import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import sc.pirate.app.PirateApp
import sc.pirate.app.R
import sc.pirate.app.api.model.CommunityListing
import sc.pirate.app.api.model.CommunityPurchase
import sc.pirate.app.api.model.HomeFeedItem
import sc.pirate.app.api.model.HomeFeedResponse
import sc.pirate.app.api.model.LiveRoomAccessResponse
import sc.pirate.app.api.model.PostEmbed
import sc.pirate.app.live.LiveRoomPresentation
import sc.pirate.app.live.LiveRoomPresentationInput
import sc.pirate.app.live.LiveRoomUiState
import sc.pirate.app.live.buildLiveRoomPresentation
import sc.pirate.app.shared.formatCommunityRouteLabel
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.VoteControl
import sc.pirate.app.ui.adjustedVoteCount

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val feed: HomeFeedResponse? = null,
    val activeSort: String = "best",
    val topTimeRange: String = "day",
    val error: String? = null,
    val paginationError: String? = null,
    val refreshError: String? = null,
    val followError: String? = null,
    val voteError: String? = null,
    val votingPostIds: Set<String> = emptySet(),
    val followingCommunityIds: Set<String> = emptySet(),
    val viewerUserId: String? = null,
    val liveRoomAccessById: Map<String, LiveRoomAccessResponse> = emptyMap(),
    val listingsByAssetId: Map<String, CommunityListing> = emptyMap(),
    val listingsByLiveRoomId: Map<String, CommunityListing> = emptyMap(),
    val purchasesByAssetId: Map<String, CommunityPurchase> = emptyMap(),
    val purchasesByLiveRoomId: Map<String, CommunityPurchase> = emptyMap(),
)

private data class CommerceEnrichment(
    val listingsByAssetId: Map<String, CommunityListing> = emptyMap(),
    val listingsByLiveRoomId: Map<String, CommunityListing> = emptyMap(),
    val purchasesByAssetId: Map<String, CommunityPurchase> = emptyMap(),
    val purchasesByLiveRoomId: Map<String, CommunityPurchase> = emptyMap(),
    val liveRoomAccessById: Map<String, LiveRoomAccessResponse> = emptyMap(),
    val viewerUserId: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val feedRepository get() = app.repositories.feedRepository
    private val postRepository get() = app.repositories.postRepository
    private val communityRepository get() = app.repositories.communityRepository
    private val homeFeedCache get() = app.homeFeedCache

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private val enrichmentJobs = mutableSetOf<Job>()

    init {
        load()
    }

    fun load() {
        load(sort = _state.value.activeSort, timeRange = _state.value.topTimeRange)
    }

    fun refresh() {
        val current = _state.value
        if (current.loading || current.refreshing || current.loadingMore) return
        val sort = current.activeSort
        val timeRange = current.topTimeRange
        viewModelScope.launch {
            _state.value = current.copy(refreshing = true, error = null, refreshError = null)
            try {
                val key = cacheKey(sort, timeRange)
                val feed = feedRepository.home(
                    sort = sort,
                    timeRange = if (sort == "top") timeRange else null,
                )
                homeFeedCache.put(key, feed)
                _state.value = _state.value.withFeed(
                    refreshing = false,
                    loading = false,
                    feed = feed,
                    activeSort = sort,
                    topTimeRange = timeRange,
                    voteError = null,
                    refreshError = null,
                    viewerUserId = app.sessionStore.get()?.user?.userId ?: _state.value.viewerUserId,
                    liveRoomAccessById = emptyMap(),
                    listingsByAssetId = emptyMap(),
                    listingsByLiveRoomId = emptyMap(),
                    purchasesByAssetId = emptyMap(),
                    purchasesByLiveRoomId = emptyMap(),
                )
                loadEnrichmentInBackground(key, feed.items, replaceExisting = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    refreshing = false,
                    error = if (_state.value.feed?.items.isNullOrEmpty()) {
                        e.message ?: "Could not refresh home feed"
                    } else {
                        null
                    },
                    refreshError = if (_state.value.feed?.items.isNullOrEmpty()) {
                        null
                    } else {
                        e.message ?: "Could not refresh home feed"
                    },
                )
            }
        }
    }

    fun setSort(sort: String) {
        if (sort == _state.value.activeSort) return
        load(sort = sort, timeRange = _state.value.topTimeRange)
    }

    fun setTopTimeRange(timeRange: String) {
        if (timeRange == _state.value.topTimeRange) return
        load(sort = _state.value.activeSort, timeRange = timeRange)
    }

    fun toggleFollowCommunity(communityId: String) {
        val current = _state.value
        if (communityId in current.followingCommunityIds) return
        val feed = current.feed ?: return
        val item = feed.items.firstOrNull { it.homeCommunityId() == communityId } ?: return
        val currentlyFollowing = item.community.viewerFollowing == true

        _state.value = current.copy(
            feed = feed.withCommunityFollow(communityId, !currentlyFollowing),
            followingCommunityIds = current.followingCommunityIds + communityId,
            followError = null,
        )

        viewModelScope.launch {
            try {
                val response = if (currentlyFollowing) {
                    communityRepository.unfollowCommunity(communityId)
                } else {
                    communityRepository.followCommunity(communityId)
                }
                _state.value = _state.value.copy(
                    feed = _state.value.feed?.withCommunityFollow(communityId, response.following),
                    followingCommunityIds = _state.value.followingCommunityIds - communityId,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    feed = _state.value.feed?.withCommunityFollow(communityId, currentlyFollowing),
                    followingCommunityIds = _state.value.followingCommunityIds - communityId,
                    followError = e.message ?: "Could not update follow state",
                )
            }
        }
    }

    private fun load(sort: String, timeRange: String) {
        viewModelScope.launch {
            val key = cacheKey(sort, timeRange)
            val cached = homeFeedCache.get(key)
            if (cached != null) {
                _state.value = _state.value.withFeed(
                    loading = false,
                    feed = cached.feed,
                    activeSort = sort,
                    topTimeRange = timeRange,
                    viewerUserId = app.sessionStore.get()?.user?.userId,
                )
                loadEnrichmentInBackground(key, cached.feed.items, replaceExisting = true)
                if (cached.fresh) return@launch
            }

            _state.value = _state.value.copy(
                loading = cached == null,
                error = null,
                refreshError = null,
                voteError = null,
                activeSort = sort,
                topTimeRange = timeRange,
            )
            try {
                val feed = feedRepository.home(
                    sort = sort,
                    timeRange = if (sort == "top") timeRange else null,
                )
                homeFeedCache.put(key, feed)
                _state.value = _state.value.withFeed(
                    loading = false,
                    feed = feed,
                    activeSort = sort,
                    topTimeRange = timeRange,
                    viewerUserId = app.sessionStore.get()?.user?.userId,
                )
                loadEnrichmentInBackground(key, feed.items, replaceExisting = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cached != null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        refreshError = e.message ?: "Could not refresh home feed",
                    )
                } else {
                    _state.value = HomeUiState(
                        loading = false,
                        activeSort = sort,
                        topTimeRange = timeRange,
                        error = e.message ?: "Could not load home feed",
                    )
                }
            }
        }
    }

    fun loadMore() {
        val currentState = _state.value
        val currentFeed = currentState.feed ?: return
        val cursor = currentFeed.nextCursor ?: return
        if (currentState.loadingMore || currentState.refreshing) return

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
                val nextFeed = _state.value.feed?.appendPage(nextPage) ?: currentFeed.appendPage(nextPage)
                homeFeedCache.put(
                    cacheKey(currentState.activeSort, currentState.topTimeRange),
                    nextFeed,
                )
                _state.value = _state.value.copy(
                    loadingMore = false,
                    feed = nextFeed,
                )
                loadEnrichmentInBackground(
                    cacheKey(currentState.activeSort, currentState.topTimeRange),
                    nextPage.items,
                    replaceExisting = false,
                )
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    feed = _state.value.feed?.replacePostItem(previousItem),
                    voteError = e.message ?: "Could not update vote",
                    votingPostIds = _state.value.votingPostIds - postId,
                )
            }
        }
    }

    private suspend fun cacheKey(sort: String, timeRange: String): HomeFeedCacheKey {
        return HomeFeedCacheKey(
            userId = app.sessionStore.get()?.user?.userId,
            sort = sort,
            timeRange = if (sort == "top") timeRange else null,
        )
    }

    private fun loadEnrichmentInBackground(
        key: HomeFeedCacheKey,
        items: List<HomeFeedItem>,
        replaceExisting: Boolean,
    ) {
        if (replaceExisting) {
            enrichmentJobs.forEach { it.cancel() }
            enrichmentJobs.clear()
        }
        val job = viewModelScope.launch {
            val enrichment = loadCommerceEnrichment(items)
            val current = _state.value
            if (cacheKey(current.activeSort, current.topTimeRange) != key) return@launch
            _state.value = current.copy(
                viewerUserId = enrichment.viewerUserId ?: current.viewerUserId,
                liveRoomAccessById = current.liveRoomAccessById + enrichment.liveRoomAccessById,
                listingsByAssetId = current.listingsByAssetId + enrichment.listingsByAssetId,
                listingsByLiveRoomId = current.listingsByLiveRoomId + enrichment.listingsByLiveRoomId,
                purchasesByAssetId = current.purchasesByAssetId + enrichment.purchasesByAssetId,
                purchasesByLiveRoomId = current.purchasesByLiveRoomId + enrichment.purchasesByLiveRoomId,
            )
        }
        enrichmentJobs.add(job)
        job.invokeOnCompletion { enrichmentJobs.remove(job) }
    }

    private suspend fun loadCommerceEnrichment(items: List<HomeFeedItem>): CommerceEnrichment {
        if (items.isEmpty()) return CommerceEnrichment()

        val session = app.sessionStore.get()
        val hasSession = session != null
        val liveRoomRefs = items
            .mapNotNull { item ->
                item.post.post.anchorLiveRoom?.let { liveRoomId -> item.homeCommunityId() to liveRoomId }
            }
            .distinct()

        val liveRoomAccessById = mutableMapOf<String, LiveRoomAccessResponse>()
        val requestLimiter = Semaphore(4)

        if (!hasSession) {
            supervisorScope {
                liveRoomRefs.map { (communityId, liveRoomId) ->
                    async {
                        requestLimiter.withPermit {
                            liveRoomId to runCatching {
                                communityRepository.getPublicLiveRoomAccess(communityId, liveRoomId)
                            }.getOrNullUnlessCancelled()
                        }
                    }
                }.awaitAll().forEach { (liveRoomId, access) ->
                    if (access != null) liveRoomAccessById[liveRoomId] = access
                }
            }
            return CommerceEnrichment(
                liveRoomAccessById = liveRoomAccessById,
                viewerUserId = session?.user?.userId,
            )
        }

        val communityIds = items
            .mapNotNull { item ->
                val post = item.post.post
                if (post.assetId != null || post.anchorLiveRoom != null) item.homeCommunityId() else null
            }
            .distinct()
        val listingsByAssetId = mutableMapOf<String, CommunityListing>()
        val listingsByLiveRoomId = mutableMapOf<String, CommunityListing>()
        val purchasesByAssetId = mutableMapOf<String, CommunityPurchase>()
        val purchasesByLiveRoomId = mutableMapOf<String, CommunityPurchase>()

        supervisorScope {
            val commerceResults = async {
                communityIds.map { communityId ->
                    async {
                        requestLimiter.withPermit {
                            val listings = runCatching { communityRepository.listListings(communityId).items }
                                .getOrDefaultUnlessCancelled(emptyList())
                            val purchases = runCatching { communityRepository.listPurchases(communityId).items }
                                .getOrDefaultUnlessCancelled(emptyList())
                            listings to purchases
                        }
                    }
                }.awaitAll()
            }
            val liveRoomAccessResults = async {
                liveRoomRefs.map { (communityId, liveRoomId) ->
                    async {
                        requestLimiter.withPermit {
                            liveRoomId to getLiveRoomAccessUnlessCancelled(communityId, liveRoomId)
                        }
                    }
                }.awaitAll()
            }

            commerceResults.await().forEach { (listings, purchases) ->
                listings.forEach { listing ->
                    listing.asset?.takeIf { it.isNotBlank() }?.let { listingsByAssetId[it] = listing }
                    listing.liveRoom?.takeIf { it.isNotBlank() }?.let { listingsByLiveRoomId[it] = listing }
                }
                purchases.forEach { purchase ->
                    purchase.asset?.takeIf { it.isNotBlank() }?.let { purchasesByAssetId[it] = purchase }
                    purchase.liveRoom?.takeIf { it.isNotBlank() }?.let { purchasesByLiveRoomId[it] = purchase }
                }
            }

            liveRoomAccessResults.await().forEach { (liveRoomId, access) ->
                if (access != null) liveRoomAccessById[liveRoomId] = access
            }
        }

        return CommerceEnrichment(
            listingsByAssetId = listingsByAssetId,
            listingsByLiveRoomId = listingsByLiveRoomId,
            purchasesByAssetId = purchasesByAssetId,
            purchasesByLiveRoomId = purchasesByLiveRoomId,
            liveRoomAccessById = liveRoomAccessById,
            viewerUserId = session?.user?.userId,
        )
    }

    private suspend fun getLiveRoomAccessUnlessCancelled(
        communityId: String,
        liveRoomId: String,
    ): LiveRoomAccessResponse? {
        return try {
            communityRepository.getLiveRoomAccess(communityId, liveRoomId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            try {
                communityRepository.getPublicLiveRoomAccess(communityId, liveRoomId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun HomeUiState.withFeed(
    loading: Boolean,
    feed: HomeFeedResponse,
    activeSort: String,
    topTimeRange: String,
    viewerUserId: String?,
    refreshing: Boolean = false,
    voteError: String? = null,
    refreshError: String? = null,
    liveRoomAccessById: Map<String, LiveRoomAccessResponse> = emptyMap(),
    listingsByAssetId: Map<String, CommunityListing> = emptyMap(),
    listingsByLiveRoomId: Map<String, CommunityListing> = emptyMap(),
    purchasesByAssetId: Map<String, CommunityPurchase> = emptyMap(),
    purchasesByLiveRoomId: Map<String, CommunityPurchase> = emptyMap(),
): HomeUiState = copy(
    loading = loading,
    refreshing = refreshing,
    loadingMore = false,
    feed = feed,
    activeSort = activeSort,
    topTimeRange = topTimeRange,
    error = null,
    paginationError = null,
    refreshError = refreshError,
    followError = null,
    voteError = voteError,
    viewerUserId = viewerUserId,
    liveRoomAccessById = liveRoomAccessById,
    listingsByAssetId = listingsByAssetId,
    listingsByLiveRoomId = listingsByLiveRoomId,
    purchasesByAssetId = purchasesByAssetId,
    purchasesByLiveRoomId = purchasesByLiveRoomId,
)

private fun <T> Result<T>.getOrNullUnlessCancelled(): T? =
    fold(
        onSuccess = { it },
        onFailure = { error ->
            if (error is CancellationException) throw error
            null
        },
    )

private fun <T> Result<T>.getOrDefaultUnlessCancelled(defaultValue: T): T =
    fold(
        onSuccess = { it },
        onFailure = { error ->
            if (error is CancellationException) throw error
            defaultValue
        },
    )

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

private fun HomeFeedResponse.withCommunityFollow(communityId: String, following: Boolean): HomeFeedResponse =
    copy(
        items = items.map { item ->
            if (item.homeCommunityId() == communityId) {
                item.copy(community = item.community.copy(viewerFollowing = following))
            } else {
                item
            }
        },
    )

private fun scoreText(item: HomeFeedItem): String {
    val score = item.post.upvoteCount - item.post.downvoteCount
    val comments = item.post.commentCount ?: item.post.threadSnapshot?.commentCount ?: 0
    return "$score score | $comments comments"
}

private data class MediaPreview(
    val url: String,
    val title: String,
    val mimeType: String? = null,
    val videoUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
)

private data class ActiveMediaPreview(
    val item: HomeFeedItem,
    val preview: MediaPreview,
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
    onOpenNavigation: () -> Unit,
    signInDrawer: @Composable (onDismiss: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val feed = state.feed
    var authPromptAction by rememberSaveable { mutableStateOf<String?>(null) }
    var mediaPreview by remember { mutableStateOf<ActiveMediaPreview?>(null) }
    var actionItem by remember { mutableStateOf<HomeFeedItem?>(null) }

    authPromptAction?.let {
        signInDrawer { authPromptAction = null }
    }

    mediaPreview?.let { activePreview ->
        val item = activePreview.item
        val post = item.post.post
        MediaPreviewDialog(
            item = item,
            preview = activePreview.preview,
            onDismiss = { mediaPreview = null },
            onOpenCommunity = { onNavigateToCommunity(item.homeCommunityId()) },
            onVote = { value ->
                if (hasSession) {
                    viewModel.votePost(post.postId, value)
                } else {
                    mediaPreview = null
                    authPromptAction = "Voting"
                }
            },
            onComment = {
                mediaPreview = null
                if (hasSession) {
                    onNavigateToPost(post.postId)
                } else {
                    authPromptAction = "Commenting"
                }
            },
        )
    }

    actionItem?.let { item ->
        PostActionSheet(
            isFollowing = item.community.viewerFollowing == true,
            followLoading = item.homeCommunityId() in state.followingCommunityIds,
            onDismiss = { actionItem = null },
            onToggleFollow = {
                actionItem = null
                if (hasSession) {
                    viewModel.toggleFollowCommunity(item.homeCommunityId())
                } else {
                    authPromptAction = "Following communities"
                }
            },
        )
    }

    Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onOpenNavigation) {
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
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PirateTokens.colors.bgPage),
                    horizontalAlignment = Alignment.Start,
                ) {
                    when {
                        state.loading -> {
                            item {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = PirateTokens.colors.accentBrand,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                        }

                        state.error != null -> {
                            item {
                                HomeCenteredState(
                                    title = "Feed unavailable",
                                    description = userFacingFeedError(state.error),
                                    actionText = "Retry",
                                    onAction = viewModel::load,
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        }

                        feed == null || feed.items.isEmpty() -> {
                            item {
                                HomeCenteredState(
                                title = "No posts yet",
                                description = "Join or create a community to start building your feed.",
                                modifier = Modifier.fillParentMaxSize(),
                            )
                        }
                    }

                    else -> {
                        if (state.followError != null) {
                            item {
                                HomeInlineMessage(
                                    title = "Follow failed",
                                    description = state.followError.orEmpty(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }

                        if (state.paginationError != null) {
                            item {
                                HomeInlineMessage(
                                    title = "More posts unavailable",
                                    description = state.paginationError.orEmpty(),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }

                        if (state.refreshError != null) {
                            item {
                                HomeInlineMessage(
                                    title = "Refresh failed",
                                    description = state.refreshError.orEmpty(),
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
                                liveRoomAccess = post.anchorLiveRoom?.let { state.liveRoomAccessById[it] },
                                liveRoomListing = post.anchorLiveRoom?.let { state.listingsByLiveRoomId[it] },
                                liveRoomPurchase = post.anchorLiveRoom?.let { state.purchasesByLiveRoomId[it] },
                                viewerUserId = state.viewerUserId,
                                onOpenPost = { onNavigateToPost(post.postId) },
                                onOpenCommunity = { onNavigateToCommunity(item.homeCommunityId()) },
                                onOpenMedia = { mediaPreview = ActiveMediaPreview(item, it) },
                                onOpenActions = { actionItem = item },
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

private fun HomeFeedItem.homeCommunityId(): String =
    community.communityId?.takeIf { it.isNotBlank() } ?: post.post.communityId

private fun HomeFeedItem.communityRouteLabel(): String =
    formatCommunityRouteLabel(
        communityId = homeCommunityId(),
        routeSlug = community.routeSlug ?: community.displayName,
    )

private fun userFacingFeedError(error: String?): String {
    val message = error?.takeIf { it.isNotBlank() } ?: return "Could not load the home feed."
    return if (message.contains("serial name") || message.contains("Fields [")) {
        "Could not load the home feed."
    } else {
        message
    }
}

@Composable
private fun MediaPreviewDialog(
    item: HomeFeedItem,
    preview: MediaPreview,
    onDismiss: () -> Unit,
    onOpenCommunity: () -> Unit,
    onVote: (Int) -> Unit,
    onComment: () -> Unit,
) {
    val postResponse = item.post
    val post = postResponse.post
    val routeLabel = item.communityRouteLabel()
    val authorLabel = post.anonymousLabel
        ?: post.authorUserId?.take(8)?.let { "u/$it" }
        ?: "anonymous"
    val caption = postResponse.translatedCaption
        ?: post.caption
        ?: postResponse.translatedBody
        ?: post.body
        ?: preview.title
    val comments = postResponse.commentCount ?: postResponse.threadSnapshot?.commentCount ?: 0
    val score = postResponse.upvoteCount - postResponse.downvoteCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.X,
                            contentDescription = "Close preview",
                            tint = Color.White,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable(onClick = onOpenCommunity),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FeedAvatar(
                            label = item.community.displayName,
                            avatarSrc = resolveCommunityAvatarSrc(
                                avatarSrc = item.community.avatarRef,
                                communityId = item.homeCommunityId(),
                                displayName = item.community.displayName,
                            ),
                            size = 24,
                        )
                        Text(
                            text = routeLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (preview.isVideo) {
                        VideoPlayer(
                            url = preview.videoUrl ?: preview.url,
                            autoplay = true,
                            muted = false,
                            showControls = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 620.dp)
                                .aspectRatio(preview.displayAspectRatio()),
                        )
                    } else {
                        var detectedAspectRatio by remember(preview.url) { mutableStateOf<Float?>(null) }
                        val imageAspectRatio = detectedAspectRatio ?: preview.displayAspectRatio()
                        AsyncImage(
                            model = preview.url,
                            contentDescription = preview.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 620.dp)
                                .aspectRatio(imageAspectRatio),
                            contentScale = ContentScale.Fit,
                            onSuccess = { state ->
                                detectedAspectRatio = state.result.drawable.detectedAspectRatio()
                                    ?: detectedAspectRatio
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeedAvatar(
                        label = item.community.displayName,
                        avatarSrc = resolveCommunityAvatarSrc(
                            avatarSrc = item.community.avatarRef,
                            communityId = item.homeCommunityId(),
                            displayName = item.community.displayName,
                        ),
                        size = 36,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = authorLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoteControl(
                        score = score,
                        viewerVote = postResponse.viewerVote,
                        enabled = true,
                        onVote = onVote,
                    )
                    CommentPill(count = comments, onClick = onComment)
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PostActionSheet(
    isFollowing: Boolean,
    followLoading: Boolean,
    onDismiss: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = PirateTokens.colors.bgPage,
        contentColor = PirateTokens.colors.textPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SheetActionRow(
                label = if (isFollowing) "Unfollow" else "Follow",
                icon = PhosphorIcons.Users,
                enabled = !followLoading,
                onClick = onToggleFollow,
            )
            SheetActionRow(
                label = "Save post",
                icon = PhosphorIcons.Article,
                enabled = false,
                onClick = {},
            )
            SheetActionRow(
                label = "Report post",
                icon = PhosphorIcons.Flag,
                enabled = false,
                onClick = {},
            )
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SheetActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val contentColor = if (enabled) {
            PirateTokens.colors.textPrimary
        } else {
            PirateTokens.colors.textSecondary.copy(alpha = 0.5f)
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) PirateTokens.colors.textSecondary else contentColor,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun HomeCenteredState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = PirateTokens.colors.accentBrand,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            if (actionText != null && onAction != null) {
                PirateButton(
                    text = actionText,
                    onClick = onAction,
                    modifier = Modifier.widthIn(min = 140.dp, max = 220.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeInlineMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = PirateTokens.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomePostCard(
    item: HomeFeedItem,
    isVoting: Boolean,
    liveRoomAccess: LiveRoomAccessResponse?,
    liveRoomListing: CommunityListing?,
    liveRoomPurchase: CommunityPurchase?,
    viewerUserId: String?,
    onOpenPost: () -> Unit,
    onOpenCommunity: () -> Unit,
    onOpenMedia: (MediaPreview) -> Unit,
    onOpenActions: () -> Unit,
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
    val comments = postResponse.commentCount ?: postResponse.threadSnapshot?.commentCount ?: 0
    val score = postResponse.upvoteCount - postResponse.downvoteCount
    val routeLabel = item.communityRouteLabel()
    val mediaPreview = item.primaryMediaPreview(title)
    val xEmbed = post.primaryXEmbed()

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
                FeedAvatar(
                    label = item.community.displayName,
                    avatarSrc = resolveCommunityAvatarSrc(
                        avatarSrc = item.community.avatarRef,
                        communityId = item.homeCommunityId(),
                        displayName = item.community.displayName,
                    ),
                )
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
                }
                IconButton(onClick = onOpenActions) {
                    Icon(
                        imageVector = PhosphorIcons.DotsThree,
                        contentDescription = "Post options",
                        tint = PirateTokens.colors.textSecondary,
                    )
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

            post.anchorLiveRoom?.let { liveRoomId ->
                LiveRoomSummaryCard(
                    presentation = buildLiveRoomPresentation(
                        LiveRoomPresentationInput(
                            fallbackTitle = title,
                            access = liveRoomAccess,
                            listing = liveRoomListing,
                            purchase = liveRoomPurchase,
                            publicStatus = post.anchorLiveRoomStatus,
                            publicAccessMode = post.accessMode,
                            fallbackCoverRef = post.mediaRefs.firstOrNull()?.posterRef ?: post.mediaRefs.firstOrNull()?.storageRef,
                            viewerUserId = viewerUserId,
                            postAuthorUserId = post.authorUserId,
                            liveRoomId = liveRoomId,
                        ),
                    ),
                    onOpenPost = onOpenPost,
                )
            }

            if (xEmbed != null) {
                XEmbedPreviewCard(embed = xEmbed)
            }

            post.linkUrl?.takeIf { xEmbed == null && it.isNotBlank() }?.let { linkUrl ->
                Text(
                    text = linkUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.accentBrand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            mediaPreview?.let { preview ->
                FeedMediaPreview(
                    preview = preview,
                    onClick = { onOpenMedia(preview) },
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
                CommentPill(count = comments, onClick = onComment)
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LiveRoomSummaryCard(
    presentation: LiveRoomPresentation,
    onOpenPost: () -> Unit,
) {
    val primaryActionLabel = when (val ui = presentation.uiState) {
        is LiveRoomUiState.CanWatch -> ui.cta
        is LiveRoomUiState.CanWatchReplay -> ui.cta
        is LiveRoomUiState.NeedsAccess -> ui.cta
        is LiveRoomUiState.NeedsTicket -> ui.cta
        is LiveRoomUiState.NeedsVerification -> ui.cta
        is LiveRoomUiState.CanRsvp -> ui.cta
        else -> null
    }.takeIf { presentation.producerRole == null }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PirateTokens.colors.bgElevated),
                contentAlignment = Alignment.Center,
            ) {
                if (presentation.coverSrc != null) {
                    AsyncImage(
                        model = presentation.coverSrc,
                        contentDescription = presentation.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = PhosphorIcons.VideoCamera,
                        contentDescription = null,
                        tint = PirateTokens.colors.textSecondary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = presentation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PirateTokens.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                presentation.statusLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (presentation.status == "live") PirateTokens.colors.accentDanger else PirateTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                presentation.accessLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = PirateTokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                presentation.participantLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                presentation.descriptionLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                primaryActionLabel?.let { label ->
                    PirateButton(
                        text = label,
                        onClick = onOpenPost,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (presentation.uiState is LiveRoomUiState.ReplayProcessing) {
                    Text(
                        text = "Replay processing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun XEmbedPreviewCard(embed: PostEmbed) {
    val preview = embed.preview
    val text = preview?.text?.trim()
        ?: preview?.title?.trim()
        ?: "X post"
    val source = formatXSource(embed)
    val imageSrc = preview?.mediaUrl?.takeIf { it.isNotBlank() }
        ?: preview?.thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: preview?.imageUrl?.takeIf { it.isNotBlank() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textPrimary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = source,
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            imageSrc?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PirateTokens.colors.bgElevated),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun CommentPill(
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PirateTokens.radius.full),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier
                .height(38.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PhosphorIcons.ChatCircle,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun FeedMediaPreview(
    preview: MediaPreview,
    onClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        var detectedAspectRatio by remember(preview.url) { mutableStateOf<Float?>(null) }
        val aspectRatio = detectedAspectRatio ?: preview.displayAspectRatio()
        val mediaHeight = (maxWidth / aspectRatio).coerceAtMost(620.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mediaHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(PirateTokens.colors.bgElevated)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (preview.isVideo) {
                VideoPlayer(
                    url = preview.videoUrl ?: preview.url,
                    autoplay = true,
                    muted = true,
                    showControls = false,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = preview.url,
                    contentDescription = preview.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        detectedAspectRatio = state.result.drawable.detectedAspectRatio()
                            ?: detectedAspectRatio
                    },
                )
            }
        }
    }
}

@Composable
private fun VideoPlayer(
    url: String,
    autoplay: Boolean,
    muted: Boolean,
    showControls: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            repeatMode = if (showControls) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
            volume = if (muted) 0f else 1f
            playWhenReady = autoplay
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            if (showControls) {
                PlayerView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            } else {
                (LayoutInflater.from(context)
                    .inflate(R.layout.pirate_player_view_texture, null, false) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            }
        },
        update = { view ->
            (view as PlayerView).apply {
                useController = showControls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
            player.volume = if (muted) 0f else 1f
            player.playWhenReady = autoplay
        },
    )
}

private val MediaPreview.isVideo: Boolean
    get() = mimeType?.startsWith("video/") == true || videoUrl != null

private fun MediaPreview.displayAspectRatio(): Float {
    val rawRatio = if (width != null && height != null && width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        16f / 9f
    }
    return rawRatio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
}

private fun android.graphics.drawable.Drawable.detectedAspectRatio(): Float? {
    val width = intrinsicWidth
    val height = intrinsicHeight
    if (width <= 0 || height <= 0) return null
    val ratio = width.toFloat() / height.toFloat()
    return ratio.takeIf { it.isFinite() && it > 0f }
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

private fun HomeFeedItem.primaryMediaPreview(title: String): MediaPreview? {
    if (post.post.primaryXEmbed() != null) return null

    val media = post.post.mediaRefs.firstOrNull()
    if (media != null) {
        val previewUrl = when {
            media.mimeType?.startsWith("image/") == true -> media.storageRef
            media.mimeType?.startsWith("video/") == true -> media.posterRef
            else -> media.posterRef ?: media.storageRef
        }?.let(::resolvePublicMediaSrc)
        val videoUrl = if (media.mimeType?.startsWith("video/") == true) {
            resolvePublicMediaSrc(media.storageRef)
        } else {
            null
        }
        val displayWidth = if (media.mimeType?.startsWith("video/") == true) {
            media.posterWidth ?: media.width
        } else {
            media.width
        }
        val displayHeight = if (media.mimeType?.startsWith("video/") == true) {
            media.posterHeight ?: media.height
        } else {
            media.height
        }

        if (previewUrl != null || videoUrl != null) {
            return MediaPreview(
                url = previewUrl ?: videoUrl.orEmpty(),
                title = title,
                mimeType = media.mimeType,
                videoUrl = videoUrl,
                width = displayWidth,
                height = displayHeight,
            )
        }
    }

    return post.post.linkOgImageUrl
        ?.let(::resolvePublicMediaSrc)
        ?.let { MediaPreview(url = it, title = title, mimeType = "image/*") }
}

private fun sc.pirate.app.api.model.Post.primaryXEmbed(): PostEmbed? =
    embeds.firstOrNull { it.provider == "x" }
        ?: detectClientSideXEmbed(linkUrl)

private fun detectClientSideXEmbed(linkUrl: String?): PostEmbed? {
    val trimmed = linkUrl?.trim().orEmpty()
    if (trimmed.isBlank()) return null

    val uri = try {
        java.net.URI(trimmed)
    } catch (_: Exception) {
        return null
    }

    val scheme = uri.scheme?.lowercase()
    if (scheme != "https" && scheme != "http") return null

    val host = uri.host?.trim()?.lowercase()?.trimEnd('.') ?: return null
    if (host !in setOf("x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")) {
        return null
    }

    val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
    val statusIndex = segments.indexOfFirst { it.equals("status", ignoreCase = true) }
    val postId = when {
        statusIndex > 0 -> segments.getOrNull(statusIndex + 1)
        segments.size >= 4 && segments[0] == "i" && segments[1] == "web" && segments[2] == "status" -> segments[3]
        else -> null
    }?.trim()
    if (postId.isNullOrBlank() || postId.any { !it.isDigit() }) return null

    val handle = if (statusIndex > 0) segments.getOrNull(statusIndex - 1)?.trim() else null
    val canonicalPath = if (!handle.isNullOrBlank()) {
        "/${java.net.URLEncoder.encode(handle, StandardCharsets.UTF_8.toString())}/status/$postId"
    } else {
        "/i/web/status/$postId"
    }

    return PostEmbed(
        provider = "x",
        canonicalUrl = "https://x.com$canonicalPath",
        originalUrl = trimmed,
        state = "embed",
    )
}

private fun formatXSource(embed: PostEmbed): String {
    val author = embed.preview?.authorName?.trim()
    if (!author.isNullOrBlank()) return "$author on X"

    val authorUrl = embed.preview?.authorUrl?.trim()
    val handleFromAuthor = authorUrl?.let(::extractXHandle)
    if (!handleFromAuthor.isNullOrBlank()) return "@$handleFromAuthor on X"

    val handleFromCanonical = extractXHandle(embed.canonicalUrl)
    if (!handleFromCanonical.isNullOrBlank()) return "@$handleFromCanonical on X"

    return "X"
}

private fun extractXHandle(url: String): String? {
    val uri = try {
        java.net.URI(url)
    } catch (_: Exception) {
        return null
    }
    val host = uri.host?.lowercase() ?: return null
    if (host != "x.com" && host != "twitter.com" && host != "www.x.com" && host != "www.twitter.com") return null
    val first = uri.path.orEmpty().split('/').firstOrNull { it.isNotBlank() } ?: return null
    return first.takeUnless { it == "i" }
}

@Composable
private fun FeedAvatar(
    label: String,
    avatarSrc: String,
    size: Int = 44,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(PirateTokens.radius.full))
            .background(PirateTokens.colors.bgElevated),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = avatarSrc,
            contentDescription = label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun resolveCommunityAvatarSrc(
    avatarSrc: String?,
    communityId: String,
    displayName: String,
): String = avatarSrc?.trim()?.takeIf { it.isNotBlank() }
    ?.let(::resolvePublicMediaSrc)
    ?: buildDefaultCommunityAvatarSrc(communityId, displayName)

private fun buildDefaultCommunityAvatarSrc(communityId: String, displayName: String): String {
    val seed = "${communityId.trim()}:${displayName.trim().replace(Regex("\\s+"), " ")}"
    val palette = listOf(
        "#243f46" to "#d9f0f2",
        "#314936" to "#e2f3de",
        "#3f3a5f" to "#ece8ff",
        "#4b4555" to "#f0eaf6",
        "#33465f" to "#e6eef8",
        "#4c4a37" to "#f4f0d9",
    )
    val colors = palette[Math.floorMod(seed.hashCode(), palette.size)]
    val initials = displayName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "C" }
    val svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 128 128" role="img" aria-label="$initials">
          <rect width="128" height="128" rx="64" fill="${colors.first}" />
          <path d="M24 92C38 74 53 65 68 65C83 65 95 72 104 86" fill="none" stroke="rgba(255,255,255,0.16)" stroke-width="10" stroke-linecap="round" />
          <text x="50%" y="55%" text-anchor="middle" dominant-baseline="middle" fill="${colors.second}" font-family="system-ui, Arial, sans-serif" font-size="44" font-weight="700">$initials</text>
        </svg>
    """.trimIndent()
    return "data:image/svg+xml;charset=utf-8,${encodeQuery(svg)}"
}

private fun encodeQuery(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
