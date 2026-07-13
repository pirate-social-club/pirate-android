package sc.pirate.app.community

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import sc.pirate.app.PirateApp
import sc.pirate.app.api.PoWGate
import sc.pirate.app.api.model.Community
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CommunityReferenceLink
import sc.pirate.app.api.model.CommunityRule
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.MembershipGateSummary
import sc.pirate.app.shared.formatCommunityRouteLabel
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.safety.withoutBlockedPostAuthors
import sc.pirate.app.song.SongPlaybackState
import sc.pirate.app.song.SongSummaryRow
import sc.pirate.app.song.resolveSongAudioUrl
import sc.pirate.app.song.songDisplayTitle
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ChipOption
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.EmptyFeedState
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.FeedSkeletons
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.VoteControl
import sc.pirate.app.ui.adjustedVoteCount
import sc.pirate.app.video.VideoPlaybackState
import sc.pirate.app.video.VideoPlayerView
import sc.pirate.app.video.isVideoPost
import sc.pirate.app.video.resolveVideoPosterUrl
import sc.pirate.app.video.videoAspectRatio

data class CommunityUiState(
    val community: Community? = null,
    val preview: CommunityPreview? = null,
    val eligibility: JoinEligibility? = null,
    val posts: List<LocalizedPostResponse> = emptyList(),
    val nextPostsCursor: String? = null,
    val activeSort: String = "best",
    val loading: Boolean = true,
    val postsLoadingMore: Boolean = false,
    val joinLoading: Boolean = false,
    val solvingProofOfWork: Boolean = false,
    val followLoading: Boolean = false,
    val error: String? = null,
    val postsPaginationError: String? = null,
    val joinError: String? = null,
    val followError: String? = null,
    val voteError: String? = null,
    val votingPostIds: Set<String> = emptySet(),
)

class CommunityViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val powGate by lazy { PoWGate(app.apiClient) }
    private val postRepository get() = app.repositories.postRepository

    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()
    val playbackState: StateFlow<SongPlaybackState> = app.songPlaybackController.state
    val videoPlaybackState: StateFlow<VideoPlaybackState> = app.videoPlaybackController.state

    private var currentCommunityId: String? = null
    private var currentHasSession: Boolean = false
    private var joinJob: Job? = null
    private var blockedUserIds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            app.userBlockStore.observe().collect { blockState ->
                blockedUserIds = blockState.userIds
                _state.value = _state.value.copy(
                    posts = _state.value.posts.withoutBlockedPostAuthors(blockedUserIds),
                )
            }
        }
    }

    fun loadCommunity(communityId: String, hasSession: Boolean, sort: String = _state.value.activeSort) {
        currentCommunityId = communityId
        currentHasSession = hasSession

        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                error = null,
                postsPaginationError = null,
                activeSort = sort,
            )
            try {
                if (!hasSession) {
                    val preview = communityRepository.getPublicPreview(communityId)
                    val posts = communityRepository.listPublicPosts(
                        communityId = communityId,
                        limit = 25,
                        sort = sort,
                    )
                    app.knownCommunitiesStore.remember(
                        communityId = preview.communityId,
                        displayName = preview.displayName,
                        avatarRef = preview.avatarRef,
                        routeSlug = preview.routeSlug,
                    )
                    _state.value = CommunityUiState(
                        preview = preview,
                        posts = posts.items.withoutBlockedPostAuthors(blockedUserIds),
                        nextPostsCursor = posts.nextCursor,
                        activeSort = sort,
                        loading = false,
                    )
                    return@launch
                }

                val preview = communityRepository.getPreview(communityId)
                val eligibility = communityRepository.getJoinEligibility(communityId)
                val nextState = if (eligibility.status != "already_joined") {
                    val posts = communityRepository.listPublicPosts(
                        communityId = communityId,
                        limit = 25,
                        sort = sort,
                    )
                    CommunityUiState(
                        preview = preview,
                        eligibility = eligibility,
                        posts = posts.items.withoutBlockedPostAuthors(blockedUserIds),
                        nextPostsCursor = posts.nextCursor,
                        activeSort = sort,
                        loading = false,
                    )
                } else {
                    val community = communityRepository.getCommunity(communityId)
                    val posts = communityRepository.listPosts(
                        communityId = communityId,
                        limit = 25,
                        sort = sort,
                    )
                    CommunityUiState(
                        community = community,
                        preview = preview,
                        eligibility = eligibility,
                        posts = posts.items.withoutBlockedPostAuthors(blockedUserIds),
                        nextPostsCursor = posts.nextCursor,
                        activeSort = sort,
                        loading = false,
                    )
                }
                nextState.preview?.let { preview ->
                    app.knownCommunitiesStore.remember(
                        communityId = preview.communityId,
                        displayName = preview.displayName,
                        avatarRef = preview.avatarRef,
                        routeSlug = preview.routeSlug,
                    )
                }
                _state.value = nextState
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load community",
                )
            }
        }
    }

    fun setSort(sort: String) {
        val communityId = currentCommunityId ?: return
        if (sort == _state.value.activeSort) return
        loadCommunity(communityId = communityId, hasSession = currentHasSession, sort = sort)
    }

    fun loadMorePosts() {
        val communityId = currentCommunityId ?: return
        val current = _state.value
        val cursor = current.nextPostsCursor ?: return
        if (current.postsLoadingMore) return

        _state.value = current.copy(
            postsLoadingMore = true,
            postsPaginationError = null,
        )

        viewModelScope.launch {
            try {
                val page = if (currentHasSession) {
                    communityRepository.listPosts(
                        communityId = communityId,
                        cursor = cursor,
                        limit = 25,
                        sort = current.activeSort,
                    )
                } else {
                    communityRepository.listPublicPosts(
                        communityId = communityId,
                        cursor = cursor,
                        limit = 25,
                        sort = current.activeSort,
                    )
                }
                _state.value = _state.value.copy(
                    posts = (_state.value.posts + page.items)
                        .distinctBy { it.post.postId }
                        .withoutBlockedPostAuthors(blockedUserIds),
                    nextPostsCursor = page.nextCursor,
                    postsLoadingMore = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    postsLoadingMore = false,
                    postsPaginationError = e.message ?: "Could not load more posts",
                )
            }
        }
    }

    fun joinCommunity() {
        val communityId = currentCommunityId ?: return
        joinJob?.cancel()
        joinJob = viewModelScope.launch {
            _state.value = _state.value.copy(joinLoading = true, joinError = null)
            try {
                val joinResult = powGate.execute(
                    scope = "community_join",
                    action = communityAltchaAction(communityId),
                    onSolvingProofOfWorkChanged = { solving ->
                        _state.value = _state.value.copy(solvingProofOfWork = solving)
                    },
                ) { altchaHeader ->
                    communityRepository.joinCommunity(communityId, altchaHeader)
                }
                val currentPreview = _state.value.preview
                val nextPreview = if (joinResult.status == "joined" && currentPreview != null) {
                    currentPreview.copy(
                        viewerMembershipStatus = "member",
                        viewerFollowing = true,
                        followerCount = if (currentPreview.viewerFollowing == true) {
                            currentPreview.followerCount
                        } else {
                            currentPreview.followerCount?.plus(1)
                        },
                    )
                } else {
                    currentPreview
                }
                _state.value = _state.value.copy(
                    eligibility = communityRepository.getJoinEligibility(communityId),
                    preview = nextPreview,
                    joinLoading = false,
                    solvingProofOfWork = false,
                )
            } catch (e: Exception) {
                if (e is CancellationException) {
                    _state.value = _state.value.copy(
                        joinLoading = false,
                        solvingProofOfWork = false,
                    )
                    throw e
                }
                _state.value = _state.value.copy(
                    joinLoading = false,
                    solvingProofOfWork = false,
                    joinError = e.message ?: "Could not join community",
                )
            }
        }
    }

    fun cancelJoin() {
        joinJob?.cancel()
        joinJob = null
        _state.value = _state.value.copy(
            joinLoading = false,
            solvingProofOfWork = false,
        )
    }

    fun toggleFollow() {
        val communityId = currentCommunityId ?: return
        val preview = _state.value.preview ?: return
        if (!currentHasSession) return

        viewModelScope.launch {
            _state.value = _state.value.copy(followLoading = true, followError = null)
            try {
                val response = if (preview.viewerFollowing == true) {
                    communityRepository.unfollowCommunity(communityId)
                } else {
                    communityRepository.followCommunity(communityId)
                }
                _state.value = _state.value.copy(
                    preview = preview.copy(
                        viewerFollowing = response.following,
                        followerCount = response.followerCount,
                    ),
                    followLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    followLoading = false,
                    followError = e.message ?: "Could not update follow state",
                )
            }
        }
    }

    fun votePost(postId: String, value: Int) {
        val current = _state.value
        if (postId in current.votingPostIds) return

        val previousPost = current.posts.firstOrNull { it.post.postId == postId } ?: return
        if (previousPost.viewerVote == value) return

        _state.value = current.copy(
            posts = current.posts.withPostVote(postId, value),
            voteError = null,
            votingPostIds = current.votingPostIds + postId,
        )

        viewModelScope.launch {
            try {
                val response = postRepository.votePost(postId, value)
                _state.value = _state.value.copy(
                    posts = _state.value.posts.withPostVote(postId, response.value),
                    votingPostIds = _state.value.votingPostIds - postId,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    posts = _state.value.posts.replacePost(postId, previousPost),
                    voteError = e.message ?: "Could not update vote",
                    votingPostIds = _state.value.votingPostIds - postId,
                )
            }
        }
    }

    fun toggleSongPlayback(post: LocalizedPostResponse) {
        app.songPlaybackController.toggle(post)
    }

    fun pauseSongPlayback() {
        app.songPlaybackController.pause()
    }

    fun playVideoPreview(post: LocalizedPostResponse) {
        app.videoPlaybackController.playPreview(post)
    }

    fun pauseVideoPlayback() {
        app.videoPlaybackController.pause()
    }

    fun currentVideoPlayer() = app.videoPlaybackController.currentPlayer()
}

