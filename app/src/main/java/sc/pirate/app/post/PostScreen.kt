package sc.pirate.app.post

import android.app.Application
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import sc.pirate.app.api.CreateCommentRequest
import sc.pirate.app.api.model.CommentListItem
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ChipOption
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.PirateChipRow
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.adjustedVoteCount

data class PostUiState(
    val post: LocalizedPostResponse? = null,
    val comments: List<CommentListItem> = emptyList(),
    val nextCommentsCursor: String? = null,
    val commentSort: String = "best",
    val loading: Boolean = true,
    val commentsLoading: Boolean = false,
    val commentsLoadingMore: Boolean = false,
    val commentDraft: String = "",
    val commentSubmitting: Boolean = false,
    val repliesByParentId: Map<String, List<CommentListItem>> = emptyMap(),
    val nextRepliesCursorByParentId: Map<String, String?> = emptyMap(),
    val loadingRepliesParentIds: Set<String> = emptySet(),
    val replyDraftsByParentId: Map<String, String> = emptyMap(),
    val submittingReplyParentIds: Set<String> = emptySet(),
    val votingCommentIds: Set<String> = emptySet(),
    val error: String? = null,
    val commentsError: String? = null,
    val commentsPaginationError: String? = null,
    val commentSubmitError: String? = null,
    val repliesErrorByParentId: Map<String, String> = emptyMap(),
    val replySubmitErrorByParentId: Map<String, String> = emptyMap(),
    val commentVoteError: String? = null,
)

class PostViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val postRepository get() = app.repositories.postRepository
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
                _state.value = PostUiState(
                    post = post,
                    commentSort = commentSort,
                    commentDraft = existingState.commentDraft,
                    loading = false,
                    commentsLoading = true,
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
}

