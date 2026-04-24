package sc.pirate.app.communities

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.PublicProfileCommunitySummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class YourCommunitiesUiState(
    val loading: Boolean = true,
    val handleLabel: String? = null,
    val communities: List<PublicProfileCommunitySummary> = emptyList(),
    val error: String? = null,
)

class YourCommunitiesViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository

    private val _state = MutableStateFlow(YourCommunitiesUiState())
    val state: StateFlow<YourCommunitiesUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val profile = profileRepository.getMe()
                val handleLabel = profile.primaryHandleLabel()
                if (handleLabel == null) {
                    _state.value = YourCommunitiesUiState(
                        loading = false,
                        error = "Public handle unavailable.",
                    )
                    return@launch
                }

                val publicProfile = profileRepository.getPublicByHandle(handleLabel)
                _state.value = YourCommunitiesUiState(
                    loading = false,
                    handleLabel = publicProfile.resolvedHandleLabel,
                    communities = publicProfile.createdCommunities.sortedByDescending { it.createdAt },
                )
            } catch (e: Exception) {
                _state.value = YourCommunitiesUiState(
                    loading = false,
                    error = e.message ?: "Could not load communities",
                )
            }
        }
    }
}

@Composable
fun YourCommunitiesScreen(
    onNavigateToCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: YourCommunitiesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
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

            state.error != null -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        StatusCard(
                            title = "Communities unavailable",
                            description = state.error.orEmpty(),
                            tone = StatusTone.Warning,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        PirateButton(
                            text = "Retry",
                            onClick = viewModel::load,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            state.communities.isEmpty() -> {
                StatusCard(
                    title = "No communities yet",
                    description = "Communities you create will appear here.",
                    tone = StatusTone.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = "Your communities",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PirateTokens.colors.textPrimary,
                        )
                    }
                    state.handleLabel?.let { handle ->
                        item {
                            Text(
                                text = "@$handle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PirateTokens.colors.textSecondary,
                            )
                        }
                    }
                    items(state.communities, key = { it.communityId }) { community ->
                        PirateCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = community.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = PirateTokens.colors.textPrimary,
                            )
                            Text(
                                text = community.routeSlug ?: community.communityId,
                                style = MaterialTheme.typography.bodySmall,
                                color = PirateTokens.colors.textSecondary,
                            )
                            PirateButton(
                                text = "Open",
                                onClick = { onNavigateToCommunity(community.communityId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sc.pirate.app.api.model.Profile.primaryHandleLabel(): String? =
    globalHandle?.label?.takeIf { it.isNotBlank() }