private val communityTabOptions = listOf(
    ChipOption("feed", "Feed"),
    ChipOption("about", "About"),
)

private val communitySortOptions = listOf(
    ChipOption("best", "Best"),
    ChipOption("new", "New"),
    ChipOption("top", "Top"),
)

@Composable
private fun CommunitySortMenu(
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = PhosphorIcons.SlidersHorizontal,
                contentDescription = "Sort feed",
                tint = PirateTokens.colors.textPrimary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            communitySortOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = PirateTokens.colors.textPrimary,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    },
                    trailingIcon = if (option.value == selectedValue) {
                        {
                            Text(
                                text = "Selected",
                                color = PirateTokens.colors.textSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    } else {
                        null
                    },
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
    hasSession: Boolean,
    viewerUserId: String?,
    onNavigateToPost: (String) -> Unit,
    onNavigateToCompose: () -> Unit,
    onVerifyWithSelf: (String, List<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val videoPlaybackState by viewModel.videoPlaybackState.collectAsState()
    var activeTab by rememberSaveable(communityId) { mutableStateOf("feed") }
    val listState = rememberLazyListState()
    val preview = state.preview
    val community = state.community
    val eligibility = state.eligibility
    val viewerIsMember = eligibility?.status == "already_joined" ||
        preview?.viewerMembershipStatus == "member"
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val canCreatePost = hasSession && (
        viewerIsMember ||
            ownsCommunity(viewerUserId, community) ||
            viewerCanModerateCommunity(viewerUserId, preview)
        )

    LaunchedEffect(communityId, hasSession) {
        viewModel.loadCommunity(communityId, hasSession)
    }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    viewModel.pauseSongPlayback()
                    viewModel.pauseVideoPlayback()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    LaunchedEffect(activeTab, state.posts) {
        if (activeTab != "feed") {
            viewModel.pauseVideoPlayback()
            return@LaunchedEffect
        }
        snapshotFlow { mostVisibleVideoPostId(listState, state.posts) }
            .distinctUntilChanged()
            .collect { postId ->
                val post = postId?.let { nextPostId ->
                    state.posts.firstOrNull { it.post.postId == nextPostId }
                }
                if (post != null) {
                    viewModel.playVideoPreview(post)
                } else {
                    viewModel.pauseVideoPlayback()
                }
            }
    }

    LaunchedEffect(activeTab, state.nextPostsCursor, state.postsLoadingMore) {
        if (activeTab != "feed" || state.nextPostsCursor == null) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 4
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) viewModel.loadMorePosts() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhosphorIcons.CaretLeft,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (activeTab == "feed") {
                        CommunitySortMenu(
                            selectedValue = state.activeSort,
                            onSelected = viewModel::setSort,
                        )
                    }
                    if (canCreatePost) {
                        IconButton(onClick = onNavigateToCompose) {
                            Icon(
                                imageVector = PhosphorIcons.Plus,
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
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when {
                state.loading -> {
                    FeedSkeletons(count = 3, modifier = Modifier.fillMaxSize())
                }

                state.error != null -> {
                    StatusCard(
                        title = "Community unavailable",
                        description = state.error.orEmpty(),
                        tone = StatusTone.Warning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }

                community != null || preview != null -> {
                    val displayName = community?.displayName ?: preview?.displayName ?: "Community"
                    val description = community?.description ?: preview?.description
                    val routeLabel = communityRouteLabel(
                        community?.routeSlug ?: preview?.routeSlug ?: preview?.communityId ?: communityId,
                        communityId,
                    )
                    val memberCount = preview?.memberCount ?: community?.memberCount
                    val followerCount = preview?.followerCount ?: community?.followerCount
                    val avatarSrc = community?.avatarRef ?: preview?.avatarRef
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(PirateTokens.colors.bgPage),
                    ) {
                        item {
                            CommunityHero(
                                displayName = displayName,
                                avatarSrc = avatarSrc,
                                routeLabel = routeLabel,
                                description = description,
                                memberCount = memberCount,
                                followerCount = followerCount,
                                eligibilityText = eligibility?.let(::communityStatusText),
                                canCreatePost = canCreatePost,
                                eligibility = eligibility,
                                followLoading = state.followLoading,
                                hasSession = hasSession,
                                isFollowing = preview?.viewerFollowing == true,
                                isMember = viewerIsMember,
                                joinLoading = state.joinLoading,
                                solvingProofOfWork = state.solvingProofOfWork,
                                followError = state.followError,
                                joinError = state.joinError,
                                onJoin = viewModel::joinCommunity,
                                onToggleFollow = viewModel::toggleFollow,
                                onVerify = {
                                    onVerifyWithSelf(
                                        eligibility?.suggestedVerificationIntent ?: "community_join",
                                        eligibility?.missingCapabilities
                                            ?.filter(::isSelfCapability)
                                            .orEmpty(),
                                    )
                                },
                            )
                        }

                        item {
                            CommunityTabs(
                                options = communityTabOptions,
                                selectedValue = activeTab,
                                onSelected = { activeTab = it },
                            )
                        }

                        if (activeTab == "feed") {
                            if (state.postsPaginationError != null) {
                                item {
                                    StatusCard(
                                        title = "More posts unavailable",
                                        description = state.postsPaginationError.orEmpty(),
                                        tone = StatusTone.Warning,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    )
                                }
                            }

                            if (state.voteError != null) {
                                item {
                                    StatusCard(
                                        title = "Vote unavailable",
                                        description = state.voteError.orEmpty(),
                                        tone = StatusTone.Warning,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    )
                                }
                            }

                            playbackState.error?.let { playbackError ->
                                item {
                                    StatusCard(
                                        title = "Playback unavailable",
                                        description = playbackError,
                                        tone = StatusTone.Warning,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    )
                                }
                            }

                            videoPlaybackState.error?.let { playbackError ->
                                item {
                                    StatusCard(
                                        title = "Video unavailable",
                                        description = playbackError,
                                        tone = StatusTone.Warning,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    )
                                }
                            }

                            if (state.posts.isEmpty()) {
                                item {
                                    EmptyFeedState(
                                        message = "No posts yet.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    )
                                }
                            } else {
                                items(state.posts, key = { it.post.postId }) { postResp ->
                                    val isVoting = postResp.post.postId in state.votingPostIds
                                    if (postResp.post.postType == "song") {
                                        val postIsPlaying = playbackState.postId == postResp.post.postId && playbackState.isPlaying
                                        val postIsBuffering = playbackState.postId == postResp.post.postId && playbackState.isBuffering
                                        SongPostRow(
                                            post = postResp,
                                            isVoting = isVoting,
                                            canPlay = resolveSongAudioUrl(postResp) != null,
                                            isBuffering = postIsBuffering,
                                            isPlaying = postIsPlaying,
                                            onClick = { onNavigateToPost(postResp.post.postId) },
                                            onPlayPause = { viewModel.toggleSongPlayback(postResp) },
                                            onVote = { value -> viewModel.votePost(postResp.post.postId, value) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else if (isVideoPost(postResp)) {
                                        val postIsCurrentVideo = videoPlaybackState.postId == postResp.post.postId
                                        VideoPostRow(
                                            post = postResp,
                                            isVoting = isVoting,
                                            isActive = postIsCurrentVideo,
                                            isBuffering = postIsCurrentVideo && videoPlaybackState.isBuffering,
                                            player = if (postIsCurrentVideo) viewModel.currentVideoPlayer() else null,
                                            onClick = { onNavigateToPost(postResp.post.postId) },
                                            onVote = { value -> viewModel.votePost(postResp.post.postId, value) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        CommunityPostRow(
                                            post = postResp,
                                            isVoting = isVoting,
                                            onClick = { onNavigateToPost(postResp.post.postId) },
                                            onVote = { value -> viewModel.votePost(postResp.post.postId, value) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            if (state.postsLoadingMore) {
                                item {
                                    CircularProgressIndicator(
                                        color = PirateTokens.colors.accentBrand,
                                        modifier = Modifier.padding(20.dp).size(24.dp),
                                    )
                                }
                            }
                        } else {
                            preview?.membershipGateSummaries
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { gates ->
                                    item {
                                        MetadataSection(
                                            title = "Access requirements",
                                            rows = gates.map(::gateSummaryText),
                                        )
                                    }
                                }

                            preview?.rules
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { rules ->
                                    item {
                                        RulesSection(rules = rules)
                                    }
                                }

                            preview?.referenceLinks
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { links ->
                                    item {
                                        MetadataSection(
                                            title = "Links",
                                            rows = links.map(::referenceLinkText),
                                        )
                                    }
                                }
                        }
                    }
                }
            }

            if (state.solvingProofOfWork) {
                ModalBottomSheet(
                    onDismissRequest = viewModel::cancelJoin,
                    containerColor = PirateTokens.colors.bgElevated,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Verifying access",
                            style = MaterialTheme.typography.titleMedium,
                            color = PirateTokens.colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Solving proof-of-work for this community.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PirateTokens.colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        PirateButton(
                            text = "Cancel",
                            onClick = viewModel::cancelJoin,
                            variant = ButtonVariant.Outline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun communityRouteLabel(routeSlugOrId: String, communityId: String): String {
    val route = routeSlugOrId.ifBlank { communityId }
    return formatCommunityRouteLabel(communityId = communityId, routeSlug = route)
}

private fun ownsCommunity(viewerUserId: String?, community: Community?): Boolean =
    sameUserId(viewerUserId, community?.createdByUser)

private fun viewerCanModerateCommunity(viewerUserId: String?, preview: CommunityPreview?): Boolean {
    if (viewerUserId.isNullOrBlank() || preview == null) return false
    if (preview.viewerCommunityRole.isCommunityModeratorRole()) return true
    if (sameUserId(viewerUserId, preview.owner?.user)) return true
    return preview.moderators.any { roleHolder ->
        sameUserId(viewerUserId, roleHolder.user) && roleHolder.role.isCommunityModeratorRole()
    }
}

private fun String?.isCommunityModeratorRole(): Boolean =
    this == "owner" || this == "admin" || this == "moderator"

private fun sameUserId(left: String?, right: String?): Boolean {
    val normalizedLeft = left?.trim()?.takeIf { it.isNotBlank() } ?: return false
    val normalizedRight = right?.trim()?.takeIf { it.isNotBlank() } ?: return false
    return normalizedLeft == normalizedRight
}

private fun communityStatusText(eligibility: JoinEligibility): String =
    when (eligibility.status) {
        "already_joined" -> ""
        "joinable" -> ""
        "requestable" -> "Membership requires a request."
        "verification_required" -> if (requiresProofOfWork(eligibility)) {
            ""
        } else {
            val provider = eligibility.suggestedVerificationProvider ?: eligibility.humanVerificationLane
            "Verification required with ${verificationProviderLabel(provider)}."
        }
        "pending_request" -> "Your join request is pending."
        "gate_failed" -> eligibility.failureReason ?: "You do not meet this community's gate."
        "banned" -> "You cannot join this community."
        else -> eligibility.status.replace('_', ' ')
    }

private fun gateSummaryText(gate: MembershipGateSummary): String =
    when (gate.gateType) {
        "altcha_pow" -> "Proof-of-work check"
        "unique_human" -> {
            val providers = gate.acceptedProviders
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " via ") { verificationProviderLabel(it) }
                .orEmpty()
            "Unique human proof$providers"
        }
        "age_over_18" -> "Age 18+"
        "minimum_age" -> "Age ${gate.requiredMinimumAge ?: gate.requiredValue ?: "required"}+"
        "nationality" -> "Nationality: ${formatCountryRequirement(gate)}"
        "gender" -> "Document marker: ${gate.requiredValue ?: "required"}"
        "wallet_score" -> "Wallet score: ${gate.minimumScore ?: gate.requiredValue ?: "required"}+"
        "erc721_holding" -> "NFT gate: ${gate.assetFilterLabel ?: gate.contractAddress ?: "configured"}"
        "erc721_inventory_match" -> "Inventory gate: ${gate.assetFilterLabel ?: gate.assetCategory ?: "required"}"
        else -> gate.gateType.replace('_', ' ')
    }

private fun verificationProviderLabel(provider: String?): String =
    when (provider) {
        "self" -> "Self"
        "very" -> "Very"
        "passport" -> "Passport"
        else -> provider?.replace('_', ' ') ?: "verification"
    }

private fun formatCountryRequirement(gate: MembershipGateSummary): String {
    val countries = gate.requiredValues?.takeIf { it.isNotEmpty() }
        ?: gate.requiredValue?.takeIf { it.isNotBlank() }?.let(::listOf)
        ?: return "required"
    return countries.joinToString(", ") { countryCodeToName(it) }
}

private fun countryCodeToName(value: String): String {
    val code = value.trim()
    if (code.length != 2) return code.ifBlank { "required" }
    return runCatching {
        Locale.Builder()
            .setRegion(code.uppercase(Locale.ROOT))
            .build()
            .getDisplayCountry(Locale.getDefault())
            .takeIf { it.isNotBlank() }
    }.getOrNull() ?: code
}

private fun requiresProofOfWork(eligibility: JoinEligibility?): Boolean =
    eligibility?.missingCapabilities?.contains("altcha_pow") == true ||
        eligibility?.membershipGateSummaries?.any { it.gateType == "altcha_pow" } == true

private fun communityAltchaAction(communityId: String): String {
    val trimmed = communityId.trim()
    val publicCommunityId = if (trimmed.startsWith("com_")) trimmed else "com_$trimmed"
    return "community:$publicCommunityId"
}

private fun isSelfCapability(capability: String): Boolean =
    capability == "unique_human" ||
        capability == "age_over_18" ||
        capability == "minimum_age" ||
        capability == "nationality" ||
        capability == "gender"

private fun referenceLinkText(link: CommunityReferenceLink): String {
    val label = link.label ?: link.platform ?: "Link"
    return "$label: ${link.url}"
}

private fun durationLabel(durationMs: Long?): String? {
    val totalSeconds = durationMs?.takeIf { it > 0 }?.div(1000) ?: return null
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun postAuthorLabel(post: LocalizedPostResponse): String =
    post.post.anonymousLabel
        ?: post.post.authorUserId?.take(16)?.let { "$it.pirate" }
        ?: "anonymous"

private fun mostVisibleVideoPostId(
    listState: LazyListState,
    posts: List<LocalizedPostResponse>,
): String? {
    val videoPostIds = posts
        .filter(::isVideoPost)
        .map { it.post.postId }
        .toSet()
    if (videoPostIds.isEmpty()) return null

    val layoutInfo = listState.layoutInfo
    return layoutInfo.visibleItemsInfo
        .mapNotNull { itemInfo ->
            val postId = itemInfo.key as? String
            if (postId !in videoPostIds) return@mapNotNull null
            val visibleStart = itemInfo.offset.coerceAtLeast(layoutInfo.viewportStartOffset)
            val visibleEnd = (itemInfo.offset + itemInfo.size).coerceAtMost(layoutInfo.viewportEndOffset)
            val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
            val visibleRatio = if (itemInfo.size > 0) visiblePx.toFloat() / itemInfo.size.toFloat() else 0f
            if (visibleRatio >= 0.5f) postId to visiblePx else null
        }
        .maxByOrNull { it.second }
        ?.first
}

private fun List<LocalizedPostResponse>.withPostVote(postId: String, value: Int): List<LocalizedPostResponse> =
    map { post ->
        if (post.post.postId != postId) return@map post
        val previousValue = post.viewerVote
        if (previousValue == value) return@map post
        post.copy(
            upvoteCount = adjustedVoteCount(post.upvoteCount, previousValue, value, 1),
            downvoteCount = adjustedVoteCount(post.downvoteCount, previousValue, value, -1),
            viewerVote = value,
        )
    }

private fun List<LocalizedPostResponse>.replacePost(postId: String, previousPost: LocalizedPostResponse): List<LocalizedPostResponse> =
    map { post -> if (post.post.postId == postId) previousPost else post }

@Composable
private fun CommunityHeaderActions(
    canCreatePost: Boolean,
    eligibility: JoinEligibility?,
    followLoading: Boolean,
    hasSession: Boolean,
    isFollowing: Boolean,
    isMember: Boolean,
    joinLoading: Boolean,
    solvingProofOfWork: Boolean,
    onJoin: () -> Unit,
    onToggleFollow: () -> Unit,
    onVerify: () -> Unit,
) {
    val primaryActionLabel = when (eligibility?.status) {
        "joinable", "verification_required" -> "Join"
        "requestable" -> "Request to join"
        else -> null
    }
    val showPrimaryAction = !canCreatePost && primaryActionLabel != null
    val showFollowAction = hasSession && !isMember

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMember) {
            PirateButton(
                text = "Joined",
                onClick = {},
                enabled = false,
                modifier = Modifier.weight(1f),
            )
            return@Row
        }

        if (showPrimaryAction) {
            if (eligibility?.status == "verification_required" && !requiresProofOfWork(eligibility)) {
                if (eligibility.suggestedVerificationProvider == "self") {
                    PirateButton(
                        text = "Verify with ID",
                        onClick = onVerify,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                PirateButton(
                    text = if (solvingProofOfWork) "Solving proof..." else primaryActionLabel.orEmpty(),
                    onClick = onJoin,
                    loading = joinLoading || solvingProofOfWork,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (showFollowAction) {
            PirateButton(
                text = if (isFollowing) "Following" else "Follow",
                onClick = onToggleFollow,
                loading = followLoading,
                variant = ButtonVariant.Outline,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetadataSection(
    title: String,
    rows: List<String>,
    modifier: Modifier = Modifier,
) {
    PirateCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { row ->
                Text(
                    text = row,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun CommunityHero(
    displayName: String,
    avatarSrc: String?,
    routeLabel: String,
    description: String?,
    memberCount: Int?,
    followerCount: Int?,
    eligibilityText: String?,
    canCreatePost: Boolean,
    eligibility: JoinEligibility?,
    followLoading: Boolean,
    hasSession: Boolean,
    isFollowing: Boolean,
    isMember: Boolean,
    joinLoading: Boolean,
    solvingProofOfWork: Boolean,
    followError: String?,
    joinError: String?,
    onJoin: () -> Unit,
    onToggleFollow: () -> Unit,
    onVerify: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .background(Color(0xFF351A62)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFF231226).copy(alpha = 0.7f)),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            CommunityAvatar(
                label = displayName,
                avatarSrc = avatarSrc,
                modifier = Modifier.padding(top = 0.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                color = PirateTokens.colors.textPrimary,
            )
            Text(
                text = routeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            description?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textPrimary,
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                memberCount?.let { CommunityMeta("$it members") }
                followerCount?.let { CommunityMeta("$it followers") }
            }
            eligibilityText?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            CommunityHeaderActions(
                canCreatePost = canCreatePost,
                eligibility = eligibility,
                followLoading = followLoading,
                hasSession = hasSession,
                isFollowing = isFollowing,
                isMember = isMember,
                joinLoading = joinLoading,
                solvingProofOfWork = solvingProofOfWork,
                onJoin = onJoin,
                onToggleFollow = onToggleFollow,
                onVerify = onVerify,
            )
            followError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                FormNote(message = it, tone = FormTone.Error)
            }
            joinError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                FormNote(message = it, tone = FormTone.Error)
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CommunityMeta(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = PirateTokens.colors.textSecondary,
    )
}

@Composable
private fun CommunityTabs(
    options: List<ChipOption>,
    selectedValue: String,
    onSelected: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            options.forEach { option ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelected(option.value) }
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (option.value == selectedValue) {
                            PirateTokens.colors.textPrimary
                        } else {
                            PirateTokens.colors.textSecondary
                        },
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (option.value == selectedValue) {
                                    PirateTokens.colors.accentDanger
                                } else {
                                    Color.Transparent
                                },
                            ),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(PirateTokens.colors.borderSoft),
        )
    }
}

@Composable
private fun RulesSection(
    rules: List<CommunityRule>,
    modifier: Modifier = Modifier,
) {
    PirateCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Rules",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rules.sortedBy { it.position ?: Int.MAX_VALUE }.forEach { rule ->
                Column {
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textPrimary,
                    )
                    if (!rule.body.isNullOrBlank()) {
                        Text(
                            text = rule.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = PirateTokens.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostEngagementRow(
    score: Int,
    viewerVote: Int?,
    comments: Int,
    isVoting: Boolean,
    onVote: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VoteControl(
            score = score,
            viewerVote = viewerVote,
            enabled = !isVoting,
            onVote = onVote,
        )
        Surface(
            shape = RoundedCornerShape(PirateTokens.radius.full),
            color = PirateTokens.colors.surfaceSubtle,
            border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = PhosphorIcons.ChatCircle,
                    contentDescription = null,
                    tint = PirateTokens.colors.textSecondary,
                )
                Text(
                    text = comments.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = PirateTokens.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun SongPostRow(
    post: LocalizedPostResponse,
    isVoting: Boolean,
    canPlay: Boolean,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = songDisplayTitle(post)
    val body = post.translatedBody ?: post.post.body
    val score = post.upvoteCount - post.downvoteCount
    val comments = post.threadSnapshot?.commentCount ?: 0
    val authorLabel = postAuthorLabel(post)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CommunityPostByline(authorLabel = authorLabel, timestampLabel = relativeTimeLabel(post.post.createdAt))
            SongSummaryRow(
                post = post,
                canPlay = canPlay,
                isBuffering = isBuffering,
                isPlaying = isPlaying,
                onPlayPause = onPlayPause,
                body = body?.takeIf { it != title },
                artworkSize = 76.dp,
                titleStyle = MaterialTheme.typography.titleMedium,
                durationStyle = MaterialTheme.typography.bodySmall,
                bodyStyle = MaterialTheme.typography.bodySmall,
            )
            PostEngagementRow(
                score = score,
                viewerVote = post.viewerVote,
                comments = comments,
                isVoting = isVoting,
                onVote = onVote,
            )
        }
    }
}

@Composable
private fun VideoPostRow(
    post: LocalizedPostResponse,
    isVoting: Boolean,
    isActive: Boolean,
    isBuffering: Boolean,
    player: Player?,
    onClick: () -> Unit,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = post.translatedTitle ?: post.post.title ?: post.post.caption ?: "Video"
    val body = post.translatedBody ?: post.post.body
    val score = post.upvoteCount - post.downvoteCount
    val comments = post.threadSnapshot?.commentCount ?: 0
    val posterSrc = resolveVideoPosterUrl(post)
    val duration = durationLabel(post.post.mediaRefs.firstOrNull { it.mimeType?.startsWith("video/") == true }?.durationMs)
    val authorLabel = postAuthorLabel(post)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CommunityPostByline(authorLabel = authorLabel, timestampLabel = relativeTimeLabel(post.post.createdAt))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio(post))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (isActive && player != null) {
                    VideoPlayerView(
                        player = player,
                        showControls = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (posterSrc != null) {
                    AsyncImage(
                        model = posterSrc,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = PhosphorIcons.VideoCamera,
                        contentDescription = null,
                        tint = PirateTokens.colors.textSecondary,
                    )
                }
                if (!isActive || isBuffering) {
                    Surface(
                        shape = RoundedCornerShape(PirateTokens.radius.full),
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Icon(
                            imageVector = if (isBuffering) PhosphorIcons.MusicNotes else PhosphorIcons.Play,
                            contentDescription = if (isBuffering) "Loading video" else "Open video",
                            tint = Color.White,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                duration?.let {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(PirateTokens.radius.full),
                        color = Color.Black.copy(alpha = 0.65f),
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            body?.takeIf { it.isNotBlank() && it != title }?.let { bodyText ->
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PostEngagementRow(
                score = score,
                viewerVote = post.viewerVote,
                comments = comments,
                isVoting = isVoting,
                onVote = onVote,
            )
        }
    }
}

@Composable
private fun CommunityPostRow(
    post: LocalizedPostResponse,
    isVoting: Boolean,
    onClick: () -> Unit,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = post.translatedTitle ?: post.post.title ?: post.post.caption ?: "Untitled post"
    val body = post.translatedBody ?: post.post.body
    val score = post.upvoteCount - post.downvoteCount
    val comments = post.threadSnapshot?.commentCount ?: 0
    val authorLabel = postAuthorLabel(post)

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CommunityPostByline(authorLabel = authorLabel, timestampLabel = relativeTimeLabel(post.post.createdAt))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
            )
            body?.takeIf { it.isNotBlank() && it != title }?.let { bodyText ->
                Text(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textPrimary,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PostEngagementRow(
                score = score,
                viewerVote = post.viewerVote,
                comments = comments,
                isVoting = isVoting,
                onVote = onVote,
            )
        }
    }
}

@Composable
private fun CommunityPostByline(authorLabel: String, timestampLabel: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(label = authorLabel)
        Text(
            text = "$authorLabel \u2022 $timestampLabel",
            style = MaterialTheme.typography.labelLarge,
            color = PirateTokens.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CommunityAvatar(label: String, avatarSrc: String?, modifier: Modifier = Modifier) {
    val colors = communityPlaceholderColors(label)
    val resolvedAvatar = resolvePublicMediaSrc(avatarSrc)
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(PirateTokens.radius.full))
            .background(colors.first),
        contentAlignment = Alignment.Center,
    ) {
        if (resolvedAvatar != null) {
            AsyncImage(
                model = resolvedAvatar,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = initials(label),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.second,
            )
        }
    }
}

@Composable
private fun UserAvatar(label: String) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(PirateTokens.radius.full))
            .background(Color(0xFFF4BF45)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ":)",
            style = MaterialTheme.typography.titleSmall,
            color = Color(0xFF101010),
        )
    }
}

private fun initials(label: String): String =
    label
        .split(" ", "-", "_")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "C" }

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
