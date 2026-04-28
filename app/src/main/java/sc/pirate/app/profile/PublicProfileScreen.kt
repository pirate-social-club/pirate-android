package sc.pirate.app.profile

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
import sc.pirate.app.api.model.PublicProfileResolution
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class PublicProfileUiState(
    val loading: Boolean = true,
    val profile: PublicProfileResolution? = null,
    val error: String? = null,
)

class PublicProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository

    private val _state = MutableStateFlow(PublicProfileUiState())
    val state: StateFlow<PublicProfileUiState> = _state.asStateFlow()

    fun load(handleLabel: String) {
        if (handleLabel.isBlank()) {
            _state.value = PublicProfileUiState(
                loading = false,
                error = "Profile handle is unavailable.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = PublicProfileUiState(loading = true)
            try {
                _state.value = PublicProfileUiState(
                    loading = false,
                    profile = profileRepository.getPublicByHandle(handleLabel),
                )
            } catch (e: Exception) {
                _state.value = PublicProfileUiState(
                    loading = false,
                    error = e.message ?: "Could not load public profile",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    handleLabel: String,
    onNavigateToCommunity: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PublicProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(handleLabel) {
        viewModel.load(handleLabel)
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.profile?.resolvedHandleLabel ?: handleLabel,
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhosphorIcons.CaretLeft,
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
                            title = "Profile unavailable",
                            description = state.error.orEmpty(),
                            tone = StatusTone.Warning,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PirateButton(
                            text = "Retry",
                            onClick = { viewModel.load(handleLabel) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                state.profile != null -> {
                    val resolution = state.profile!!
                    val profile = resolution.profile
                    val createdCommunities = resolution.createdCommunities.sortedByDescending { it.createdAt }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            PirateCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = profile.displayName ?: resolution.resolvedHandleLabel,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = PirateTokens.colors.textPrimary,
                                )
                                Text(
                                    text = "@${resolution.resolvedHandleLabel}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PirateTokens.colors.textSecondary,
                                )
                                if (!profile.bio.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = profile.bio,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = PirateTokens.colors.textPrimary,
                                    )
                                }
                            }
                        }

                        if (!resolution.isCanonical) {
                            item {
                                StatusCard(
                                    title = "Canonical profile",
                                    description = "@${resolution.requestedHandleLabel} resolved to @${resolution.resolvedHandleLabel}.",
                                    tone = StatusTone.Default,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        if (createdCommunities.isEmpty()) {
                            item {
                                StatusCard(
                                    title = "No public communities",
                                    description = "Communities this profile creates will appear here.",
                                    tone = StatusTone.Default,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            item {
                                Text(
                                    text = "Created communities",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PirateTokens.colors.textPrimary,
                                )
                            }
                            items(createdCommunities, key = { it.communityId }) { community ->
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
    }
}
