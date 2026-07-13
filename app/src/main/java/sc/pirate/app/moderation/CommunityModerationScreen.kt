package sc.pirate.app.moderation

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.Community
import sc.pirate.app.api.model.MembershipRequestSummary
import sc.pirate.app.api.model.NamespaceVerificationSession
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FeatureStubScreen
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class NamespaceSettingsUiState(
    val loading: Boolean = true,
    val community: Community? = null,
    val session: NamespaceVerificationSession? = null,
    val family: String = "hns",
    val rootLabel: String = "",
    val starting: Boolean = false,
    val checking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

data class MembershipRequestsUiState(
    val communityId: String? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val requests: List<MembershipRequestSummary> = emptyList(),
    val nextCursor: String? = null,
    val processingRequestId: String? = null,
    val processingDecision: String? = null,
    val error: String? = null,
    val message: String? = null,
)

internal fun removeReviewedMembershipRequest(
    requests: List<MembershipRequestSummary>,
    reviewedRequestId: String,
): List<MembershipRequestSummary> = requests.filterNot { it.id == reviewedRequestId }

class CommunityModerationViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val verificationRepository get() = app.repositories.verificationRepository

    private val _namespaceState = MutableStateFlow(NamespaceSettingsUiState())
    val namespaceState: StateFlow<NamespaceSettingsUiState> = _namespaceState.asStateFlow()

    private val _membershipRequestsState = MutableStateFlow(MembershipRequestsUiState())
    val membershipRequestsState: StateFlow<MembershipRequestsUiState> = _membershipRequestsState.asStateFlow()

    fun loadMembershipRequests(communityId: String, refresh: Boolean = false) {
        val current = _membershipRequestsState.value
        if (current.communityId == communityId && (current.loading || current.refreshing)) return

        viewModelScope.launch {
            val preserveItems = refresh && current.communityId == communityId
            _membershipRequestsState.value = MembershipRequestsUiState(
                communityId = communityId,
                loading = !preserveItems,
                refreshing = preserveItems,
                requests = if (preserveItems) current.requests else emptyList(),
                nextCursor = if (preserveItems) current.nextCursor else null,
            )
            try {
                val response = communityRepository.listMembershipRequests(communityId)
                _membershipRequestsState.value = _membershipRequestsState.value.copy(
                    loading = false,
                    refreshing = false,
                    requests = response.items.filter { it.status == "pending" },
                    nextCursor = response.nextCursor,
                )
            } catch (e: Exception) {
                _membershipRequestsState.value = _membershipRequestsState.value.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Could not load membership requests",
                )
            }
        }
    }

    fun loadMoreMembershipRequests() {
        val current = _membershipRequestsState.value
        val communityId = current.communityId ?: return
        val cursor = current.nextCursor ?: return
        if (current.loading || current.refreshing || current.loadingMore) return

        viewModelScope.launch {
            _membershipRequestsState.value = current.copy(loadingMore = true, error = null)
            try {
                val response = communityRepository.listMembershipRequests(communityId, cursor)
                _membershipRequestsState.value = _membershipRequestsState.value.copy(
                    loadingMore = false,
                    requests = (_membershipRequestsState.value.requests + response.items)
                        .filter { it.status == "pending" }
                        .distinctBy { it.id },
                    nextCursor = response.nextCursor,
                )
            } catch (e: Exception) {
                _membershipRequestsState.value = _membershipRequestsState.value.copy(
                    loadingMore = false,
                    error = e.message ?: "Could not load more membership requests",
                )
            }
        }
    }

    fun reviewMembershipRequest(communityId: String, request: MembershipRequestSummary, approve: Boolean) {
        val current = _membershipRequestsState.value
        if (current.processingRequestId != null || current.communityId != communityId) return

        viewModelScope.launch {
            _membershipRequestsState.value = current.copy(
                processingRequestId = request.id,
                processingDecision = if (approve) "approve" else "reject",
                error = null,
                message = null,
            )
            try {
                val reviewed = communityRepository.reviewMembershipRequest(
                    communityId = communityId,
                    requestId = request.id,
                    approve = approve,
                )
                val label = request.applicantHandle?.trim()?.takeIf { it.isNotBlank() } ?: "Member"
                _membershipRequestsState.value = _membershipRequestsState.value.copy(
                    processingRequestId = null,
                    processingDecision = null,
                    requests = removeReviewedMembershipRequest(
                        _membershipRequestsState.value.requests,
                        reviewed.id,
                    ),
                    message = if (approve) "$label approved." else "$label rejected.",
                )
            } catch (e: Exception) {
                _membershipRequestsState.value = _membershipRequestsState.value.copy(
                    processingRequestId = null,
                    processingDecision = null,
                    error = e.message ?: if (approve) {
                        "Could not approve membership request"
                    } else {
                        "Could not reject membership request"
                    },
                )
            }
        }
    }

    fun clearMembershipRequestMessage() {
        _membershipRequestsState.value = _membershipRequestsState.value.copy(message = null)
    }

    fun loadNamespace(communityId: String) {
        viewModelScope.launch {
            _namespaceState.value = NamespaceSettingsUiState(loading = true)
            try {
                val community = communityRepository.getCommunity(communityId)
                val pendingSessionId = community.pendingNamespaceVerificationSessionId
                val session = pendingSessionId?.let { verificationRepository.getNamespaceSession(it) }
                _namespaceState.value = NamespaceSettingsUiState(
                    loading = false,
                    community = community,
                    session = session,
                    family = session?.family ?: "hns",
                    rootLabel = session?.submittedRootLabel ?: community.routeSlug.orEmpty(),
                    message = if (community.namespaceVerificationId != null) {
                        "Namespace verified."
                    } else {
                        null
                    },
                )
            } catch (e: Exception) {
                _namespaceState.value = NamespaceSettingsUiState(
                    loading = false,
                    error = e.message ?: "Could not load namespace settings",
                )
            }
        }
    }

    fun selectFamily(value: String) {
        if (value !in setOf("hns", "spaces")) return
        _namespaceState.value = _namespaceState.value.copy(family = value, error = null, message = null)
    }

    fun updateRootLabel(value: String) {
        _namespaceState.value = _namespaceState.value.copy(rootLabel = value, error = null, message = null)
    }

    fun startNamespaceSession(communityId: String) {
        val current = _namespaceState.value
        val rootLabel = current.rootLabel.trim()
        if (rootLabel.isBlank()) {
            _namespaceState.value = current.copy(error = "Namespace label is required.")
            return
        }

        viewModelScope.launch {
            _namespaceState.value = _namespaceState.value.copy(starting = true, error = null, message = null)
            try {
                val session = verificationRepository.startNamespaceSession(current.family, rootLabel)
                val community = communityRepository.setPendingNamespaceSession(
                    communityId,
                    session.namespaceVerificationSessionId,
                )
                _namespaceState.value = _namespaceState.value.copy(
                    starting = false,
                    community = community,
                    session = session,
                    family = session.family ?: current.family,
                    rootLabel = session.submittedRootLabel ?: rootLabel,
                    message = "Namespace challenge started.",
                )
            } catch (e: Exception) {
                _namespaceState.value = _namespaceState.value.copy(
                    starting = false,
                    error = e.message ?: "Could not start namespace verification",
                )
            }
        }
    }

    fun completeNamespaceSession(communityId: String, restartChallenge: Boolean = false) {
        val sessionId = _namespaceState.value.session?.namespaceVerificationSessionId ?: return
        viewModelScope.launch {
            _namespaceState.value = _namespaceState.value.copy(checking = true, error = null, message = null)
            try {
                val session = verificationRepository.completeNamespaceSession(sessionId, restartChallenge)
                val namespaceVerificationId = session.namespaceVerificationId
                val community = if (session.status == "verified" && namespaceVerificationId != null) {
                    communityRepository.attachNamespace(communityId, namespaceVerificationId)
                } else {
                    _namespaceState.value.community
                }
                _namespaceState.value = _namespaceState.value.copy(
                    checking = false,
                    community = community,
                    session = session,
                    message = if (session.status == "verified") {
                        "Namespace verified."
                    } else {
                        "Verification status: ${session.status}."
                    },
                )
            } catch (e: Exception) {
                _namespaceState.value = _namespaceState.value.copy(
                    checking = false,
                    error = e.message ?: "Could not complete namespace verification",
                )
            }
        }
    }
}

