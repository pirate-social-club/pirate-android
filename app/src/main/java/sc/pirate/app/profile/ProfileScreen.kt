package sc.pirate.app.profile

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.Profile
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import sc.pirate.app.ui.shortAddress

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
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    ProfileContent(state = state, onSignIn = onSignIn, modifier = modifier)
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
    ProfileContent(state = state, onSignIn = null, modifier = modifier)
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onSignIn: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        if (state.loading) {
            CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
        } else if (state.requiresAuth && onSignIn != null) {
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
        } else if (state.profile != null) {
            val profile = state.profile
            val handle = profile.globalHandle?.let { "${it.label}.pirate" }.orEmpty()
            val attachmentCount = state.session?.walletAttachments?.size ?: 0
            val primaryWalletAddress = profile.primaryWalletAddress
                ?: state.session?.walletAttachments?.firstOrNull { it.isPrimary }?.walletAddress
                ?: state.session?.walletAttachments?.firstOrNull()?.walletAddress

            PirateCard {
                Text(
                    text = profile.displayName ?: handle.ifBlank { "Profile" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = PirateTokens.colors.textPrimary,
                )
                if (handle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
                if (!profile.bio.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PirateTokens.colors.textPrimary,
                    )
                }
                if (!primaryWalletAddress.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Primary wallet",
                        style = MaterialTheme.typography.labelLarge,
                        color = PirateTokens.colors.textSecondary,
                    )
                    Text(
                        text = shortAddress(primaryWalletAddress),
                        style = MaterialTheme.typography.bodyLarge,
                        color = PirateTokens.colors.textPrimary,
                    )
                }
                if (attachmentCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$attachmentCount attached wallet${if (attachmentCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PirateTokens.colors.textSecondary,
                    )
                }
            }
        } else if (state.error != null) {
            Text(
                text = state.error,
                color = PirateTokens.colors.accentDanger,
            )
        }
    }
}
