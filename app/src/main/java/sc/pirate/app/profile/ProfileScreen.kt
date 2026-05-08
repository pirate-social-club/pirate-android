package sc.pirate.app.profile

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import sc.pirate.app.api.model.Profile
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class ProfileUiState(
    val profile: Profile? = null,
    val session: SessionExchangeResponse? = null,
    val loading: Boolean = true,
    val requiresAuth: Boolean = false,
    val error: String? = null,
)

class MeProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ProfileUiState(loading = true)
            try {
                val session = app.sessionStore.get()
                if (session == null) {
                    _state.value = ProfileUiState(
                        loading = false,
                        requiresAuth = true,
                    )
                    return@launch
                }
                val profile = profileRepository.getMe()
                _state.value = ProfileUiState(profile = profile, session = session, loading = false)
            } catch (e: Exception) {
                _state.value = ProfileUiState(
                    loading = false,
                    error = e.message ?: "Failed to load profile",
                )
            }
        }
    }
}

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val profileRepository get() = app.repositories.profileRepository
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun load(userId: String) {
        if (userId.isBlank()) {
            _state.value = ProfileUiState(
                loading = false,
                error = "Profile is unavailable.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = ProfileUiState(loading = true)
            try {
                val profile = profileRepository.getByUserId(userId)
                _state.value = ProfileUiState(profile = profile, loading = false)
            } catch (e: Exception) {
                _state.value = ProfileUiState(
                    loading = false,
                    error = e.message ?: "Failed to load profile",
                )
            }
        }
    }
}

@Composable
fun MeProfileScreen(
    viewModel: MeProfileViewModel,
    onSignIn: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    ProfileContent(
        state = state,
        viewerContext = ViewerContext.Self,
        onSignIn = onSignIn,
        onEditProfile = onEditProfile,
        modifier = modifier,
    )
}

@Composable
fun UserProfileScreen(
    userId: String,
    viewModel: UserProfileViewModel,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(userId) {
        viewModel.load(userId)
    }

    val state by viewModel.state.collectAsState()
    ProfileContent(
        state = state,
        viewerContext = ViewerContext.Public,
        onSignIn = null,
        onEditProfile = null,
        modifier = modifier,
    )
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    viewerContext: ViewerContext,
    onSignIn: (() -> Unit)?,
    onEditProfile: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (state.loading) {
            CircularProgressIndicator(
                color = PirateTokens.colors.accentBrand,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (state.requiresAuth && onSignIn != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatusCard(
                    title = "Sign in to view profile",
                    description = "Your profile, handle, and connected wallets appear after sign-in.",
                    tone = StatusTone.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                PirateButton(
                    text = "Sign in",
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (state.profile != null) {
            val profile = state.profile
            val primaryWalletAddress = profile.primaryWalletAddress
                ?: state.session?.walletAttachments?.firstOrNull { it.isPrimary }?.walletAddress
                ?: state.session?.walletAttachments?.firstOrNull()?.walletAddress
            PirateProfilePage(
                data = ProfilePageData(
                    profile = profile,
                    viewerContext = viewerContext,
                    stats = profile.followStats(),
                    walletAddress = primaryWalletAddress,
                ),
                onEditProfile = onEditProfile,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (state.error != null) {
            Text(
                text = state.error,
                color = PirateTokens.colors.accentDanger,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

private fun Profile.followStats(): List<ProfileStat> =
    listOf(
        ProfileStat(label = "Followers", value = (followerCount ?: 0).toString()),
        ProfileStat(label = "Following", value = (followingCount ?: 0).toString()),
    )
