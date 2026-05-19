package sc.pirate.app.createcommunity

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.CreateCommunityRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class CreateCommunityUiState(
    val displayName: String = "",
    val description: String = "",
    val membershipMode: String = "open",
    val submitting: Boolean = false,
    val error: String? = null,
    val createdCommunityId: String? = null,
    val provisioningStatus: String? = null,
)

class CreateCommunityViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository

    private val _state = MutableStateFlow(CreateCommunityUiState())
    val state: StateFlow<CreateCommunityUiState> = _state.asStateFlow()

    fun updateDisplayName(value: String) {
        _state.value = _state.value.copy(displayName = value, error = null)
    }

    fun updateDescription(value: String) {
        _state.value = _state.value.copy(description = value, error = null)
    }

    fun selectMembershipMode(value: String) {
        if (value !in setOf("open", "request")) return
        _state.value = _state.value.copy(membershipMode = value, error = null)
    }

    fun submit() {
        val current = _state.value
        val displayName = current.displayName.trim()
        if (displayName.isBlank()) {
            _state.value = current.copy(error = "Name is required.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            try {
                val result = communityRepository.createCommunity(
                    CreateCommunityRequest(
                        displayName = displayName,
                        description = current.description.trim().ifBlank { null },
                        membershipMode = current.membershipMode,
                    ),
                )
                app.knownCommunitiesStore.remember(
                    communityId = result.community.communityId,
                    displayName = result.community.displayName,
                    avatarRef = result.community.avatarRef,
                    routeSlug = result.community.routeSlug,
                )
                _state.value = _state.value.copy(
                    submitting = false,
                    createdCommunityId = result.community.communityId,
                    provisioningStatus = result.job.status,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    submitting = false,
                    error = e.message ?: "Community creation failed",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommunityScreen(
    onBack: () -> Unit,
    onVerifyWithId: () -> Unit,
    onCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CreateCommunityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.createdCommunityId) {
        state.createdCommunityId?.let(onCreated)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Create community", color = PirateTokens.colors.textPrimary) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PirateCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Community basics",
                        style = MaterialTheme.typography.titleLarge,
                        color = PirateTokens.colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::updateDisplayName,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !state.submitting,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::updateDescription,
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        enabled = !state.submitting,
                    )
                }
            }

            item {
                PirateCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Membership",
                        style = MaterialTheme.typography.titleLarge,
                        color = PirateTokens.colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (state.membershipMode == "open") {
                            "Anyone can join immediately."
                        } else {
                            "People can request access before posting."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PirateButton(
                            text = if (state.membershipMode == "open") "Open selected" else "Open",
                            onClick = { viewModel.selectMembershipMode("open") },
                            enabled = !state.submitting && state.membershipMode != "open",
                            modifier = Modifier.weight(1f),
                        )
                        PirateButton(
                            text = if (state.membershipMode == "request") "Request selected" else "Request",
                            onClick = { viewModel.selectMembershipMode("request") },
                            enabled = !state.submitting && state.membershipMode != "request",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                StatusCard(
                    title = "Advanced gates are deferred",
                    description = "This v0 creates standard centralized communities. Gated and 18+ creation need the fuller policy and verification flow.",
                    tone = StatusTone.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.error != null) {
                        FormNote(
                            message = state.error.orEmpty(),
                            tone = FormTone.Error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    PirateButton(
                        text = "Create community",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                        enabled = state.displayName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PirateButton(
                        text = "Verify with ID",
                        onClick = onVerifyWithId,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
