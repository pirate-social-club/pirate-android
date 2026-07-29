package sc.pirate.app.moderation

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiError
import sc.pirate.app.api.model.CreateModerationActionRequest
import sc.pirate.app.api.model.ModerationCaseDetail
import sc.pirate.app.api.model.ModerationCaseSummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class ModerationQueueUiState(
    val communityId: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val cases: List<ModerationCaseSummary> = emptyList(),
    val details: Map<String, ModerationCaseDetail> = emptyMap(),
    val expandedCaseIds: Set<String> = emptySet(),
    val loadingDetailIds: Set<String> = emptySet(),
    val processingCaseId: String? = null,
    val processingAction: String? = null,
    val error: String? = null,
    val message: String? = null,
)

internal fun removeResolvedModerationCase(
    cases: List<ModerationCaseSummary>,
    moderationCaseId: String,
): List<ModerationCaseSummary> = cases.filterNot { it.moderationCaseId == moderationCaseId }

class ModerationQueueViewModel(application: Application) : AndroidViewModel(application) {
    private val repository get() = getApplication<sc.pirate.app.PirateApp>().repositories.communityRepository
    private val _state = MutableStateFlow(ModerationQueueUiState())
    val state: StateFlow<ModerationQueueUiState> = _state.asStateFlow()

