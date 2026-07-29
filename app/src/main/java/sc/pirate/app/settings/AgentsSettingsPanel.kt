package sc.pirate.app.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.UserAgent
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.security.AgentKeyStore
import sc.pirate.app.security.StoredAgentKey
import sc.pirate.app.security.agentPublicKeysMatch
import sc.pirate.app.security.parseAgentSigningBundle

private data class AgentsSettingsState(
    val loading: Boolean = true,
    val agents: List<UserAgent> = emptyList(),
    val error: String? = null,
    val savingAgentId: String? = null,
    val saveError: String? = null,
    val enrolledKeyAgentIds: Set<String> = emptySet(),
)

private class AgentsSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val keyStore by lazy { AgentKeyStore.create(getApplication()) }
    private val _state = MutableStateFlow(AgentsSettingsState())
    val state = _state.asStateFlow()

    fun load() {
        if (!_state.value.loading && _state.value.error == null) return
        viewModelScope.launch {
            _state.value = AgentsSettingsState(loading = true)
            try {
                val result = getApplication<PirateApp>().apiClient.agents.list()
                val enrolledIds = runCatching { keyStore.list().map { it.agentId }.toSet() }.getOrDefault(emptySet())
                _state.value = AgentsSettingsState(
                    loading = false,
                    agents = result.items,
                    enrolledKeyAgentIds = enrolledIds,
                )
            } catch (error: Exception) {
                _state.value = AgentsSettingsState(
                    loading = false,
                    error = error.message ?: "Could not load your agents.",
                )
            }
        }
    }

    fun updateDisplayName(agentId: String, displayName: String) = updateAgent(agentId) {
        getApplication<PirateApp>().apiClient.agents.updateDisplayName(agentId, displayName.trim())
    }

    fun updateHandle(agentId: String, handle: String) {
        val normalized = handle.trim().removePrefix("@")
        viewModelScope.launch {
            _state.value = _state.value.copy(savingAgentId = agentId, saveError = null)
            try {
                val updatedHandle = getApplication<PirateApp>().apiClient.agents.updateHandle(agentId, normalized)
                _state.value = _state.value.copy(
                    agents = _state.value.agents.map { agent ->
                        if (agent.id == agentId) agent.copy(handle = updatedHandle) else agent
                    },
                    savingAgentId = null,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    savingAgentId = null,
                    saveError = error.message ?: "Could not update agent handle.",
                )
            }
        }
    }

    fun enrollSigningKey(agent: UserAgent, rawBundle: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(savingAgentId = agent.id, saveError = null)
            try {
                val ownership = requireNotNull(agent.currentOwnership) { "Verified agent ownership is required." }
                val expectedPublicKey = requireNotNull(ownership.publicKey) {
                    "This ownership record has no public key to verify against."
                }
                val bundle = parseAgentSigningBundle(rawBundle)
                require(agentPublicKeysMatch(bundle.publicKeyPem, expectedPublicKey)) {
                    "The imported private key does not match this agent's verified public key."
                }
                val now = java.time.Instant.now().toString()
                keyStore.save(
                    StoredAgentKey(
                        agentId = agent.id,
                        displayName = agent.displayName,
                        ownershipProvider = ownership.ownershipProvider,
                        publicKeyPem = bundle.publicKeyPem,
                        privateKeyPem = bundle.privateKeyPem,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                _state.value = _state.value.copy(
                    savingAgentId = null,
                    enrolledKeyAgentIds = _state.value.enrolledKeyAgentIds + agent.id,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    savingAgentId = null,
                    saveError = error.message ?: "Could not enroll the signing key.",
                )
            }
        }
    }

    private fun updateAgent(agentId: String, action: suspend () -> UserAgent) {
        viewModelScope.launch {
            _state.value = _state.value.copy(savingAgentId = agentId, saveError = null)
            try {
                val updated = action()
                keyStore.find(agentId)?.let { stored ->
                    keyStore.save(stored.copy(displayName = updated.displayName, updatedAt = java.time.Instant.now().toString()))
                }
                _state.value = _state.value.copy(
                    agents = _state.value.agents.map { if (it.id == agentId) updated else it },
                    savingAgentId = null,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    savingAgentId = null,
                    saveError = error.message ?: "Could not update agent.",
                )
            }
        }
    }
}

