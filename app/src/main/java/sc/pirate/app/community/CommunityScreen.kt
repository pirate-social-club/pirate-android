package sc.pirate.app.community

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.Community
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CommunityReferenceLink
import sc.pirate.app.api.model.CommunityRule
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.MembershipGateSummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.EmptyFeedState
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.PirateButton

data class CommunityUiState(
    val community: Community? = null,
    val preview: CommunityPreview? = null,
    val eligibility: JoinEligibility? = null,
    val posts: List<LocalizedPostResponse> = emptyList(),
    val nextPostsCursor: String? = null,
    val loading: Boolean = true,
    val postsLoadingMore: Boolean = false,
    val joinLoading: Boolean = false,
    val error: String? = null,
    val postsPaginationError: String? = null,
    val joinError: String? = null,
)

class CommunityViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository

    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    fun loadCommunity(communityId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                _state.value = coroutineScope {
                    val community = async { communityRepository.getCommunity(communityId) }
                    val preview = async { communityRepository.getPreview(communityId) }
                    val eligibility = async { communityRepository.getJoinEligibility(communityId) }
                    val posts = async {
                        communityRepository.listPosts(
                            communityId = communityId,
                            limit = 25,
                            sort = "new",
                        )
                    }

                    val postPage = posts.await()
                    CommunityUiState(
                        community = community.await(),
                        preview = preview.await(),
                        eligibility = eligibility.await(),
                        posts = postPage.items,
                        nextPostsCursor = postPage.nextCursor,
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

    fun loadMorePosts(communityId: String) {
        val current = _state.value
        val cursor = current.nextPostsCursor ?: return
        if (current.postsLoadingMore) return

        _state.value = current.copy(
            postsLoadingMore = true,
            postsPaginationError = null,
        )

        viewModelScope.launch {
            try {
                val page = communityRepository.listPosts(
                    communityId = communityId,
                    cursor = cursor,
                    limit = 25,
                    sort = "new",
                )
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

    fun joinCommunity(communityId: String) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    viewModel: CommunityViewModel,
    communityId: String,
    onNavigateToPost: (String) -> Unit,
    onNavigateToCompose: () -> Unit,
    onVerifyWithSelf: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val canCreatePost = state.eligibility?.status == "already_joined"

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
            if (canCreatePost) {
                FloatingActionButton(
                    onClick = onNavigateToCompose,
                    containerColor = PirateTokens.colors.accentBrand,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create post")
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                }
            } else if (state.error != null) {
                FormNote(
                    message = state.error!!,
                    tone = sc.pirate.app.ui.FormTone.Error,
                    modifier = Modifier.padding(16.dp),
                )
            } else if (state.community != null) {
                val c = state.community!!
                val preview = state.preview
                val eligibility = state.eligibility
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
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
                                text = "${preview?.memberCount ?: c.memberCount ?: 0} members",
                                style = MaterialTheme.typography.labelMedium,
                                color = PirateTokens.colors.textSecondary,
                            )
                            if (eligibility != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = communityStatusText(eligibility),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PirateTokens.colors.textSecondary,
                                )
                                if (eligibility.status == "joinable" || eligibility.status == "requestable") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    PirateButton(
                                        text = if (eligibility.status == "requestable") "Request to join" else "Join",
                                        onClick = { viewModel.joinCommunity(communityId) },
                                        loading = state.joinLoading,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (
                                    eligibility.status == "verification_required" &&
                                    eligibility.suggestedVerificationProvider == "self"
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    PirateButton(
                                        text = "Verify with ID",
                                        onClick = {
                                            onVerifyWithSelf(
                                                eligibility.suggestedVerificationIntent ?: "community_join",
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                if (state.joinError != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    FormNote(
                                        message = state.joinError.orEmpty(),
                                        tone = sc.pirate.app.ui.FormTone.Error,
                                    )
                                }
                            }
                        }
                    }

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

                    if (state.postsPaginationError != null) {
                        item {
                            FormNote(
                                message = state.postsPaginationError.orEmpty(),
                                tone = sc.pirate.app.ui.FormTone.Error,
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
                            PostRow(
                                title = postResp.post.title ?: "Untitled",
                                body = postResp.post.body,
                                onClick = { onNavigateToPost(postResp.post.postId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (state.nextPostsCursor != null) {
                        item {
                            PirateButton(
                                text = if (state.postsLoadingMore) "Loading" else "Load more",
                                onClick = { viewModel.loadMorePosts(communityId) },
                                loading = state.postsLoadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun communityStatusText(eligibility: JoinEligibility): String =
    when (eligibility.status) {
        "already_joined" -> "You are a member."
        "joinable" -> "You can join this community."
        "requestable" -> "Membership requires a request."
        "verification_required" -> {
            val provider = eligibility.suggestedVerificationProvider ?: eligibility.humanVerificationLane
            "Verification required with $provider."
        }
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
