package sc.pirate.app.profile

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.model.Profile
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PirateCard

data class ProfileUiState(
    val profile: Profile? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    init {
        loadMyProfile()
    }

    private fun loadMyProfile() {
        viewModelScope.launch {
            try {
                val profile = ApiClient.Profiles.getMe()
                _state.value = ProfileUiState(profile = profile, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load profile",
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        if (state.loading) {
            CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
        } else if (state.profile != null) {
            val profile = state.profile
            val handle = profile.globalHandle?.let { "${it.label}.pirate" } ?: ""

            PirateCard {
                Text(
                    text = profile.displayName ?: handle,
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
                if (profile.bio != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = profile.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PirateTokens.colors.textPrimary,
                    )
                }
            }
        } else if (state.error != null) {
            Text(
                text = state.error!!,
                color = PirateTokens.colors.accentDanger,
            )
        }
    }
}

@Composable
fun ProfileScreen(
    userId: String,
    modifier: Modifier = Modifier,
) {
    val viewModel: ProfileViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    LaunchedEffect(userId) {
        try {
            val profile = ApiClient.Profiles.getByUserId(userId)
        } catch (_: Exception) { }
    }

    ProfileScreen(viewModel = viewModel, modifier = modifier)
}
