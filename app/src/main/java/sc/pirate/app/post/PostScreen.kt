package sc.pirate.app.post

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import sc.pirate.app.api.CreateCommentRequest
import sc.pirate.app.api.model.CommentListItem
import sc.pirate.app.api.model.CommunityListing
import sc.pirate.app.api.model.CommunityPreview
import sc.pirate.app.api.model.CommunityPurchase
import sc.pirate.app.api.model.CommunityPurchaseSettlementFailureRequest
import sc.pirate.app.api.model.CommunityPurchaseSettlementRequest
import sc.pirate.app.api.model.LiveRoomAccessResponse
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.Profile
import sc.pirate.app.commerce.buildStoryCheckoutQuoteRequest
import sc.pirate.app.commerce.resolveStoryCheckoutTransferInput
import sc.pirate.app.shared.buildDefaultUserAvatarSrc
import sc.pirate.app.shared.formatCommunityRouteLabel
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ChipOption
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.VoteControl
import sc.pirate.app.ui.adjustedVoteCount

data class PostUiState(
    val post: LocalizedPostResponse? = null,
    val comments: List<CommentListItem> = emptyList(),
    val communityPreview: CommunityPreview? = null,
    val authorProfiles: Map<String, Profile> = emptyMap(),
    val nextCommentsCursor: String? = null,
    val commentSort: String = "best",
    val loading: Boolean = true,
    val commentsLoading: Boolean = false,
    val commentsLoadingMore: Boolean = false,
    val commentDraft: String = "",
    val commentSubmitting: Boolean = false,
    val postVoting: Boolean = false,
    val repliesByParentId: Map<String, List<CommentListItem>> = emptyMap(),
    val nextRepliesCursorByParentId: Map<String, String?> = emptyMap(),
    val loadingRepliesParentIds: Set<String> = emptySet(),
    val replyDraftsByParentId: Map<String, String> = emptyMap(),
    val submittingReplyParentIds: Set<String> = emptySet(),
    val votingCommentIds: Set<String> = emptySet(),
    val liveRoomAccess: LiveRoomAccessResponse? = null,
    val liveRoomListing: CommunityListing? = null,
    val liveRoomPurchase: CommunityPurchase? = null,
    val assetListing: CommunityListing? = null,
    val assetPurchase: CommunityPurchase? = null,
    val purchaseSubmitting: Boolean = false,
    val purchaseError: String? = null,
    val purchaseMessage: String? = null,
    val error: String? = null,
    val commentsError: String? = null,
    val commentsPaginationError: String? = null,
    val commentSubmitError: String? = null,
    val postVoteError: String? = null,
    val repliesErrorByParentId: Map<String, String> = emptyMap(),
    val replySubmitErrorByParentId: Map<String, String> = emptyMap(),
    val commentVoteError: String? = null,
)

private data class PostCommerceEnrichment(
    val liveRoomAccess: LiveRoomAccessResponse? = null,
    val liveRoomListing: CommunityListing? = null,
    val liveRoomPurchase: CommunityPurchase? = null,
    val assetListing: CommunityListing? = null,
    val assetPurchase: CommunityPurchase? = null,
)