@Composable
fun CommunityModerationScreen(
    communityId: String,
    section: String?,
    onBack: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (section == null || section == "requests") {
        MembershipRequestsScreen(
            communityId = communityId,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }

    if (section == "namespace") {
        NamespaceSettingsScreen(
            communityId = communityId,
            onBack = onBack,
            onOpenCommunity = onOpenCommunity,
            modifier = modifier,
        )
        return
    }

    val body = "Moderation section \"$section\" for community \"$communityId\" is not implemented yet."

    FeatureStubScreen(
        title = "Moderation",
        body = body,
        modifier = modifier,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NamespaceSettingsScreen(
    communityId: String,
    onBack: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CommunityModerationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.namespaceState.collectAsState()

    LaunchedEffect(communityId) {
        viewModel.loadNamespace(communityId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Namespace",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PhosphorIcons.CaretLeft,
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
        modifier = modifier,
    ) { innerPadding ->
        when {
            state.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.message != null) {
                        item {
                            StatusCard(
                                title = state.message.orEmpty(),
                                description = state.community?.routeSlug?.let { "Community route: c/$it" }
                                    ?: "Continue once the namespace status is verified.",
                                tone = StatusTone.Success,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    if (state.error != null) {
                        item {
                            FormNote(
                                message = state.error.orEmpty(),
                                tone = FormTone.Error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    item {
                        NamespaceStartCard(
                            state = state,
                            onSelectFamily = viewModel::selectFamily,
                            onRootLabelChange = viewModel::updateRootLabel,
                            onStart = { viewModel.startNamespaceSession(communityId) },
                        )
                    }
                    state.session?.let { session ->
                        item {
                            NamespaceChallengeCard(
                                session = session,
                                checking = state.checking,
                                onCheck = { viewModel.completeNamespaceSession(communityId) },
                                onRestart = { viewModel.completeNamespaceSession(communityId, restartChallenge = true) },
                            )
                        }
                    }
                    if (state.community?.namespaceVerificationId != null) {
                        item {
                            PirateButton(
                                text = "Open community",
                                onClick = { onOpenCommunity(communityId) },
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
private fun NamespaceStartCard(
    state: NamespaceSettingsUiState,
    onSelectFamily: (String) -> Unit,
    onRootLabelChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Verify a namespace",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Attach a verified HNS or Spaces root to this community.",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PirateButton(
                text = if (state.family == "hns") "HNS selected" else "HNS",
                onClick = { onSelectFamily("hns") },
                enabled = !state.starting && state.family != "hns",
                modifier = Modifier.weight(1f),
            )
            PirateButton(
                text = if (state.family == "spaces") "Spaces selected" else "Spaces",
                onClick = { onSelectFamily("spaces") },
                enabled = !state.starting && state.family != "spaces",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.rootLabel,
            onValueChange = onRootLabelChange,
            label = { Text("Root label") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.starting,
        )
        Spacer(modifier = Modifier.height(12.dp))
        PirateButton(
            text = if (state.session == null) "Start verification" else "Start new challenge",
            onClick = onStart,
            loading = state.starting,
            enabled = state.rootLabel.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NamespaceChallengeCard(
    session: NamespaceVerificationSession,
    checking: Boolean,
    onCheck: () -> Unit,
    onRestart: () -> Unit,
) {
    PirateCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Challenge",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Status: ${session.status}",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        session.challengeKind?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Kind: $it",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        session.challengeHost?.let {
            Spacer(modifier = Modifier.height(12.dp))
            ChallengeValue(label = "Host", value = it)
        }
        session.challengeTxtValue?.let {
            Spacer(modifier = Modifier.height(12.dp))
            ChallengeValue(label = "TXT value", value = it)
        }
        session.challengePayload?.let {
            Spacer(modifier = Modifier.height(12.dp))
            ChallengeValue(label = "Payload", value = it.toString())
        }
        session.challengeExpiresAt?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Expires: $it",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        session.failureReason?.let {
            Spacer(modifier = Modifier.height(12.dp))
            FormNote(message = it, tone = FormTone.Error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        PirateButton(
            text = "Check verification",
            onClick = onCheck,
            loading = checking,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        PirateButton(
            text = "Restart challenge",
            onClick = onRestart,
            enabled = !checking,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChallengeValue(label: String, value: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = PirateTokens.colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = PirateTokens.colors.textPrimary,
    )
}