@Composable
internal fun AgentsSettingsPanel() {
    val vm: AgentsSettingsViewModel = viewModel()
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.loading -> CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
            state.error != null -> StatusCard(
                title = "Could not load agents",
                description = state.error.orEmpty(),
                tone = StatusTone.Warning,
                modifier = Modifier.fillMaxWidth(),
            )
            state.agents.isEmpty() -> StatusCard(
                title = "No owned agents",
                description = "Registering a new agent still requires the web ownership-provider flow.",
                tone = StatusTone.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> state.agents.forEach { agent ->
                AgentCard(
                    agent = agent,
                    saving = state.savingAgentId == agent.id,
                    signingKeyEnrolled = agent.id in state.enrolledKeyAgentIds,
                    onUpdateName = { vm.updateDisplayName(agent.id, it) },
                    onUpdateHandle = { vm.updateHandle(agent.id, it) },
                    onEnrollSigningKey = { vm.enrollSigningKey(agent, it) },
                )
            }
        }
        state.saveError?.let { StatusCard("Could not save agent", it, StatusTone.Warning) }
        Text(
            text = "Registration, credential issuance, and signing will appear here after Android secure-key enrollment is complete.",
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

@Composable
private fun AgentCard(
    agent: UserAgent,
    saving: Boolean,
    signingKeyEnrolled: Boolean,
    onUpdateName: (String) -> Unit,
    onUpdateHandle: (String) -> Unit,
    onEnrollSigningKey: (String) -> Unit,
) {
    var editMode by remember { mutableStateOf<String?>(null) }
    var showKeyEnrollment by remember { mutableStateOf(false) }
    editMode?.let { mode ->
        AgentEditDialog(
            title = if (mode == "name") "Agent name" else "Agent handle",
            initialValue = if (mode == "name") agent.displayName else agent.handle?.labelDisplay.orEmpty(),
            saving = saving,
            onSave = {
                if (mode == "name") onUpdateName(it) else onUpdateHandle(it)
                editMode = null
            },
            onDismiss = { if (!saving) editMode = null },
        )
    }
    if (showKeyEnrollment) {
        SigningKeyEnrollmentDialog(
            saving = saving,
            onEnroll = {
                onEnrollSigningKey(it)
                showKeyEnrollment = false
            },
            onDismiss = { if (!saving) showKeyEnrollment = false },
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(agent.displayName, style = MaterialTheme.typography.titleMedium, color = PirateTokens.colors.textPrimary)
        agent.handle?.let {
            Text("@${it.labelDisplay}", style = MaterialTheme.typography.bodyMedium, color = PirateTokens.colors.textSecondary)
        }
        Text(
            "Status: ${agent.status} · ownership: ${agent.currentOwnership?.ownershipState ?: "not verified"}",
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
        Text(
            if (signingKeyEnrolled) "Signing key: encrypted on this device" else "Signing key: not enrolled on this device",
            style = MaterialTheme.typography.bodySmall,
            color = if (signingKeyEnrolled) PirateTokens.colors.accentSuccess else PirateTokens.colors.textSecondary,
        )
        agent.currentOwnership?.let {
            Text(
                "Provider: ${it.ownershipProvider}",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        PirateButton(
            text = "Edit name",
            onClick = { editMode = "name" },
            enabled = !saving,
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!signingKeyEnrolled && agent.status == "active" && agent.currentOwnership?.ownershipState == "verified") {
            PirateButton(
                text = "Enroll signing key",
                onClick = { showKeyEnrollment = true },
                enabled = !saving && agent.currentOwnership.publicKey != null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PirateButton(
            text = if (agent.handle == null) "Claim handle" else "Change handle",
            onClick = { editMode = "handle" },
            enabled = !saving,
            variant = ButtonVariant.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SigningKeyEnrollmentDialog(
    saving: Boolean,
    onEnroll: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var bundle by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enroll signing key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste the agent registration JSON bundle. Android verifies its public key against the ownership record before encrypting the private key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                )
                OutlinedTextField(
                    value = bundle,
                    onValueChange = { bundle = it },
                    enabled = !saving,
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            PirateButton(
                text = "Verify and encrypt",
                onClick = { onEnroll(bundle) },
                loading = saving,
                enabled = !saving && bundle.isNotBlank(),
            )
        },
        dismissButton = {
            PirateButton("Cancel", onDismiss, variant = ButtonVariant.Outline, enabled = !saving)
        },
    )
}

@Composable
private fun AgentEditDialog(
    title: String,
    initialValue: String,
    saving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                enabled = !saving,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            PirateButton(
                text = "Save",
                onClick = { onSave(value) },
                loading = saving,
                enabled = !saving && value.trim().isNotEmpty(),
            )
        },
        dismissButton = {
            PirateButton("Cancel", onDismiss, variant = ButtonVariant.Outline, enabled = !saving)
        },
    )
}