class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val postRepository get() = app.repositories.postRepository
    private val communityRepository get() = app.repositories.communityRepository
    private val profileRepository get() = app.repositories.profileRepository
    private val _state = MutableStateFlow(PostUiState())
    val state: StateFlow<PostUiState> = _state.asStateFlow()
    private var currentPostId: String? = null
    private var currentHasSession: Boolean = false

    fun loadPost(postId: String, hasSession: Boolean, commentSort: String = _state.value.commentSort) {
        currentPostId = postId
        currentHasSession = hasSession
        val existingState = _state.value
        viewModelScope.launch {
            _state.value = existingState.copy(
                loading = true,
                error = null,
                commentSort = commentSort,
            )
            try {
                val post = if (hasSession) {
                    postRepository.getPost(postId)
                } else {
                    postRepository.getPublicPost(postId)
                }
                val commerce = loadPostCommerce(post, hasSession)
                _state.value = PostUiState(
                    post = post,
                    communityPreview = loadCommunityPreview(post.post.communityId, hasSession),
                    authorProfiles = loadAuthorProfiles(listOfNotNull(post.post.authorUserId)),
                    commentSort = commentSort,
                    commentDraft = existingState.commentDraft,
                    loading = false,
                    commentsLoading = true,
                    liveRoomAccess = commerce.liveRoomAccess,
                    liveRoomListing = commerce.liveRoomListing,
                    liveRoomPurchase = commerce.liveRoomPurchase,
                    assetListing = commerce.assetListing,
                    assetPurchase = commerce.assetPurchase,
                )
                loadTopLevelComments(post, hasSession)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load post",
                )
            }
        }
    }

    fun setCommentSort(sort: String) {
        val postId = currentPostId ?: return
        if (sort == _state.value.commentSort) return
        loadPost(postId = postId, hasSession = currentHasSession, commentSort = sort)
    }

    fun updateCommentDraft(value: String) {
        _state.value = _state.value.copy(
            commentDraft = value,
            commentSubmitError = null,
        )
    }

    fun submitComment() {
        val current = _state.value
        val post = current.post ?: return
        val body = current.commentDraft.trim()
        if (body.isBlank() || current.commentSubmitting) return

        viewModelScope.launch {
            _state.value = _state.value.copy(
                commentSubmitting = true,
                commentSubmitError = null,
            )
            try {
                postRepository.createComment(
                    communityId = post.post.communityId,
                    postId = post.post.postId,
                    request = CreateCommentRequest(body = body),
                )
                _state.value = _state.value.copy(
                    commentDraft = "",
                    commentSubmitting = false,
                    commentsLoading = true,
                )
                loadTopLevelComments(post, hasSession = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    commentSubmitting = false,
                    commentSubmitError = e.message ?: "Failed to post comment",
                )
            }
        }
    }

    fun votePost(value: Int) {
        val current = _state.value
        val previousPost = current.post ?: return
        if (current.postVoting || previousPost.viewerVote == value) return

        _state.value = current.copy(
            post = previousPost.withPostVote(value),
            postVoting = true,
            postVoteError = null,
        )

        viewModelScope.launch {
            try {
                val response = postRepository.votePost(previousPost.post.postId, value)
                _state.value = _state.value.copy(
                    post = _state.value.post?.withPostVote(response.value),
                    postVoting = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    post = previousPost,
                    postVoting = false,
                    postVoteError = e.message ?: "Failed to vote on post",
                )
            }
        }
    }

    fun loadMoreComments() {
        val current = _state.value
        val post = current.post ?: return
        val cursor = current.nextCommentsCursor ?: return
        if (current.commentsLoadingMore) return

        _state.value = current.copy(
            commentsLoadingMore = true,
            commentsPaginationError = null,
        )

        viewModelScope.launch {
            try {
                val response = if (app.sessionStore.get() != null) {
                    postRepository.listComments(
                        communityId = post.post.communityId,
                        postId = post.post.postId,
                        cursor = cursor,
                        limit = 25,
                        sort = current.commentSort,
                    )
                } else {
                    postRepository.listPublicComments(
                        postId = post.post.postId,
                        cursor = cursor,
                        limit = 25,
                        sort = current.commentSort,
                    )
                }
                _state.value = _state.value.copy(
                    comments = (_state.value.comments + response.items).distinctBy { it.comment.commentId },
                    authorProfiles = _state.value.authorProfiles + loadAuthorProfiles(
                        response.items.mapNotNull { it.comment.authorUserId },
                    ),
                    nextCommentsCursor = response.nextCursor,
                    commentsLoadingMore = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    commentsLoadingMore = false,
                    commentsPaginationError = e.message ?: "Failed to load more comments",
                )
            }
        }
    }

    fun loadReplies(parentCommentId: String) {
        val current = _state.value
        if (parentCommentId in current.loadingRepliesParentIds) return

        _state.value = current.copy(
            loadingRepliesParentIds = current.loadingRepliesParentIds + parentCommentId,
            repliesErrorByParentId = current.repliesErrorByParentId - parentCommentId,
        )

        viewModelScope.launch {
            try {
                val response = if (app.sessionStore.get() != null) {
                    postRepository.listReplies(
                        commentId = parentCommentId,
                        limit = 10,
                        sort = "best",
                    )
                } else {
                    postRepository.listPublicReplies(
                        commentId = parentCommentId,
                        limit = 10,
                        sort = "best",
                    )
                }
                _state.value = _state.value.copy(
                    repliesByParentId = _state.value.repliesByParentId + (parentCommentId to response.items),
                    nextRepliesCursorByParentId = _state.value.nextRepliesCursorByParentId +
                        (parentCommentId to response.nextCursor),
                    loadingRepliesParentIds = _state.value.loadingRepliesParentIds - parentCommentId,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingRepliesParentIds = _state.value.loadingRepliesParentIds - parentCommentId,
                    repliesErrorByParentId = _state.value.repliesErrorByParentId +
                        (parentCommentId to (e.message ?: "Failed to load replies")),
                )
            }
        }
    }

    fun loadMoreReplies(parentCommentId: String) {
        val current = _state.value
        val cursor = current.nextRepliesCursorByParentId[parentCommentId] ?: return
        if (parentCommentId in current.loadingRepliesParentIds) return

        _state.value = current.copy(
            loadingRepliesParentIds = current.loadingRepliesParentIds + parentCommentId,
            repliesErrorByParentId = current.repliesErrorByParentId - parentCommentId,
        )

        viewModelScope.launch {
            try {
                val response = if (app.sessionStore.get() != null) {
                    postRepository.listReplies(
                        commentId = parentCommentId,
                        cursor = cursor,
                        limit = 10,
                        sort = "best",
                    )
                } else {
                    postRepository.listPublicReplies(
                        commentId = parentCommentId,
                        cursor = cursor,
                        limit = 10,
                        sort = "best",
                    )
                }
                val currentReplies = _state.value.repliesByParentId[parentCommentId].orEmpty()
                _state.value = _state.value.copy(
                    repliesByParentId = _state.value.repliesByParentId + (
                        parentCommentId to (currentReplies + response.items).distinctBy { it.comment.commentId }
                    ),
                    nextRepliesCursorByParentId = _state.value.nextRepliesCursorByParentId +
                        (parentCommentId to response.nextCursor),
                    loadingRepliesParentIds = _state.value.loadingRepliesParentIds - parentCommentId,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingRepliesParentIds = _state.value.loadingRepliesParentIds - parentCommentId,
                    repliesErrorByParentId = _state.value.repliesErrorByParentId +
                        (parentCommentId to (e.message ?: "Failed to load more replies")),
                )
            }
        }
    }

    fun updateReplyDraft(parentCommentId: String, value: String) {
        _state.value = _state.value.copy(
            replyDraftsByParentId = _state.value.replyDraftsByParentId + (parentCommentId to value),
            replySubmitErrorByParentId = _state.value.replySubmitErrorByParentId - parentCommentId,
        )
    }

    fun submitReply(parentCommentId: String) {
        val current = _state.value
        val body = current.replyDraftsByParentId[parentCommentId].orEmpty().trim()
        if (body.isBlank() || parentCommentId in current.submittingReplyParentIds) return

        _state.value = current.copy(
            submittingReplyParentIds = current.submittingReplyParentIds + parentCommentId,
            replySubmitErrorByParentId = current.replySubmitErrorByParentId - parentCommentId,
        )

        viewModelScope.launch {
            try {
                postRepository.createReply(
                    commentId = parentCommentId,
                    request = CreateCommentRequest(body = body),
                )
                _state.value = _state.value.copy(
                    replyDraftsByParentId = _state.value.replyDraftsByParentId + (parentCommentId to ""),
                    submittingReplyParentIds = _state.value.submittingReplyParentIds - parentCommentId,
                )
                loadReplies(parentCommentId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    submittingReplyParentIds = _state.value.submittingReplyParentIds - parentCommentId,
                    replySubmitErrorByParentId = _state.value.replySubmitErrorByParentId +
                        (parentCommentId to (e.message ?: "Failed to post reply")),
                )
            }
        }
    }

    fun voteComment(commentId: String, value: Int) {
        val current = _state.value
        if (commentId in current.votingCommentIds) return

        val previousComment = current.comments.firstOrNull { it.comment.commentId == commentId } ?: return
        if (previousComment.viewerVote == value) return

        _state.value = current.copy(
            comments = current.comments.withCommentVote(commentId, value),
            votingCommentIds = current.votingCommentIds + commentId,
            commentVoteError = null,
        )

        viewModelScope.launch {
            try {
                val response = postRepository.voteComment(commentId, value)
                _state.value = _state.value.copy(
                    comments = _state.value.comments.replaceComment(previousComment.withVote(response.value)),
                    votingCommentIds = _state.value.votingCommentIds - commentId,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    comments = _state.value.comments.replaceComment(previousComment),
                    votingCommentIds = _state.value.votingCommentIds - commentId,
                    commentVoteError = e.message ?: "Failed to vote on comment",
                )
            }
        }
    }

    fun buyLiveRoomTicket() {
        val current = _state.value
        val postResponse = current.post ?: return
        val listing = current.liveRoomListing ?: return
        if (current.purchaseSubmitting) return

        _state.value = current.copy(
            purchaseSubmitting = true,
            purchaseError = null,
            purchaseMessage = null,
        )

        viewModelScope.launch {
            var quoteId: String? = null
            var fundingTxRef: String? = null
            try {
                val session = requireNotNull(app.sessionStore.get()) {
                    "Sign in before buying a ticket."
                }
                val walletAttachmentId = session.walletAttachments
                    .firstOrNull { it.isPrimary }
                    ?.walletAttachmentId
                    ?: session.walletAttachments.firstOrNull()?.walletAttachmentId
                    ?: error("Link a wallet before buying a ticket.")
                val quote = communityRepository.createPurchaseQuote(
                    communityId = postResponse.post.communityId,
                    request = buildStoryCheckoutQuoteRequest(
                        listingId = listing.id,
                        selectedWalletChainId = app.reownManager.state.value.selectedChain,
                    ),
                )
                quoteId = quote.id
                val transfer = resolveStoryCheckoutTransferInput(quote)
                fundingTxRef = app.reownManager.sendErc20Transfer(
                    tokenAddress = transfer.tokenAddress,
                    recipientAddress = transfer.recipientAddress,
                    amountAtomic = transfer.amountAtomic,
                    chainId = transfer.walletChainId,
                )
                communityRepository.settlePurchase(
                    communityId = postResponse.post.communityId,
                    request = CommunityPurchaseSettlementRequest(
                        quote = quote.id,
                        settlementWalletAttachment = walletAttachmentId,
                        fundingTxRef = fundingTxRef,
                        settlementTxRef = fundingTxRef,
                    ),
                )
                val commerce = loadPostCommerce(postResponse, hasSession = true)
                _state.value = _state.value.copy(
                    liveRoomAccess = commerce.liveRoomAccess,
                    liveRoomListing = commerce.liveRoomListing,
                    liveRoomPurchase = commerce.liveRoomPurchase,
                    assetListing = commerce.assetListing,
                    assetPurchase = commerce.assetPurchase,
                    purchaseSubmitting = false,
                    purchaseMessage = "Ticket purchase submitted.",
                )
            } catch (e: Exception) {
                val failedQuoteId = quoteId
                if (failedQuoteId != null && fundingTxRef == null) {
                    runCatching {
                        communityRepository.failPurchase(
                            communityId = postResponse.post.communityId,
                            request = CommunityPurchaseSettlementFailureRequest(quote = failedQuoteId),
                        )
                    }
                }
                _state.value = _state.value.copy(
                    purchaseSubmitting = false,
                    purchaseError = e.message ?: "Failed to buy ticket",
                )
            }
        }
    }

    private suspend fun loadTopLevelComments(post: LocalizedPostResponse, hasSession: Boolean) {
        try {
            val response = if (hasSession) {
                postRepository.listComments(
                    communityId = post.post.communityId,
                    postId = post.post.postId,
                    limit = 25,
                    sort = _state.value.commentSort,
                )
            } else {
                postRepository.listPublicComments(
                    postId = post.post.postId,
                    limit = 25,
                    sort = _state.value.commentSort,
                )
            }
            _state.value = _state.value.copy(
                comments = response.items,
                authorProfiles = _state.value.authorProfiles + loadAuthorProfiles(
                    response.items.mapNotNull { it.comment.authorUserId },
                ),
                nextCommentsCursor = response.nextCursor,
                commentsLoading = false,
                commentsError = null,
                commentsPaginationError = null,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                commentsLoading = false,
                commentsError = e.message ?: "Failed to load comments",
            )
        }
    }

    private suspend fun loadCommunityPreview(communityId: String, hasSession: Boolean): CommunityPreview? =
        try {
            if (hasSession) {
                communityRepository.getPreview(communityId)
            } else {
                communityRepository.getPublicPreview(communityId)
            }
        } catch (_: Exception) {
            null
        }

    private suspend fun loadPostCommerce(postResponse: LocalizedPostResponse, hasSession: Boolean): PostCommerceEnrichment {
        val post = postResponse.post
        val communityId = post.communityId.takeIf { it.isNotBlank() } ?: return PostCommerceEnrichment()
        val anchorLiveRoom = post.anchorLiveRoom?.takeIf { it.isNotBlank() }
        val assetId = post.assetId?.takeIf { it.isNotBlank() }
        if (anchorLiveRoom == null && assetId == null) return PostCommerceEnrichment()
        if (!hasSession) {
            return PostCommerceEnrichment(
                liveRoomAccess = anchorLiveRoom?.let {
                    runCatching { communityRepository.getPublicLiveRoomAccess(communityId, it) }.getOrNull()
                },
            )
        }

        val listings = runCatching { communityRepository.listListings(communityId).items }.getOrDefault(emptyList())
        val purchases = runCatching { communityRepository.listPurchases(communityId).items }.getOrDefault(emptyList())
        return PostCommerceEnrichment(
            liveRoomAccess = anchorLiveRoom?.let {
                runCatching { communityRepository.getLiveRoomAccess(communityId, it) }.getOrNull()
            },
            liveRoomListing = anchorLiveRoom?.let { liveRoomId -> listings.firstOrNull { it.liveRoom == liveRoomId } },
            liveRoomPurchase = anchorLiveRoom?.let { liveRoomId -> purchases.firstOrNull { it.liveRoom == liveRoomId } },
            assetListing = assetId?.let { nextAssetId -> listings.firstOrNull { it.asset == nextAssetId } },
            assetPurchase = assetId?.let { nextAssetId -> purchases.firstOrNull { it.asset == nextAssetId } },
        )
    }

    private suspend fun loadAuthorProfiles(userIds: List<String>): Map<String, Profile> {
        val missingUserIds = userIds.distinct().filter { it.isNotBlank() && it !in _state.value.authorProfiles }
        if (missingUserIds.isEmpty()) return emptyMap()

        val profiles = mutableMapOf<String, Profile>()
        for (userId in missingUserIds) {
            val profile = try {
                profileRepository.getByUserId(userId)
            } catch (_: Exception) {
                null
            }
            if (profile != null) profiles[userId] = profile
        }
        return profiles
    }
}

