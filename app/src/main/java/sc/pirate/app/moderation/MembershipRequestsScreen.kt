package sc.pirate.app.moderation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged
import sc.pirate.app.api.model.MembershipRequestSummary
import sc.pirate.app.shared.buildDefaultUserAvatarSrc
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MembershipRequestsScreen(
    communityId: String,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CommunityModerationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.membershipRequestsState.collectAsState()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(communityId) {
        viewModel.loadMembershipRequests(communityId)
    }

    LaunchedEffect(state.nextCursor, state.loadingMore, state.requests.size) {
        if (state.nextCursor == null) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            layout.totalItemsCount > 0 && lastVisible >= layout.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) viewModel.loadMoreMembershipRequests() }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMembershipRequestMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Requests", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PhosphorIcons.CaretLeft,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onOpenRules) {
                        Text("Rules", color = PirateTokens.colors.textPrimary)
                    }
                    TextButton(
                        onClick = { viewModel.loadMembershipRequests(communityId, refresh = true) },
                        enabled = !state.loading && !state.refreshing,
                    ) {
                        if (state.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = PirateTokens.colors.accentBrand,
                            )
                        } else {
                            Text("Refresh", color = PirateTokens.colors.textPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> MembershipRequestsSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )

            state.error != null && state.requests.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    StatusCard(
                        title = "Requests unavailable",
                        description = state.error.orEmpty(),
                        tone = StatusTone.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PirateButton(
                        text = "Try again",
                        onClick = { viewModel.loadMembershipRequests(communityId) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Review who can join",
                                style = MaterialTheme.typography.titleLarge,
                                color = PirateTokens.colors.textPrimary,
                            )
                            Text(
                                text = "Approve people you recognize or reject requests that don't fit this community.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PirateTokens.colors.textSecondary,
                            )
                        }
                    }

                    state.error?.let { error ->
                        item {
                            StatusCard(
                                title = "Some requests may be missing",
                                description = error,
                                tone = StatusTone.Warning,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (state.requests.isEmpty()) {
                        item {
                            StatusCard(
                                title = "All caught up",
                                description = "There are no pending membership requests.",
                                tone = StatusTone.Success,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        items(
                            items = state.requests,
                            key = { it.id },
                        ) { request ->
                            MembershipRequestCard(
                                request = request,
                                processingRequestId = state.processingRequestId,
                                processingDecision = state.processingDecision,
                                onApprove = {
                                    viewModel.reviewMembershipRequest(communityId, it, approve = true)
                                },
                                onReject = {
                                    viewModel.reviewMembershipRequest(communityId, it, approve = false)
                                },
                            )
                        }
                    }

                    if (state.loadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = PirateTokens.colors.accentBrand,
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
private fun MembershipRequestCard(
    request: MembershipRequestSummary,
    processingRequestId: String?,
    processingDecision: String?,
    onApprove: (MembershipRequestSummary) -> Unit,
    onReject: (MembershipRequestSummary) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val processing = processingRequestId == request.id

    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            MembershipApplicantAvatar(request)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = membershipApplicantLabel(request),
                        style = MaterialTheme.typography.titleMedium,
                        color = PirateTokens.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMembershipRequestAge(request.created),
                        style = MaterialTheme.typography.labelMedium,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = request.note?.trim()?.takeIf { it.isNotBlank() } ?: "No message.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PirateButton(
                text = "Approve",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onApprove(request)
                },
                enabled = processingRequestId == null,
                loading = processing && processingDecision == "approve",
                modifier = Modifier.weight(1f),
            )
            PirateButton(
                text = "Reject",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onReject(request)
                },
                enabled = processingRequestId == null,
                loading = processing && processingDecision == "reject",
                variant = ButtonVariant.Outline,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MembershipApplicantAvatar(request: MembershipRequestSummary) {
    val context = LocalContext.current
    val label = membershipApplicantLabel(request)
    val avatar = resolvePublicMediaSrc(request.applicantAvatarRef)
        ?: buildDefaultUserAvatarSrc(request.applicantUser)

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(avatar)
            .crossfade(true)
            .build(),
        contentDescription = label,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(PirateTokens.radius.full))
            .background(PirateTokens.colors.bgPage),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun MembershipRequestsSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(3) {
            PirateCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(PirateTokens.radius.full))
                            .background(PirateTokens.colors.surfaceInteractive),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.48f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PirateTokens.colors.surfaceInteractive),
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PirateTokens.colors.surfaceInteractive),
                        )
                    }
                }
            }
        }
    }
}

internal fun membershipApplicantLabel(request: MembershipRequestSummary): String =
    request.applicantHandle?.trim()?.takeIf { it.isNotBlank() } ?: "Member"

internal fun formatMembershipRequestAge(created: Long, nowSeconds: Long = System.currentTimeMillis() / 1000L): String {
    val elapsed = (nowSeconds - created).coerceAtLeast(0L)
    val units = listOf(
        "y" to 365L * 24L * 60L * 60L,
        "mo" to 30L * 24L * 60L * 60L,
        "w" to 7L * 24L * 60L * 60L,
        "d" to 24L * 60L * 60L,
        "h" to 60L * 60L,
        "m" to 60L,
    )
    for ((label, seconds) in units) {
        if (elapsed >= seconds) return "${elapsed / seconds}$label"
    }
    return "now"
}
