package sc.pirate.app.inbox

import android.app.Application
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import sc.pirate.app.api.model.NotificationFeedItem
import sc.pirate.app.api.model.UserTask
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class InboxUiState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val requiresAuth: Boolean = false,
    val tasks: List<UserTask> = emptyList(),
    val activity: List<NotificationFeedItem> = emptyList(),
    val nextCursor: String? = null,
    val error: String? = null,
)

class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val notificationRepository get() = app.repositories.notificationRepository

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = InboxUiState(loading = true)
            try {
                if (app.sessionStore.get() == null) {
                    _state.value = InboxUiState(
                        loading = false,
                        requiresAuth = true,
                    )
                    return@launch
                }
                try {
                    notificationRepository.markRead()
                } catch (_: Exception) {
                    // Inbox can still render if marking existing notifications read fails.
                }
                val (tasks, feed) = coroutineScope {
                    val tasksDeferred = async { notificationRepository.getTasks() }
                    val feedDeferred = async { notificationRepository.getFeed(limit = 25) }
                    tasksDeferred.await() to feedDeferred.await()
                }
                _state.value = InboxUiState(
                    loading = false,
                    tasks = tasks.items,
                    activity = feed.items,
                    nextCursor = feed.nextCursor,
                )
            } catch (e: Exception) {
                _state.value = InboxUiState(
                    loading = false,
                    error = e.message ?: "Could not load inbox",
                )
            }
        }
    }

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        if (_state.value.loadingMore) return

        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true, error = null)
            try {
                val feed = notificationRepository.getFeed(limit = 25, cursor = cursor)
                _state.value = _state.value.copy(
                    loadingMore = false,
                    activity = (_state.value.activity + feed.items).distinctBy { it.event.eventId },
                    nextCursor = feed.nextCursor,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loadingMore = false,
                    error = e.message ?: "Could not load more activity",
                )
            }
        }
    }

    fun dismissTask(taskId: String) {
        viewModelScope.launch {
            try {
                notificationRepository.dismissTask(taskId)
                _state.value = _state.value.copy(
                    tasks = _state.value.tasks.filterNot { it.taskId == taskId },
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "Could not dismiss task",
                )
            }
        }
    }
}

@Composable
fun InboxScreen(
    onOpenPost: (String) -> Unit,
    onOpenCommunity: (String) -> Unit,
    onOpenCommunityNamespace: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: InboxViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator(
                    color = PirateTokens.colors.accentBrand,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.requiresAuth -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Inbox",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PirateTokens.colors.textPrimary,
                    )
                    StatusCard(
                        title = "Sign in to view inbox",
                        description = "Notifications and moderation tasks appear after you sign in.",
                        tone = StatusTone.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PirateButton(
                        text = "Sign in",
                        onClick = onSignIn,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.error != null && state.tasks.isEmpty() && state.activity.isEmpty() -> {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusCard(
                        title = "Inbox unavailable",
                        description = state.error.orEmpty(),
                        tone = StatusTone.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PirateButton(
                        text = "Retry",
                        onClick = viewModel::load,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        Text(
                            text = "Inbox",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PirateTokens.colors.textPrimary,
                        )
                    }
                    if (state.error != null) {
                        item {
                            StatusCard(
                                title = "Inbox action failed",
                                description = state.error.orEmpty(),
                                tone = StatusTone.Warning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (state.tasks.isEmpty() && state.activity.isEmpty()) {
                        item {
                            StatusCard(
                                title = "Nothing new",
                                description = "Tasks and recent notification activity will appear here.",
                                tone = StatusTone.Default,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (state.tasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Needs action",
                                style = MaterialTheme.typography.titleLarge,
                                color = PirateTokens.colors.textPrimary,
                            )
                        }
                        items(state.tasks, key = { it.taskId }) { task ->
                            TaskCard(
                                task = task,
                                onDismiss = { viewModel.dismissTask(task.taskId) },
                                onOpenCommunity = onOpenCommunity,
                                onOpenCommunityNamespace = onOpenCommunityNamespace,
                            )
                        }
                    }
                    if (state.activity.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent activity",
                                style = MaterialTheme.typography.titleLarge,
                                color = PirateTokens.colors.textPrimary,
                            )
                        }
                        items(state.activity, key = { it.event.eventId }) { item ->
                            ActivityCard(
                                item = item,
                                onOpenPost = onOpenPost,
                            )
                        }
                    }
                    if (state.nextCursor != null) {
                        item {
                            PirateButton(
                                text = "Load more",
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
private fun TaskCard(
    task: UserTask,
    onDismiss: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    onOpenCommunityNamespace: (String) -> Unit,
) {
    val communityName = task.payload.payloadString("community_display_name")
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = taskTitle(task),
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        if (communityName != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = communityName,
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (task.type) {
            "namespace_verification_required" -> PirateButton(
                text = "Open namespace",
                onClick = { onOpenCommunityNamespace(task.subjectId) },
                modifier = Modifier.fillMaxWidth(),
            )
            "membership_review" -> PirateButton(
                text = "Open community",
                onClick = { onOpenCommunity(task.subjectId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (task.type != "membership_review") {
            Spacer(modifier = Modifier.height(8.dp))
            PirateButton(
                text = "Dismiss",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ActivityCard(
    item: NotificationFeedItem,
    onOpenPost: (String) -> Unit,
) {
    val postId = item.targetPostId()
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = eventTitle(item),
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        activityContext(item)?.let { context ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = context,
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.event.createdAt,
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
        if (postId != null) {
            Spacer(modifier = Modifier.height(12.dp))
            PirateButton(
                text = "Open thread",
                onClick = { onOpenPost(postId) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun taskTitle(task: UserTask): String = when (task.type) {
    "namespace_verification_required" -> "Namespace verification required"
    "membership_review" -> "Membership requests"
    else -> task.type.replace("_", " ")
}

private fun eventTitle(item: NotificationFeedItem): String {
    val actor = item.event.payload.payloadString("actor_display_name") ?: "Someone"
    return when (item.event.type) {
        "comment_reply" -> "$actor replied to your comment"
        "post_commented" -> "$actor commented on your post"
        else -> "$actor ${item.event.type.replace("_", " ")}"
    }
}

private fun activityContext(item: NotificationFeedItem): String? =
    item.event.payload.payloadString("comment_excerpt")
        ?: item.event.payload.payloadString("post_title")
        ?: item.event.payload.payloadString("context_label")

private fun NotificationFeedItem.targetPostId(): String? {
    val targetPath = event.payload.payloadString("target_path")
    if (targetPath?.startsWith("/p/") == true) {
        return targetPath.removePrefix("/p/").substringBefore("/")
    }
    if (event.type == "comment_reply") {
        return event.payload.payloadString("thread_root_post_id")
    }
    if (event.type == "post_commented" && event.subjectType == "post") {
        return event.subjectId
    }
    return null
}

private fun kotlinx.serialization.json.JsonObject?.payloadString(key: String): String? {
    val value = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    return value?.trim()?.takeIf { it.isNotBlank() }
}
