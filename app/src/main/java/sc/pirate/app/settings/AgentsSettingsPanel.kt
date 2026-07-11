package sc.pirate.app.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

private data class AgentsSettingsState(
    val loading: Boolean = true,
    val agents: List<UserAgent> = emptyList(),
    val error: String? = null,
)

private class AgentsSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(AgentsSettingsState())
    val state = _state.asStateFlow()

    fun load() {
        if (!_state.value.loading && _state.value.error == null) return
        viewModelScope.launch {
            _state.value = AgentsSettingsState(loading = true)
            try {
                val result = getApplication<PirateApp>().apiClient.agents.list()
                _state.value = AgentsSettingsState(loading = false, agents = result.items)
            } catch (error: Exception) {
                _state.value = AgentsSettingsState(
                    loading = false,
                    error = error.message ?: "Could not load your agents.",
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
            else -> state.agents.forEach { agent -> AgentCard(agent) }
        }
        Text(
            text = "Registration, credential issuance, and signing will appear here after Android secure-key enrollment is complete.",
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
    }
}

@Composable
private fun AgentCard(agent: UserAgent) {
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
        agent.currentOwnership?.let {
            Text(
                "Provider: ${it.ownershipProvider}",
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}