    fun load(communityId: String, refresh: Boolean = false) {
        val current = _state.value
        if (current.communityId == communityId && (current.loading || current.refreshing)) return
        viewModelScope.launch {
            val preserve = refresh && current.communityId == communityId
            _state.value = ModerationQueueUiState(
                communityId = communityId,
                loading = !preserve,
                refreshing = preserve,
                cases = if (preserve) current.cases else emptyList(),
                details = if (preserve) current.details else emptyMap(),
            )
            try {
                val result = repository.listModerationCases(communityId)
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    cases = result.items.filter { it.status == "open" },
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = error.message ?: "Could not load moderation queue",
                )
            }
        }
    }

    fun toggleDetails(moderationCaseId: String) {
        val current = _state.value
        if (moderationCaseId in current.expandedCaseIds) {
            _state.value = current.copy(expandedCaseIds = current.expandedCaseIds - moderationCaseId)
            return
        }
        _state.value = current.copy(expandedCaseIds = current.expandedCaseIds + moderationCaseId)
        if (current.details.containsKey(moderationCaseId) || moderationCaseId in current.loadingDetailIds) return
        val communityId = current.communityId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingDetailIds = _state.value.loadingDetailIds + moderationCaseId)
            try {
                val detail = repository.getModerationCase(communityId, moderationCaseId)
                _state.value = _state.value.copy(
                    details = _state.value.details + (moderationCaseId to detail),
                    loadingDetailIds = _state.value.loadingDetailIds - moderationCaseId,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loadingDetailIds = _state.value.loadingDetailIds - moderationCaseId,
                    error = error.message ?: "Could not load moderation evidence",
                )
            }
        }
    }

    fun applyAction(moderationCaseId: String, action: String) {
        val current = _state.value
        val communityId = current.communityId ?: return
        if (current.processingCaseId != null || action !in setOf("restore", "hide", "remove", "dismiss")) return
        viewModelScope.launch {
            _state.value = current.copy(
                processingCaseId = moderationCaseId,
                processingAction = action,
                error = null,
                message = null,
            )
            try {
                repository.resolveModerationCase(
                    communityId,
                    moderationCaseId,
                    CreateModerationActionRequest(actionType = action),
                )
                _state.value = _state.value.copy(
                    processingCaseId = null,
                    processingAction = null,
                    cases = removeResolvedModerationCase(_state.value.cases, moderationCaseId),
                    message = moderationActionSuccessMessage(action),
                )
            } catch (error: Exception) {
                if (error is ApiError && error.status == 400 && error.message == "Moderation case is already resolved") {
                    _state.value = _state.value.copy(
                        processingCaseId = null,
                        processingAction = null,
                        cases = removeResolvedModerationCase(_state.value.cases, moderationCaseId),
                        message = "This case was already resolved.",
                    )
                } else {
                    _state.value = _state.value.copy(
                        processingCaseId = null,
                        processingAction = null,
                        error = error.message ?: "Could not apply moderation action",
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModerationQueueScreen(
    communityId: String,
    onBack: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ModerationQueueViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current
    var pendingAction by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(communityId) { viewModel.load(communityId) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    pendingAction?.let { (caseId, action) ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(moderationActionLabel(action)) },
            text = { Text(moderationActionConfirmation(action)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction = null
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.applyAction(caseId, action)
                }) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Queue", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PhosphorIcons.CaretLeft, "Back", tint = PirateTokens.colors.textPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = onOpenRequests) { Text("Requests", color = PirateTokens.colors.textPrimary) }
                    TextButton(onClick = onOpenRules) { Text("Rules", color = PirateTokens.colors.textPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = PirateTokens.colors.accentBrand) }

            state.error != null && state.cases.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StatusCard("Queue unavailable", state.error.orEmpty(), StatusTone.Warning, modifier = Modifier.fillMaxWidth())
                PirateButton("Try again", { viewModel.load(communityId) }, modifier = Modifier.fillMaxWidth())
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Needs review", style = MaterialTheme.typography.titleLarge, color = PirateTokens.colors.textPrimary)
                            Text("Review flagged posts and comments.", color = PirateTokens.colors.textSecondary)
                        }
                        TextButton(
                            onClick = { viewModel.load(communityId, refresh = true) },
                            enabled = !state.refreshing,
                        ) { Text(if (state.refreshing) "Refreshing…" else "Refresh") }
                    }
                }
                state.error?.let { error -> item {
                    StatusCard("Queue action failed", error, StatusTone.Warning, modifier = Modifier.fillMaxWidth())
                } }
                if (state.cases.isEmpty()) item {
                    StatusCard(
                        "All clear",
                        "Nothing needs moderator attention right now.",
                        StatusTone.Success,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(state.cases, key = { it.moderationCaseId }) { caseItem ->
                    ModerationCaseCard(
                        caseItem = caseItem,
                        detail = state.details[caseItem.moderationCaseId],
                        expanded = caseItem.moderationCaseId in state.expandedCaseIds,
                        loadingDetail = caseItem.moderationCaseId in state.loadingDetailIds,
                        processing = state.processingCaseId != null,
                        processingThis = state.processingCaseId == caseItem.moderationCaseId,
                        processingAction = state.processingAction,
                        onToggleDetails = { viewModel.toggleDetails(caseItem.moderationCaseId) },
                        onAction = { action -> pendingAction = caseItem.moderationCaseId to action },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModerationCaseCard(
    caseItem: ModerationCaseSummary,
    detail: ModerationCaseDetail?,
    expanded: Boolean,
    loadingDetail: Boolean,
    processing: Boolean,
    processingThis: Boolean,
    processingAction: String?,
    onToggleDetails: () -> Unit,
    onAction: (String) -> Unit,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                moderationPriorityLabel(caseItem.priority),
                style = MaterialTheme.typography.labelLarge,
                color = moderationPriorityColor(caseItem.priority),
            )
            Text(formatModerationCaseAge(caseItem.createdAt), color = PirateTokens.colors.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Text(moderationOpenedByLabel(caseItem.openedBy), color = PirateTokens.colors.textSecondary)
        Spacer(Modifier.height(14.dp))
        val preview = caseItem.post
        Text(
            preview?.title?.trim()?.takeIf { it.isNotBlank() }
                ?: if (caseItem.commentId != null) "Comment needs review" else "Post needs review",
            style = MaterialTheme.typography.titleMedium,
            color = PirateTokens.colors.textPrimary,
        )
        preview?.authorHandle?.let { Text("by $it", color = PirateTokens.colors.textSecondary) }
        val excerpt = preview?.body?.trim()?.takeIf { it.isNotBlank() }
            ?: preview?.caption?.trim()?.takeIf { it.isNotBlank() }
        excerpt?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.textSecondary, maxLines = 4)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onToggleDetails, enabled = !processing) {
            Text(if (expanded) "Hide evidence" else "View evidence")
        }
        if (expanded) {
            when {
                loadingDetail -> CircularProgressIndicator(
                    modifier = Modifier.padding(12.dp).size(22.dp),
                    strokeWidth = 2.dp,
                )
                detail != null -> ModerationEvidence(detail)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PirateButton(
                "Approve",
                { onAction("restore") },
                enabled = !processing,
                loading = processingThis && processingAction == "restore",
                modifier = Modifier.weight(1f),
            )
            PirateButton(
                "Remove",
                { onAction("remove") },
                enabled = !processing,
                loading = processingThis && processingAction == "remove",
                variant = ButtonVariant.Outline,
                modifier = Modifier.weight(1f),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAction("hide") }, enabled = !processing) { Text("Hide") }
            TextButton(
                onClick = { onAction("dismiss") },
                enabled = !processing && caseItem.post?.status != "draft",
            ) { Text("Dismiss flag") }
        }
    }
}

@Composable
private fun ModerationEvidence(detail: ModerationCaseDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        detail.signals.forEach { signal ->
            Text(
                "${signal.providerLabel}: ${humanizeModerationValue(signal.signalType)} (${signal.severity})",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        detail.reports.forEach { report ->
            Text(
                "Member report: ${humanizeModerationValue(report.reasonCode)}" +
                    report.note?.trim()?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        if (detail.signals.isEmpty() && detail.reports.isEmpty()) {
            Text("No additional evidence was provided.", color = PirateTokens.colors.textSecondary)
        }
    }
}

internal fun moderationOpenedByLabel(value: String): String = when (value) {
    "platform_analysis" -> "Flagged by Pirate"
    "user_report" -> "Reported by a member"
    "mixed" -> "Reported and automatically flagged"
    else -> "Needs review"
}

internal fun moderationPriorityLabel(value: String): String =
    "${value.replaceFirstChar { it.uppercase() }} priority"

@Composable
private fun moderationPriorityColor(value: String) = when (value) {
    "high" -> PirateTokens.colors.accentDanger
    "medium" -> PirateTokens.colors.accentWarning
    else -> PirateTokens.colors.textSecondary
}

internal fun formatModerationCaseAge(value: String, nowSeconds: Long = System.currentTimeMillis() / 1000L): String {
    val created = runCatching { Instant.parse(value).epochSecond }.getOrNull() ?: return ""
    val elapsed = (nowSeconds - created).coerceAtLeast(0L)
    return when {
        elapsed >= 86_400 -> "${elapsed / 86_400}d"
        elapsed >= 3_600 -> "${elapsed / 3_600}h"
        elapsed >= 60 -> "${elapsed / 60}m"
        else -> "now"
    }
}

internal fun moderationActionLabel(action: String): String = when (action) {
    "restore" -> "Approve content"
    "hide" -> "Hide content"
    "remove" -> "Remove content"
    else -> "Dismiss flag"
}

private fun moderationActionConfirmation(action: String): String = when (action) {
    "restore" -> "Publish this content and close the moderation case?"
    "hide" -> "Hide this content from feeds and close the case?"
    "remove" -> "Remove this content and close the case?"
    else -> "Keep the content as-is and close the case?"
}

private fun moderationActionSuccessMessage(action: String): String = when (action) {
    "restore" -> "Content approved."
    "hide" -> "Content hidden."
    "remove" -> "Content removed."
    else -> "Flag dismissed."
}

private fun humanizeModerationValue(value: String): String = value.replace('_', ' ')

