package sc.pirate.app.inbox

import android.app.Application
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.JsonPrimitive
import sc.pirate.app.api.model.NotificationFeedItem
import sc.pirate.app.api.model.UserTask
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.verification.VeryVerificationDrawer

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
                val (tasks, feed) = coroutineScope {
                    val tasksDeferred = async { notificationRepository.getTasks() }
                    val feedDeferred = async { notificationRepository.getFeed(limit = 25) }
                    tasksDeferred.await() to feedDeferred.await()
                }
                val renderableFeed = feed.items.filter { it.event.type != "xmtp_message" }
                markUnreadVisibleActivityRead(renderableFeed)
                _state.value = InboxUiState(
                    loading = false,
                    tasks = tasks.items,
                    activity = renderableFeed,
                    nextCursor = feed.nextCursor,
                )
            } catch (e: Exception) {
                _state.value = InboxUiState(
                    loading = false,
                    error = e.message ?: "Could not load notifications",
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
                val renderableFeed = feed.items.filter { it.event.type != "xmtp_message" }
                markUnreadVisibleActivityRead(renderableFeed)
                _state.value = _state.value.copy(
                    loadingMore = false,
                    activity = (_state.value.activity + renderableFeed).distinctBy { it.event.id },
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

    private suspend fun markUnreadVisibleActivityRead(items: List<NotificationFeedItem>) {
        val unreadEventIds = items
            .filter { it.receipt.readAt == null }
            .map { it.event.id }
        if (unreadEventIds.isEmpty()) return
        try {
            notificationRepository.markRead(unreadEventIds)
        } catch (_: Exception) {
            // Notifications can still render if read receipts fail to update.
        }
    }

    fun dismissTask(taskId: String) {
        viewModelScope.launch {
            try {
                notificationRepository.dismissTask(taskId)
                _state.value = _state.value.copy(
                    tasks = _state.value.tasks.filterNot { it.id == taskId },
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
    onOpenProfileSettings: () -> Unit,
    onVerifyHuman: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: InboxViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    var veryVerificationDrawerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator(
                    color = PirateTokens.colors.accentBrand,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            state.requiresAuth -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.headlineSmall,
                        color = PirateTokens.colors.textPrimary,
                    )
                    StatusCard(
                        title = "Sign in to view notifications",
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusCard(
                        title = "Notifications unavailable",
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
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    if (state.error != null) {
                        item {
                            StatusCard(
                                title = "Notification action failed",
                                description = state.error.orEmpty(),
                                tone = StatusTone.Warning,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                    if (state.tasks.isEmpty() && state.activity.isEmpty()) {
                        item {
                            StatusCard(
                                title = "No notifications",
                                description = "Tasks and recent activity will appear here.",
                                tone = StatusTone.Default,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                    item {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PirateTokens.colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                    if (state.tasks.isNotEmpty()) {
                        item {
                            NotificationSection {
                                state.tasks.forEachIndexed { index, task ->
                                    if (index > 0) NotificationSeparator()
                                    TaskRow(
                                        task = task,
                                        onOpen = {
                                            openTask(
                                                task = task,
                                                onOpenCommunity = onOpenCommunity,
                                                onOpenCommunityNamespace = onOpenCommunityNamespace,
                                                onOpenProfileSettings = onOpenProfileSettings,
                                                onVerifyHuman = { veryVerificationDrawerOpen = true },
                                            )
                                            if (canAutoClearTaskOnOpen(task)) {
                                                viewModel.dismissTask(task.id)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (state.activity.isNotEmpty()) {
                        item {
                            NotificationSection {
                                state.activity.forEachIndexed { index, item ->
                                    if (index > 0 || state.tasks.isNotEmpty()) NotificationSeparator()
                                    ActivityRow(
                                        item = item,
                                        onOpenPost = onOpenPost,
                                    )
                                }
                            }
                        }
                    }
                    if (state.nextCursor != null) {
                        item {
                            PirateButton(
                                text = "Load more",
                                onClick = viewModel::loadMore,
                                loading = state.loadingMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (veryVerificationDrawerOpen) {
        VeryVerificationDrawer(onDismiss = { veryVerificationDrawerOpen = false })
    }
}

@Composable
private fun NotificationSection(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun NotificationSeparator() {
    HorizontalDivider(
        color = PirateTokens.colors.borderSoft,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 72.dp),
    )
}

@Composable
private fun TaskRow(
    task: UserTask,
    onOpen: () -> Unit,
) {
    NotificationRow(
        icon = taskIcon(task),
        title = taskTitle(task),
        subtext = taskMeta(task),
        unread = true,
        interactive = true,
        onClick = onOpen,
    )
}

@Composable
private fun ActivityRow(
    item: NotificationFeedItem,
    onOpenPost: (String) -> Unit,
) {
    val postId = item.targetPostId()
    NotificationRow(
        icon = activityIcon(item),
        title = eventTitle(item),
        subtext = activityContext(item),
        meta = formatRelativeShort(item.event.created),
        unread = item.receipt.readAt == null,
        interactive = postId != null,
        onClick = { if (postId != null) onOpenPost(postId) },
    )
}

@Composable
private fun NotificationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtext: String?,
    modifier: Modifier = Modifier,
    meta: String? = null,
    unread: Boolean = false,
    interactive: Boolean = false,
    onClick: () -> Unit = {},
) {
    val rowModifier = if (interactive) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = if (unread) PirateTokens.colors.surfaceAccent else PirateTokens.colors.bgPage,
            border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (unread) PirateTokens.colors.textPrimary else PirateTokens.colors.textSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (unread) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textPrimary,
                maxLines = 1,
            )
            val detail = listOfNotNull(subtext, meta).joinToString(" · ").takeIf { it.isNotBlank() }
            if (detail != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
        if (interactive) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = PhosphorIcons.CaretRight,
                contentDescription = null,
                tint = PirateTokens.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun openTask(
    task: UserTask,
    onOpenCommunity: (String) -> Unit,
    onOpenCommunityNamespace: (String) -> Unit,
    onOpenProfileSettings: () -> Unit,
    onVerifyHuman: () -> Unit,
) {
    when (task.type) {
        "unique_human_verification_required" -> onVerifyHuman()
        "profile_completion_suggested",
        "global_handle_cleanup_suggested" -> onOpenProfileSettings()
        "namespace_verification_required" -> onOpenCommunityNamespace(task.subject)
        "membership_review" -> onOpenCommunity(task.subject)
    }
}

private fun canAutoClearTaskOnOpen(task: UserTask): Boolean =
    !task.id.startsWith("synth:") && task.type != "unique_human_verification_required"

private fun taskTitle(task: UserTask): String = when (task.type) {
    "unique_human_verification_required" -> "Verify you're human"
    "profile_completion_suggested" -> "Finish your profile"
    "global_handle_cleanup_suggested" -> "Choose your .pirate name"
    "namespace_verification_required" -> "Verify your community namespace"
    "membership_review" -> "Membership requests"
    else -> task.type.replace("_", " ")
}

private fun taskMeta(task: UserTask): String? {
    val communityName = task.payload.payloadString("community_display_name")
    val requestCount = task.payload.payloadInt("request_count")
    return when (task.type) {
        "unique_human_verification_required" -> "Take a photo of your palm"
        "profile_completion_suggested" -> "Add a name, bio, avatar, or cover"
        "global_handle_cleanup_suggested" -> "Replace your generated handle"
        "membership_review" -> listOfNotNull(
            communityName,
            requestCount?.let { "$it pending" },
        ).joinToString(" · ").takeIf { it.isNotBlank() }
        else -> communityName
    }
}

private fun taskIcon(task: UserTask): androidx.compose.ui.graphics.vector.ImageVector = when (task.type) {
    "unique_human_verification_required" -> PhosphorIcons.HandPalm
    "namespace_verification_required",
    "global_handle_cleanup_suggested" -> PhosphorIcons.IdentificationCard
    "membership_review" -> PhosphorIcons.Users
    "profile_completion_suggested" -> PhosphorIcons.UserCircle
    else -> PhosphorIcons.Bell
}

private fun activityIcon(item: NotificationFeedItem): androidx.compose.ui.graphics.vector.ImageVector = when (item.event.type) {
    "royalty_earned" -> PhosphorIcons.Wallet
    else -> PhosphorIcons.Bell
}

private fun eventTitle(item: NotificationFeedItem): String {
    val actor = item.event.payload.payloadString("actor_display_name") ?: "Someone"
    return when (item.event.type) {
        "comment_reply" -> "$actor replied to your comment"
        "post_commented" -> "$actor commented on your post"
        "royalty_earned" -> "Royalty earned"
        else -> "$actor ${item.event.type.replace("_", " ")}"
    }
}

private fun activityContext(item: NotificationFeedItem): String? =
    if (item.event.type == "royalty_earned") {
        val amount = item.event.payload.payloadString("amount_wip_wei")?.let(::formatWipAmount)
        val title = item.event.payload.payloadString("title")
        when {
            amount != null && title != null -> "+$$amount \$WIP from $title"
            amount != null -> "+$$amount \$WIP"
            else -> title
        }
    } else {
        item.event.payload.payloadString("comment_excerpt")
            ?: item.event.payload.payloadString("post_title")
            ?: item.event.payload.payloadString("context_label")
    }

private fun NotificationFeedItem.targetPostId(): String? {
    val targetPath = event.payload.payloadString("target_path")
    if (targetPath?.startsWith("/p/") == true) {
        return targetPath.removePrefix("/p/").substringBefore("/")
    }
    if (event.type == "comment_reply") {
        return event.payload.payloadString("thread_root_post_id")
    }
    if (event.type == "post_commented" && event.subjectType == "post") {
        return event.subject
    }
    return null
}

private fun kotlinx.serialization.json.JsonObject?.payloadString(key: String): String? {
    val value = (this?.get(key) as? JsonPrimitive)?.contentOrNull
    return value?.trim()?.takeIf { it.isNotBlank() }
}

private fun kotlinx.serialization.json.JsonObject?.payloadInt(key: String): Int? {
    return (this?.get(key) as? JsonPrimitive)?.intOrNull
}

private fun formatRelativeShort(unixSeconds: Long): String {
    val diffSeconds = ((System.currentTimeMillis() / 1000L) - unixSeconds).coerceAtLeast(0L)
    val units = listOf(
        "y" to 365L * 24L * 60L * 60L,
        "mo" to 30L * 24L * 60L * 60L,
        "w" to 7L * 24L * 60L * 60L,
        "d" to 24L * 60L * 60L,
        "h" to 60L * 60L,
        "m" to 60L,
    )
    for ((label, seconds) in units) {
        if (diffSeconds >= seconds) {
            return "${diffSeconds / seconds}$label"
        }
    }
    return "now"
}

private fun formatWipAmount(wei: String): String? {
    val digits = wei.trim().takeIf { it.all(Char::isDigit) } ?: return null
    val padded = digits.padStart(19, '0')
    val whole = padded.dropLast(18).trimStart('0').ifEmpty { "0" }
    val fraction = padded.takeLast(18).take(4).trimEnd('0')
    return if (fraction.isBlank()) whole else "$whole.$fraction"
}
