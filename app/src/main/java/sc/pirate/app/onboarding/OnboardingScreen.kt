package sc.pirate.app.onboarding

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import sc.pirate.app.api.model.OnboardingStatus
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard

enum class OnboardingPhase {
    ChooseName,
    SuggestedCommunities,
}

data class OnboardingUiState(
    val phase: OnboardingPhase = OnboardingPhase.SuggestedCommunities,
    val loading: Boolean = true,
    val error: String? = null,
    val onboardingStatus: OnboardingStatus? = null,
    val generatedHandle: String = "",
    val actionLoading: Boolean = false,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val onboardingRepository get() = app.repositories.onboardingRepository
    private val profileRepository get() = app.repositories.profileRepository

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    init {
        loadStatus()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            try {
                val status = onboardingRepository.getStatus()
                _state.value = _state.value.copy(
                    onboardingStatus = status,
                    phase = resolvePhase(status),
                    loading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load onboarding status",
                )
            }
        }
    }

    fun updateGeneratedHandle(handle: String) {
        _state.value = _state.value.copy(generatedHandle = handle)
    }

    fun renameHandle() {
        val handle = _state.value.generatedHandle.trim()
        if (handle.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(actionLoading = true, error = null)
            if (!app.termsAcceptanceManager.requireForUgc()) {
                _state.value = _state.value.copy(actionLoading = false)
                return@launch
            }
            try {
                profileRepository.renameHandle(handle.removeSuffix(".pirate"))
                _state.value = _state.value.copy(
                    phase = OnboardingPhase.SuggestedCommunities,
                    actionLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    actionLoading = false,
                    error = e.message ?: "Handle rename failed",
                )
            }
        }
    }

    fun dismissOnboarding(onComplete: () -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(actionLoading = true, error = null)
            try {
                val status = onboardingRepository.dismiss()
                _state.value = _state.value.copy(
                    onboardingStatus = status,
                    actionLoading = false,
                )
                onComplete()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    actionLoading = false,
                    error = e.message ?: "Could not skip onboarding",
                )
            }
        }
    }

    private fun resolvePhase(status: OnboardingStatus): OnboardingPhase =
        when {
            status.hasDismissedOnboarding() -> OnboardingPhase.SuggestedCommunities
            status.cleanupRenameAvailable -> OnboardingPhase.ChooseName
            else -> OnboardingPhase.SuggestedCommunities
        }

    private fun OnboardingStatus.hasDismissedOnboarding(): Boolean =
        onboardingDismissedAt.hasJsonValue() || dismissed.hasJsonValue()

    private fun JsonElement?.hasJsonValue(): Boolean = this != null && this !is JsonNull
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    if (state.loading) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
    ) {
        Text(
            text = "Onboarding",
            style = MaterialTheme.typography.headlineMedium,
            color = PirateTokens.colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (state.error != null) {
            FormNote(message = state.error!!)
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (state.phase) {
            OnboardingPhase.ChooseName -> ChooseNameStep(
                state = state,
                onHandleChange = viewModel::updateGeneratedHandle,
                onContinue = viewModel::renameHandle,
                onSkip = { viewModel.updateGeneratedHandle(""); viewModel.dismissOnboarding(onComplete) },
            )
            OnboardingPhase.SuggestedCommunities -> {
                Text(
                    text = "You're all set!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PirateTokens.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                PirateButton(
                    text = "Continue to Pirate",
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ChooseNameStep(
    state: OnboardingUiState,
    onHandleChange: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    PirateCard {
        Text(
            text = "Choose your handle",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.generatedHandle,
            onValueChange = onHandleChange,
            label = { Text("Handle") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        PirateButton(
            text = "Continue",
            onClick = onContinue,
            enabled = state.generatedHandle.isNotBlank(),
            loading = state.actionLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        PirateButton(
            text = "Skip",
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
