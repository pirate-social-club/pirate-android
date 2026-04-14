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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.SessionExchangeProof
import sc.pirate.app.api.model.OnboardingStatus
import sc.pirate.app.api.model.RedditVerification
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

enum class OnboardingPhase {
    ImportKarma,
    ChooseName,
    SuggestedCommunities,
}

data class OnboardingUiState(
    val phase: OnboardingPhase = OnboardingPhase.ImportKarma,
    val loading: Boolean = true,
    val error: String? = null,
    val onboardingStatus: OnboardingStatus? = null,
    val redditUsername: String = "",
    val redditVerification: RedditVerification? = null,
    val importJobStatus: String = "not_started",
    val generatedHandle: String = "",
    val actionLoading: Boolean = false,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val sessionStore get() = app.sessionStore

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    init {
        loadStatus()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            try {
                val status = ApiClient.Onboarding.getStatus()
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

    fun updateRedditUsername(username: String) {
        _state.value = _state.value.copy(redditUsername = username)
    }

    fun updateGeneratedHandle(handle: String) {
        _state.value = _state.value.copy(generatedHandle = handle)
    }

    fun startRedditVerification() {
        val username = _state.value.redditUsername.trim()
        if (username.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(actionLoading = true, error = null)
            try {
                val result = ApiClient.Onboarding.startRedditVerification(username)
                _state.value = _state.value.copy(
                    redditVerification = result,
                    actionLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    actionLoading = false,
                    error = e.message ?: "Verification failed",
                )
            }
        }
    }

    fun startRedditImport() {
        val username = _state.value.redditVerification?.redditUsername
            ?: _state.value.redditUsername.trim()
        if (username.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(actionLoading = true, error = null)
            try {
                ApiClient.Onboarding.startRedditImport(username)
                _state.value = _state.value.copy(
                    importJobStatus = "queued",
                    actionLoading = false,
                )
                pollImportStatus()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    actionLoading = false,
                    error = e.message ?: "Import failed",
                )
            }
        }
    }

    private fun pollImportStatus() {
        viewModelScope.launch {
            while (_state.value.importJobStatus in listOf("queued", "running")) {
                kotlinx.coroutines.delay(3000)
                try {
                    val status = ApiClient.Onboarding.getStatus()
                    _state.value = _state.value.copy(onboardingStatus = status)
                    val importStatus = status.redditImportStatus
                    if (importStatus in listOf("succeeded", "failed")) {
                        _state.value = _state.value.copy(
                            importJobStatus = importStatus,
                            phase = resolvePhase(status),
                        )
                        break
                    }
                    _state.value = _state.value.copy(importJobStatus = importStatus)
                } catch (_: Exception) { }
            }
        }
    }

    fun renameHandle() {
        val handle = _state.value.generatedHandle.trim()
        if (handle.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(actionLoading = true, error = null)
            try {
                ApiClient.Profiles.renameHandle(handle.removeSuffix(".pirate"))
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

    private fun resolvePhase(status: OnboardingStatus): OnboardingPhase =
        when {
            status.redditVerificationStatus != "verified" ||
                status.redditImportStatus != "succeeded" -> OnboardingPhase.ImportKarma
            status.cleanupRenameAvailable -> OnboardingPhase.ChooseName
            else -> OnboardingPhase.SuggestedCommunities
        }
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
            OnboardingPhase.ImportKarma -> ImportKarmaStep(
                state = state,
                onUsernameChange = viewModel::updateRedditUsername,
                onVerify = viewModel::startRedditVerification,
                onImport = viewModel::startRedditImport,
                onSkip = onComplete,
            )
            OnboardingPhase.ChooseName -> ChooseNameStep(
                state = state,
                onHandleChange = viewModel::updateGeneratedHandle,
                onContinue = viewModel::renameHandle,
                onSkip = { viewModel.updateGeneratedHandle(""); onComplete() },
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
private fun ImportKarmaStep(
    state: OnboardingUiState,
    onUsernameChange: (String) -> Unit,
    onVerify: () -> Unit,
    onImport: () -> Unit,
    onSkip: () -> Unit,
) {
    val verState = state.redditVerification
    val isVerified = verState?.status == "verified"
    val isCodeReady = verState?.status == "pending"
    val isImportDone = state.importJobStatus == "succeeded"

    PirateCard {
        Text(
            text = "Import your Reddit identity",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isImportDone) {
            StatusCard(
                title = "Import complete",
                description = "Your Reddit karma and identity have been imported.",
                tone = StatusTone.Success,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PirateButton(text = "Continue", onClick = onImport, modifier = Modifier.fillMaxWidth())
        } else if (isVerified && !isImportDone) {
            StatusCard(
                title = "Reddit verified",
                description = "Your Reddit account has been verified. Start the import.",
                tone = StatusTone.Success,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PirateButton(
                text = "Import karma",
                onClick = onImport,
                loading = state.actionLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (isCodeReady) {
            StatusCard(
                title = "Verification code ready",
                description = verState?.verificationHint ?: "Place the verification code on your Reddit profile.",
                tone = StatusTone.Warning,
            )
            Spacer(modifier = Modifier.height(12.dp))
            PirateButton(
                text = "Check verification",
                onClick = onVerify,
                loading = state.actionLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = state.redditUsername,
                onValueChange = onUsernameChange,
                label = { Text("Reddit username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PirateButton(
                text = "Verify Reddit account",
                onClick = onVerify,
                enabled = state.redditUsername.isNotBlank(),
                loading = state.actionLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        PirateButton(
            text = "Skip",
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth(),
        )
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
