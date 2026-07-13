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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.FeedSkeletons

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

    fun loadByWallet(walletAddress: String) {
        if (walletAddress.isBlank()) {
            _state.value = PublicProfileUiState(
                loading = false,
                error = "Profile wallet is unavailable.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = PublicProfileUiState(loading = true)
            try {
                _state.value = PublicProfileUiState(
                    loading = false,
                    profile = profileRepository.getPublicByWallet(walletAddress),
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
    onViewAvailability: (String) -> Unit,
    onMessage: ((String) -> Unit)? = null,
    walletAddress: String? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: PublicProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(handleLabel, walletAddress) {
        val wallet = walletAddress?.trim().orEmpty()
        if (wallet.isNotBlank()) viewModel.loadByWallet(wallet)
        else viewModel.load(handleLabel)
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
                    FeedSkeletons(count = 2, modifier = Modifier.fillMaxSize())
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
                    PirateProfilePage(
                        data = ProfilePageData(
                            profile = profile,
                            viewerContext = ViewerContext.Public,
                            stats = profile.followStats(),
                            walletAddress = profile.primaryWalletAddress,
                        ),
                        onMessage = onMessage,
                        onBook = { onViewAvailability(profile.userId) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun sc.pirate.app.api.model.Profile.followStats(): List<ProfileStat> =
    listOf(
        ProfileStat(label = "Followers", value = (followerCount ?: 0).toString()),
        ProfileStat(label = "Following", value = (followingCount ?: 0).toString()),
    )