private val commentSortOptions = listOf(
    ChipOption("best", "Best"),
    ChipOption("new", "New"),
    ChipOption("top", "Top"),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CommentSortSheet(
    selectedSort: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
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
            commentSortOptions.forEach { option ->
                SortSheetRow(
                    label = option.label,
                    selected = option.value == selectedSort,
                    onClick = { onSelected(option.value) },
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SortSheetRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        if (selected) {
            Text(
                text = "Selected",
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.accentBrand,
            )
        }
    }
}

@Composable
private fun CommentSortPill(
    selectedSort: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = commentSortOptions.firstOrNull { it.value == selectedSort }?.label ?: "Best"
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(PirateTokens.radius.full),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier
                .height(38.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
            Icon(
                imageVector = PhosphorIcons.CaretDown,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    postId: String,
    hasSession: Boolean,
    onNavigateToCompose: ((String) -> Unit)? = null,
    onWatchLiveRoom: () -> Unit,
    signInDrawer: @Composable (onDismiss: () -> Unit) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PostViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    var authPromptAction by rememberSaveable { mutableStateOf<String?>(null) }
    var commentSortSheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(postId, hasSession) {
        viewModel.loadPost(postId, hasSession)
    }

    authPromptAction?.let {
        signInDrawer { authPromptAction = null }
    }

    if (commentSortSheetOpen) {
        CommentSortSheet(
            selectedSort = state.commentSort,
            onDismiss = { commentSortSheetOpen = false },
            onSelected = { sort ->
                commentSortSheetOpen = false
                viewModel.setCommentSort(sort)
            },
        )
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Post",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhosphorIcons.X,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    if (onNavigateToCompose != null) {
                        val communityId = state.post?.post?.communityId
                        if (!communityId.isNullOrBlank()) {
                            IconButton(onClick = { onNavigateToCompose(communityId) }) {
                                Icon(
                                    imageVector = PhosphorIcons.Plus,
                                    contentDescription = "Create post",
                                    tint = PirateTokens.colors.textPrimary,
                                )
                            }
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
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                }
            } else if (state.error != null) {
                FormNote(
                    message = state.error!!,
                    tone = FormTone.Error,
                    modifier = Modifier.padding(16.dp),
                )
            } else if (state.post != null) {
                val postResponse = state.post!!
                val post = postResponse.post
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PirateTokens.colors.bgPage),
                ) {
                    item {
                        ThreadRootPost(
                            postResponse = postResponse,
                            communityPreview = state.communityPreview,
                            authorProfile = post.authorUserId?.let { state.authorProfiles[it] },
                            isVoting = state.postVoting,
                            liveRoomAccess = state.liveRoomAccess,
                            liveRoomListing = state.liveRoomListing,
                            liveRoomPurchase = state.liveRoomPurchase,
                            assetListing = state.assetListing,
                            assetPurchase = state.assetPurchase,
                            purchaseSubmitting = state.purchaseSubmitting,
                            purchaseError = state.purchaseError,
                            purchaseMessage = state.purchaseMessage,
                            onVote = { value ->
                                if (hasSession) viewModel.votePost(value) else authPromptAction = "Voting"
                            },
                            onBuyLiveRoomTicket = {
                                if (hasSession) viewModel.buyLiveRoomTicket() else authPromptAction = "Buying tickets"
                            },
                            onWatchLiveRoom = {
                                onWatchLiveRoom()
                            },
                        )
                    }

                    if (state.postVoteError != null) {
                        item {
                            FormNote(
                                message = state.postVoteError.orEmpty(),
                                tone = FormTone.Error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item {
                        InlineReplyComposer(
                            draft = state.commentDraft,
                            error = state.commentSubmitError,
                            submitting = state.commentSubmitting,
                            onDraftChange = viewModel::updateCommentDraft,
                            onSubmit = {
                                if (hasSession) viewModel.submitComment() else authPromptAction = "Commenting"
                            },
                        )
                    }

                    item {
                        CommentSortPill(
                            selectedSort = state.commentSort,
                            onClick = { commentSortSheetOpen = true },
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }

                    if (state.commentsPaginationError != null) {
                        item {
                                StatusCard(
                                    title = "More comments unavailable",
                                    description = state.commentsPaginationError.orEmpty(),
                                    tone = StatusTone.Warning,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            }
                        }

                    when {
                        state.commentsLoading -> {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                                }
                            }
                        }

                        state.commentsError != null -> {
                            item {
                                StatusCard(
                                    title = "Comments unavailable",
                                    description = userFacingCommentsError(state.commentsError),
                                    tone = StatusTone.Warning,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            }
                        }

                        state.comments.isEmpty() -> {
                            item {
                                StatusCard(
                                    title = "No comments yet",
                                    description = "This thread has not started.",
                                    tone = StatusTone.Default,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                )
                            }
                        }

                        else -> {
                            items(state.comments, key = { it.comment.commentId }) { comment ->
                                val commentId = comment.comment.commentId
                                CommentRow(
                                    comment = comment,
                                    authorProfile = comment.comment.authorUserId?.let { state.authorProfiles[it] },
                                    isVoting = commentId in state.votingCommentIds,
                                    onVote = { value ->
                                        if (hasSession) viewModel.voteComment(commentId, value) else authPromptAction = "Voting"
                                    },
                                    onReply = {
                                        if (hasSession) viewModel.loadReplies(commentId) else authPromptAction = "Replying"
                                    },
                                )
                            }
                        }
                    }

                    if (state.nextCommentsCursor != null) {
                        item {
                            PirateButton(
                                text = if (state.commentsLoadingMore) "Loading" else "Load more",
                                onClick = viewModel::loadMoreComments,
                                loading = state.commentsLoadingMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LocalizedPostResponse.withPostVote(value: Int): LocalizedPostResponse {
    val previousValue = viewerVote
    if (previousValue == value) return this

    return copy(
        upvoteCount = adjustedVoteCount(upvoteCount, previousValue, value, 1),
        downvoteCount = adjustedVoteCount(downvoteCount, previousValue, value, -1),
        viewerVote = value,
    )
}

private fun List<CommentListItem>.withCommentVote(commentId: String, value: Int): List<CommentListItem> =
    map { item ->
        if (item.comment.commentId != commentId) return@map item

        item.withVote(value)
    }

private fun CommentListItem.withVote(value: Int): CommentListItem {
    val previousValue = viewerVote
    if (previousValue == value) return this

    return copy(
        comment = comment.copy(
            upvoteCount = adjustedVoteCount(comment.upvoteCount, previousValue, value, 1),
            downvoteCount = adjustedVoteCount(comment.downvoteCount, previousValue, value, -1),
            score = comment.score + voteScoreDelta(previousValue, value),
        ),
        viewerVote = value,
    )
}

private fun List<CommentListItem>.replaceComment(previousComment: CommentListItem): List<CommentListItem> =
    map { item ->
        if (item.comment.commentId == previousComment.comment.commentId) previousComment else item
    }

private fun voteScoreDelta(previousValue: Int?, nextValue: Int): Int =
    nextValue - (previousValue ?: 0)

@Composable
private fun ThreadRootPost(
    postResponse: LocalizedPostResponse,
    communityPreview: CommunityPreview?,
    authorProfile: Profile?,
    isVoting: Boolean,
    liveRoomAccess: LiveRoomAccessResponse?,
    liveRoomListing: CommunityListing?,
    liveRoomPurchase: CommunityPurchase?,
    assetListing: CommunityListing?,
    assetPurchase: CommunityPurchase?,
    purchaseSubmitting: Boolean,
    purchaseError: String?,
    purchaseMessage: String?,
    onVote: (Int) -> Unit,
    onBuyLiveRoomTicket: () -> Unit,
    onWatchLiveRoom: () -> Unit,
) {
    val post = postResponse.post
    val title = postResponse.translatedTitle ?: post.title ?: post.linkOgTitle ?: "Untitled post"
    val body = postResponse.translatedBody ?: post.body ?: post.caption
    val comments = postResponse.commentCount ?: postResponse.threadSnapshot?.commentCount ?: 0
    val score = postResponse.upvoteCount - postResponse.downvoteCount
    val routeLabel = formatCommunityRouteLabel(
        communityId = post.communityId,
        routeSlug = communityPreview?.routeSlug ?: communityPreview?.displayName,
    )
    val authorLabel = resolveAuthorLabel(
        identityMode = post.identityMode,
        anonymousLabel = post.anonymousLabel,
        authorUserId = post.authorUserId,
        authorProfile = authorProfile,
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(0.dp),
        border = BorderStroke(0.5.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommunityAvatar(label = communityPreview?.displayName ?: routeLabel)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routeLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = PirateTokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$authorLabel · ${relativeTimeLabel(post.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
            )
            body?.takeIf { it.isNotBlank() && it != title }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textPrimary,
                )
            }
            post.anchorLiveRoom?.let {
                ThreadLiveRoomSummary(
                    fallbackTitle = title,
                    access = liveRoomAccess,
                    listing = liveRoomListing,
                    purchase = liveRoomPurchase,
                    publicStatus = post.anchorLiveRoomStatus,
                    publicAccessMode = post.accessMode,
                    fallbackCoverRef = post.mediaRefs.firstOrNull()?.posterRef ?: post.mediaRefs.firstOrNull()?.storageRef,
                    purchaseSubmitting = purchaseSubmitting,
                    purchaseError = purchaseError,
                    purchaseMessage = purchaseMessage,
                    onBuyTicket = onBuyLiveRoomTicket,
                    onWatch = onWatchLiveRoom,
                )
            }
            if (post.anchorLiveRoom == null && post.assetId != null && assetListing != null) {
                AssetCommerceSummary(
                    postType = post.postType,
                    listing = assetListing,
                    purchase = assetPurchase,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VoteControl(
                    score = score,
                    viewerVote = postResponse.viewerVote,
                    enabled = !isVoting,
                    onVote = onVote,
                )
                CommentCountPill(count = comments)
            }
        }
    }
}

@Composable
private fun ThreadLiveRoomSummary(
    fallbackTitle: String,
    access: LiveRoomAccessResponse?,
    listing: CommunityListing?,
    purchase: CommunityPurchase?,
    publicStatus: String?,
    publicAccessMode: String?,
    fallbackCoverRef: String?,
    purchaseSubmitting: Boolean,
    purchaseError: String?,
    purchaseMessage: String?,
    onBuyTicket: () -> Unit,
    onWatch: () -> Unit,
) {
    val room = access?.room
    val title = room?.title?.takeIf { it.isNotBlank() } ?: fallbackTitle
    val status = room?.status ?: publicStatus ?: "scheduled"
    val accessMode = access?.access?.accessMode ?: room?.accessMode ?: publicAccessMode ?: listing?.let { "paid" }
    val allowed = access?.access?.allowed == true || purchase != null || accessMode == "free"
    val decisionReason = access?.access?.decisionReason
    val needsTicket = decisionReason == "purchase_required" || (accessMode == "paid" && !allowed)
    val coverSrc = resolvePublicMediaSrc(room?.coverRef ?: fallbackCoverRef)
    val priceLabel = listing?.priceCents?.takeIf { it > 0 }?.let(::formatUsdCents)
    val listingActive = listing?.status == null || listing.status == "active"
    val statusLabel = when (status) {
        "live" -> "Live now"
        "ended" -> "Ended"
        "canceled" -> "Canceled"
        else -> "Scheduled event"
    }
    val accessLabel = when {
        status == "ended" || status == "canceled" -> null
        purchase != null -> "Ticket purchased"
        needsTicket -> when {
            listing != null && listingActive -> priceLabel?.let { "Tickets $it" } ?: "Tickets available"
            else -> "Ticket required"
        }
        accessMode == "gated" -> "Gated access"
        accessMode == "free" -> "Free"
        else -> "Live event"
    }
    val description = when {
        status == "ended" -> "This room has ended."
        status == "canceled" -> "This room was canceled."
        needsTicket -> priceLabel?.let { "$it ticket required to watch." } ?: "Ticket required to watch."
        accessMode == "gated" && !allowed -> "Community access is required before you can watch."
        status == "live" && allowed -> "Watch the concert from this page."
        else -> "Come back when the host goes live."
    }
    val canBuyTicket = listingActive &&
        purchase == null &&
        status != "ended" &&
        status != "canceled" &&
        needsTicket
    val canWatch = allowed && status == "live"
    val buyTicketText = priceLabel?.let { "Buy ticket $it" } ?: "Buy ticket"
    val showTitle = title != fallbackTitle

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PirateTokens.colors.bgElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverSrc != null) {
                        AsyncImage(
                            model = coverSrc,
                            contentDescription = title,
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
                    if (showTitle) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = PirateTokens.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (status == "live") {
                            PirateTokens.colors.accentDanger
                        } else {
                            PirateTokens.colors.textSecondary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    accessLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = PirateTokens.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            purchaseError?.let {
                FormNote(message = it, tone = FormTone.Error)
            }
            purchaseMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
            if (canBuyTicket) {
                PirateButton(
                    text = if (purchaseSubmitting) "Buying" else buyTicketText,
                    onClick = onBuyTicket,
                    loading = purchaseSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (canWatch) {
                PirateButton(
                    text = "Watch",
                    onClick = onWatch,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AssetCommerceSummary(
    postType: String?,
    listing: CommunityListing,
    purchase: CommunityPurchase?,
) {
    val label = when (postType) {
        "video" -> "Video"
        "song" -> "Song"
        else -> "Asset"
    }
    val priceLabel = listing.priceCents.takeIf { it > 0 }?.let(::formatUsdCents)
    val accessLabel = if (purchase != null) {
        "$label unlocked"
    } else {
        priceLabel?.let { "$label unlock $it" } ?: "$label available to unlock"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = PirateTokens.colors.surfaceSubtle,
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (postType == "video") PhosphorIcons.VideoCamera else PhosphorIcons.MusicNote,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
            )
            Text(
                text = accessLabel,
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatUsdCents(cents: Int): String = "$" + String.format(Locale.US, "%.2f", cents / 100.0)

@Composable
private fun InlineReplyComposer(
    draft: String,
    error: String?,
    submitting: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Write a reply") },
            minLines = 1,
            enabled = !submitting,
        )
        if (error != null) {
            FormNote(
                message = error,
                tone = FormTone.Error,
            )
        }
        if (draft.isNotBlank()) {
            PirateButton(
                text = if (submitting) "Posting" else "Reply",
                onClick = onSubmit,
                enabled = draft.isNotBlank(),
                loading = submitting,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun userFacingCommentsError(error: String?): String {
    val message = error?.takeIf { it.isNotBlank() } ?: return "Could not load comments."
    return if (message.contains("serial name") || message.contains("Fields [") || message.contains("required")) {
        "Could not load comments."
    } else {
        message
    }
}

@Composable
private fun CommentRow(
    comment: CommentListItem,
    authorProfile: Profile?,
    isVoting: Boolean,
    onVote: (Int) -> Unit,
    onReply: () -> Unit,
) {
    val model = comment.comment
    val authorLabel = resolveAuthorLabel(
        identityMode = model.identityMode,
        anonymousLabel = model.anonymousLabel,
        authorUserId = model.authorUserId,
        authorProfile = authorProfile,
    )
    val avatarSrc = resolveCommentAvatarSrc(
        identityMode = model.identityMode,
        anonymousLabel = model.anonymousLabel,
        authorUserId = model.authorUserId,
        authorProfile = authorProfile,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PirateTokens.colors.bgPage,
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CommentAvatar(
                label = authorLabel,
                avatarSrc = avatarSrc,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = authorLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = PirateTokens.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "· ${relativeTimeLabel(model.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                        maxLines = 1,
                    )
                }
                Text(
                    text = comment.translatedBody ?: model.body ?: "[removed]",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textPrimary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VoteControl(
                        score = model.score,
                        viewerVote = comment.viewerVote,
                        enabled = !isVoting,
                        onVote = onVote,
                    )
                    ReplyPill(onClick = onReply)
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(
    label: String,
    avatarSrc: String,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
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

@Composable
private fun CommentCountPill(
    count: Int,
) {
    Surface(
        modifier = Modifier.height(38.dp),
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
private fun ReplyPill(
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
                text = "Reply",
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
            )
        }
    }
}

private fun resolveAuthorLabel(
    identityMode: String?,
    anonymousLabel: String?,
    authorUserId: String?,
    authorProfile: Profile?,
): String {
    if (identityMode == "anonymous") {
        return anonymousLabel ?: "anon"
    }

    return authorProfile?.globalHandle?.label?.let(::formatPirateHandle)
        ?: authorUserId?.take(8)
        ?: "Pirate user"
}

private fun resolveCommentAvatarSrc(
    identityMode: String?,
    anonymousLabel: String?,
    authorUserId: String?,
    authorProfile: Profile?,
): String {
    if (identityMode != "anonymous") {
        val avatar = resolvePublicMediaSrc(authorProfile?.avatarRef)
        if (avatar != null) return avatar
    }

    val seed = when {
        identityMode == "anonymous" -> anonymousLabel
        !authorProfile?.globalHandle?.label.isNullOrBlank() -> authorProfile?.globalHandle?.label
        !authorUserId.isNullOrBlank() -> authorUserId
        else -> anonymousLabel
    } ?: "comment"
    return buildDefaultUserAvatarSrc(seed)
}

private fun formatPirateHandle(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isBlank()) return trimmed
    return if (trimmed.endsWith(".pirate", ignoreCase = true)) trimmed else "$trimmed.pirate"
}

@Composable
private fun CommunityAvatar(label: String) {
    val colors = communityPlaceholderColors(label)
    Box(
        modifier = Modifier
            .size(46.dp)
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
