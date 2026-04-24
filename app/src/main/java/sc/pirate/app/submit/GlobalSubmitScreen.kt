package sc.pirate.app.submit

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import sc.pirate.app.api.model.HomeFeedCommunitySummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class GlobalSubmitUiState(
    val loading: Boolean = true,
    val communities: List<HomeFeedCommunitySummary> = emptyList(),
    val error: String? = null,
)

class GlobalSubmitViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val feedRepository get() = app.repositories.feedRepository

    private val _state = MutableStateFlow(GlobalSubmitUiState())
    val state: StateFlow<GlobalSubmitUiState> = _state.asStateFlow()

    fun loadCommunities() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val feed = feedRepository.home(sort = "best")
                _state.value = GlobalSubmitUiState(
                    loading = false,
                    communities = feed.topCommunities,
                )
            } catch (e: Exception) {
                _state.value = GlobalSubmitUiState(
                    loading = false,
                    error = e.message ?: "Could not load communities",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSubmitScreen(
    onBack: () -> Unit,
    onComposeInCommunity: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GlobalSubmitViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadCommunities()
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Submit",
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
            when {
                state.loading -> {
                    CircularProgressIndicator(
                        color = PirateTokens.colors.accentBrand,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                state.error != null -> {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        StatusCard(
                            title = "Communities unavailable",
                            description = state.error.orEmpty(),
                            tone = StatusTone.Warning,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PirateButton(
                            text = "Retry",
                            onClick = viewModel::loadCommunities,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.communities.isEmpty() -> {
                    StatusCard(
                        title = "No communities available",
                        description = "Join or create a community before posting.",
                        tone = StatusTone.Default,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                text = "Choose a community",
                                style = MaterialTheme.typography.headlineSmall,
                                color = PirateTokens.colors.textPrimary,
                            )
                        }

                        items(state.communities, key = { it.communityId }) { community ->
                            PirateCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = community.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PirateTokens.colors.textPrimary,
                                )
                                Text(
                                    text = "${community.memberCount ?: 0} members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PirateTokens.colors.textSecondary,
                                )
                                PirateButton(
                                    text = "Compose",
                                    onClick = { onComposeInCommunity(community.communityId) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
