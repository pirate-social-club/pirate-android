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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.Community
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CommunityReferenceLink
import sc.pirate.app.api.model.CommunityRule
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.MembershipGateSummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ChipOption
import sc.pirate.app.ui.EmptyFeedState
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.PirateChipRow
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.adjustedVoteCount

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
    private val postRepository get() = app.repositories.postRepository

    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    private var currentCommunityId: String? = null
    private var currentHasSession: Boolean = false

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
                    _state.value = CommunityUiState(
                        preview = preview,
                        posts = posts.items,
                        nextPostsCursor = posts.nextCursor,
                        activeSort = sort,
                        loading = false,
                    )
                    return@launch
                }

                _state.value = coroutineScope {
                    val community = async { communityRepository.getCommunity(communityId) }
                    val preview = async { communityRepository.getPreview(communityId) }
                    val eligibility = async { communityRepository.getJoinEligibility(communityId) }
                    val posts = async {
                        communityRepository.listPosts(
                            communityId = communityId,
                            limit = 25,
                            sort = sort,
                        )
                    }

                    val postPage = posts.await()
                    CommunityUiState(
                        community = community.await(),
                        preview = preview.await(),
                        eligibility = eligibility.await(),
                        posts = postPage.items,
                        nextPostsCursor = postPage.nextCursor,
                        activeSort = sort,
                        loading = false,
                    )
                }
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
                    posts = (_state.value.posts + page.items).distinctBy { it.post.postId },
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
        viewModelScope.launch {
            _state.value = _state.value.copy(joinLoading = true, joinError = null)
            try {
                communityRepository.joinCommunity(communityId)
                _state.value = _state.value.copy(
                    eligibility = communityRepository.getJoinEligibility(communityId),
                    joinLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    joinLoading = false,
                    joinError = e.message ?: "Could not join community",
                )
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel,
    communityId: String,
    hasSession: Boolean,
    onNavigateToPost: (String) -> Unit,
    onNavigateToCompose: () -> Unit,
    onVerifyWithSelf: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var activeTab by rememberSaveable(communityId) { mutableStateOf("feed") }
    val preview = state.preview
    val community = state.community
    val eligibility = state.eligibility
    val canOpenComposer = community != null || preview != null

    LaunchedEffect(communityId, hasSession) {
        viewModel.loadCommunity(communityId, hasSession)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = community?.displayName ?: preview?.displayName ?: "Community",
                        color = PirateTokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                    if (canOpenComposer) {
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                    }
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
                    val routeLabel = communityRouteLabel(community?.routeSlug ?: preview?.communityId ?: communityId, communityId)
                    val memberCount = preview?.memberCount ?: community?.memberCount
                    val followerCount = preview?.followerCount ?: community?.followerCount
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            PirateCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = routeLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PirateTokens.colors.textSecondary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = PirateTokens.colors.textPrimary,
                                )
                                description?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PirateTokens.colors.textSecondary,
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    memberCount?.let {
                                        Text(
                                            text = "$it members",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = PirateTokens.colors.textSecondary,
                                        )
                                    }
                                    followerCount?.let {
                                        Text(
                                            text = "$it followers",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = PirateTokens.colors.textSecondary,
                                        )
                                    }
                                }
                                eligibility?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = communityStatusText(it),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = PirateTokens.colors.textSecondary,
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                CommunityHeaderActions(
                                    canCreatePost = canCreatePost,
                                    eligibility = eligibility,
                                    followLoading = state.followLoading,
                                    hasSession = hasSession,
                                    isFollowing = preview?.viewerFollowing == true,
                                    joinLoading = state.joinLoading,
                                    onJoin = viewModel::joinCommunity,
                                    onToggleFollow = viewModel::toggleFollow,
                                    onVerify = {
                                        onVerifyWithSelf(
                                            eligibility?.suggestedVerificationIntent ?: "community_join",
                                        )
                                    },
                                )
                                state.followError?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FormNote(message = it, tone = FormTone.Error)
                                }
                                state.joinError?.let {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FormNote(message = it, tone = FormTone.Error)
                                }
                            }
                        }

                        item {
                            PirateChipRow(
                                options = communityTabOptions,
                                selectedValue = activeTab,
                                onSelected = { activeTab = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (activeTab == "feed") {
                            item {
                                PirateChipRow(
                                    options = communitySortOptions,
                                    selectedValue = state.activeSort,
                                    onSelected = viewModel::setSort,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            if (state.postsPaginationError != null) {
                                item {
                                    StatusCard(
                                        title = "More posts unavailable",
                                        description = state.postsPaginationError.orEmpty(),
                                        tone = StatusTone.Warning,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            if (state.voteError != null) {
                                item {
                                    StatusCard(
                                        title = "Vote unavailable",
                                        description = state.voteError.orEmpty(),
                                        tone = StatusTone.Warning,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            if (state.posts.isEmpty()) {
                                item {
                                    EmptyFeedState(
                                        message = "No posts yet.",
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                items(state.posts, key = { it.post.postId }) { postResp ->
                                    val isVoting = postResp.post.postId in state.votingPostIds
                                    CommunityPostRow(
                                        post = postResp,
                                        isVoting = isVoting,
                                        onClick = { onNavigateToPost(postResp.post.postId) },
                                        onVote = { value -> viewModel.votePost(postResp.post.postId, value) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            if (state.nextPostsCursor != null) {
                                item {
                                    PirateButton(
                                        text = if (state.postsLoadingMore) "Loading" else "Load more",
                                        onClick = viewModel::loadMorePosts,
                                        loading = state.postsLoadingMore,
                                        modifier = Modifier.fillMaxWidth(),
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
        }
    }
}

private fun communityRouteLabel(routeSlugOrId: String, communityId: String): String {
    val route = routeSlugOrId.ifBlank { communityId }
    return "c/$route"
}

private fun communityStatusText(eligibility: JoinEligibility): String =
    when (eligibility.status) {
        "already_joined" -> "You are a member."
        "joinable" -> "Join to post and reply here."
        "requestable" -> "Membership requires a request."
        "verification_required" -> {
            val provider = eligibility.suggestedVerificationProvider ?: eligibility.humanVerificationLane
            "Verification required with $provider."
        }
        "pending_request" -> "Your join request is pending."
        "gate_failed" -> eligibility.failureReason ?: "You do not meet this community's gate."
        "banned" -> "You cannot join this community."
        else -> eligibility.status
    }

private fun gateSummaryText(gate: MembershipGateSummary): String =
    when (gate.gateType) {
        "self_minimum_age" -> "Age ${gate.requiredMinimumAge ?: gate.requiredValue ?: "required"}+"
        "self_nationality" -> "Nationality: ${gate.requiredValues?.joinToString(", ") ?: gate.requiredValue ?: "required"}"
        "self_excluded_nationality" -> "Excluded nationality: ${gate.excludedValues?.joinToString(", ") ?: "configured"}"
        "self_gender" -> "Document marker: ${gate.requiredValue ?: "required"}"
        "passport_score" -> "Passport score: ${gate.minimumScore ?: gate.requiredValue ?: "required"}+"
        "wallet_nft" -> "NFT gate: ${gate.assetFilterLabel ?: gate.contractAddress ?: "configured"}"
        "courtyard_inventory" -> "Courtyard inventory: ${gate.assetFilterLabel ?: gate.assetCategory ?: "required"}"
        else -> gate.gateType.replace('_', ' ')
    }

private fun referenceLinkText(link: CommunityReferenceLink): String {
    val label = link.label ?: link.platform ?: "Link"
    return "$label: ${link.url}"
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
    joinLoading: Boolean,
    onJoin: () -> Unit,
    onToggleFollow: () -> Unit,
    onVerify: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hasSession) {
            PirateButton(
                text = if (isFollowing) "Following" else "Follow",
                onClick = onToggleFollow,
                loading = followLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!canCreatePost) {
            when (eligibility?.status) {
                "joinable" -> PirateButton(
                    text = "Join",
                    onClick = onJoin,
                    loading = joinLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                "requestable" -> PirateButton(
                    text = "Request to join",
                    onClick = onJoin,
                    loading = joinLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                "verification_required" ->
                    if (eligibility.suggestedVerificationProvider == "self") {
                        PirateButton(
                            text = "Verify with ID",
                            onClick = onVerify,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
            }
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
private fun CommunityPostRow(
    post: LocalizedPostResponse,
    isVoting: Boolean,
    onClick: () -> Unit,
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PirateCard(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = post.translatedTitle ?: post.post.title ?: post.post.caption ?: "Untitled post",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        (post.translatedBody ?: post.post.body)
            ?.takeIf { it.isNotBlank() }
            ?.let { body ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body.take(220),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "${post.upvoteCount - post.downvoteCount} score | ${post.threadSnapshot?.commentCount ?: 0} comments",
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PirateButton(
                text = if (post.viewerVote == 1) "Upvoted" else "Upvote",
                onClick = { onVote(1) },
                enabled = !isVoting,
                modifier = Modifier.weight(1f),
            )
            PirateButton(
                text = if (post.viewerVote == -1) "Downvoted" else "Downvote",
                onClick = { onVote(-1) },
                enabled = !isVoting,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        PirateButton(
            text = "Open post",
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