private val commentSortOptions = listOf(
    ChipOption("best", "Best"),
    ChipOption("new", "New"),
    ChipOption("top", "Top"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    postId: String,
    hasSession: Boolean,
    onNavigateToCompose: ((String) -> Unit)? = null,
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PostViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(postId, hasSession) {
        viewModel.loadPost(postId, hasSession)
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.post?.post?.title ?: "Post",
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
                actions = {
                    if (hasSession && onNavigateToCompose != null) {
                        val communityId = state.post?.post?.communityId
                        if (!communityId.isNullOrBlank()) {
                            IconButton(onClick = { onNavigateToCompose(communityId) }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
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
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        PirateCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = postResponse.translatedTitle ?: post.title ?: "Untitled post",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PirateTokens.colors.textPrimary,
                            )
                            val body = postResponse.translatedBody ?: post.body
                            if (!body.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = body,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = PirateTokens.colors.textPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = postScoreText(postResponse),
                                style = MaterialTheme.typography.bodySmall,
                                color = PirateTokens.colors.textSecondary,
                            )
                        }
                    }

                    item {
                        PirateCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Add a comment",
                                style = MaterialTheme.typography.titleMedium,
                                color = PirateTokens.colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.commentDraft,
                                onValueChange = viewModel::updateCommentDraft,
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                enabled = !state.commentSubmitting,
                            )
                            if (state.commentSubmitError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                FormNote(
                                    message = state.commentSubmitError.orEmpty(),
                                    tone = FormTone.Error,
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            PirateButton(
                                text = if (state.commentSubmitting) "Posting" else "Post comment",
                                onClick = {
                                    if (hasSession) viewModel.submitComment() else onSignIn()
                                },
                                enabled = state.commentDraft.isNotBlank(),
                                loading = state.commentSubmitting,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item {
                        Text(
                            text = "Comments",
                            style = MaterialTheme.typography.titleLarge,
                            color = PirateTokens.colors.textPrimary,
                        )
                    }

                    item {
                        PirateChipRow(
                            options = commentSortOptions,
                            selectedValue = state.commentSort,
                            onSelected = viewModel::setCommentSort,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (state.commentVoteError != null) {
                        item {
                            StatusCard(
                                title = "Vote unavailable",
                                description = state.commentVoteError.orEmpty(),
                                tone = StatusTone.Warning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (state.commentsPaginationError != null) {
                        item {
                            StatusCard(
                                title = "More comments unavailable",
                                description = state.commentsPaginationError.orEmpty(),
                                tone = StatusTone.Warning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    when {
                        state.commentsLoading -> {
                            item {
                                StatusCard(
                                    title = "Loading comments",
                                    description = "Fetching the thread.",
                                    tone = StatusTone.Default,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        state.commentsError != null -> {
                            item {
                                StatusCard(
                                    title = "Comments unavailable",
                                    description = state.commentsError.orEmpty(),
                                    tone = StatusTone.Warning,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        state.comments.isEmpty() -> {
                            item {
                                StatusCard(
                                    title = "No comments yet",
                                    description = "This thread has not started.",
                                    tone = StatusTone.Default,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        else -> {
                            items(state.comments, key = { it.comment.commentId }) { comment ->
                                val commentId = comment.comment.commentId
                                CommentRow(
                                    comment = comment,
                                    isVoting = commentId in state.votingCommentIds,
                                    replies = state.repliesByParentId[commentId].orEmpty(),
                                    repliesLoading = commentId in state.loadingRepliesParentIds,
                                    repliesError = state.repliesErrorByParentId[commentId],
                                    nextRepliesCursor = state.nextRepliesCursorByParentId[commentId],
                                    replyDraft = state.replyDraftsByParentId[commentId].orEmpty(),
                                    replySubmitting = commentId in state.submittingReplyParentIds,
                                    replySubmitError = state.replySubmitErrorByParentId[commentId],
                                    onLoadReplies = { viewModel.loadReplies(commentId) },
                                    onLoadMoreReplies = { viewModel.loadMoreReplies(commentId) },
                                    onReplyDraftChange = { value -> viewModel.updateReplyDraft(commentId, value) },
                                    onSubmitReply = {
                                        if (hasSession) viewModel.submitReply(commentId) else onSignIn()
                                    },
                                    onVote = { value ->
                                        if (hasSession) viewModel.voteComment(commentId, value) else onSignIn()
                                    },
                                    hasSession = hasSession,
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
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun postScoreText(post: LocalizedPostResponse): String {
    val score = post.upvoteCount - post.downvoteCount
    val comments = post.threadSnapshot?.commentCount ?: 0
    return "$score score | $comments comments"
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
private fun CommentRow(
    comment: CommentListItem,
    isVoting: Boolean,
    replies: List<CommentListItem>,
    repliesLoading: Boolean,
    repliesError: String?,
    nextRepliesCursor: String?,
    replyDraft: String,
    replySubmitting: Boolean,
    replySubmitError: String?,
    onLoadReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
    onReplyDraftChange: (String) -> Unit,
    onSubmitReply: () -> Unit,
    onVote: (Int) -> Unit,
    hasSession: Boolean,
) {
    val model = comment.comment
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = model.anonymousLabel ?: model.authorUserId ?: "Anonymous",
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${model.score} score",
                style = MaterialTheme.typography.labelMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = comment.translatedBody ?: model.body ?: "[removed]",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textPrimary,
        )
        if (model.directReplyCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${model.directReplyCount} replies",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PirateButton(
                text = if (comment.viewerVote == 1) "Upvoted" else "Upvote",
                onClick = { onVote(1) },
                enabled = !isVoting,
                modifier = Modifier.weight(1f),
            )
            PirateButton(
                text = if (comment.viewerVote == -1) "Downvoted" else "Downvote",
                onClick = { onVote(-1) },
                enabled = !isVoting,
                modifier = Modifier.weight(1f),
            )
        }
        if (model.directReplyCount > 0 && replies.isEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            PirateButton(
                text = if (repliesLoading) "Loading replies" else "Show replies",
                onClick = onLoadReplies,
                loading = repliesLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (repliesError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            FormNote(
                message = repliesError,
                tone = FormTone.Error,
            )
        }
        if (replies.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                replies.forEach { reply ->
                    ReplyRow(reply = reply)
                }
                if (nextRepliesCursor != null) {
                    PirateButton(
                        text = if (repliesLoading) "Loading replies" else "Load more replies",
                        onClick = onLoadMoreReplies,
                        loading = repliesLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = replyDraft,
            onValueChange = onReplyDraftChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !replySubmitting,
        )
        if (replySubmitError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            FormNote(
                message = replySubmitError,
                tone = FormTone.Error,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        PirateButton(
            text = if (replySubmitting) "Posting reply" else "Reply",
            onClick = onSubmitReply,
            enabled = replyDraft.isNotBlank(),
            loading = replySubmitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReplyRow(reply: CommentListItem) {
    val model = reply.comment
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = model.anonymousLabel ?: model.authorUserId ?: "Anonymous",
                style = MaterialTheme.typography.labelMedium,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${model.score} score",
                style = MaterialTheme.typography.labelSmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        Text(
            text = reply.translatedBody ?: model.body ?: "[removed]",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textPrimary,
        )
    }
}
