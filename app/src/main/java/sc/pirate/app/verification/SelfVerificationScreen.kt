package sc.pirate.app.verification

import android.app.Application
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.CompleteVerificationSessionRequest
import sc.pirate.app.api.StartVerificationSessionRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

enum class SelfVerificationState {
    NotStarted,
    Pending,
    Verified,
}

data class SelfVerificationUiState(
    val verificationState: SelfVerificationState = SelfVerificationState.NotStarted,
    val verificationSessionId: String? = null,
    val launchUrl: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val isUniqueHumanVerified: Boolean = false,
)

class SelfVerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val onboardingRepository get() = app.repositories.onboardingRepository
    private val verificationRepository get() = app.repositories.verificationRepository
    private val verificationCoordinator get() = app.verificationCoordinator

    private val _state = MutableStateFlow(SelfVerificationUiState())
    val state: StateFlow<SelfVerificationUiState> = _state.asStateFlow()
    private var completingSessionId: String? = null

    init {
        checkExistingVerification()
    }

    private fun checkExistingVerification() {
        viewModelScope.launch {
            try {
                val status = onboardingRepository.getStatus()
                _state.value = _state.value.copy(
                    isUniqueHumanVerified = status.uniqueHumanVerificationStatus == "verified",
                    verificationState = if (status.uniqueHumanVerificationStatus == "verified") {
                        SelfVerificationState.Verified
                    } else {
                        SelfVerificationState.NotStarted
                    },
                )
            } catch (_: Exception) { }
        }
    }

    fun startVerification(verificationIntent: String = DEFAULT_VERIFICATION_INTENT) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = verificationRepository.startSession(
                    StartVerificationSessionRequest(
                        provider = "self",
                        providerMode = "qr_deeplink",
                        requestedCapabilities = listOf("unique_human"),
                        verificationIntent = verificationIntent,
                    ),
                )
                verificationCoordinator.savePendingSession(
                    PendingVerificationSession(
                        provider = "self",
                        verificationSessionId = result.verificationSessionId,
                    )
                )
                val callbackUri = VerificationDeepLinks.buildCallbackUri(
                    verificationSessionId = result.verificationSessionId,
                    provider = "self",
                )
                val launchUri = result.buildSelfLaunchUri(callbackUri)
                if (launchUri == null) {
                    verificationCoordinator.clearPendingSession()
                    _state.value = _state.value.copy(
                        verificationState = SelfVerificationState.NotStarted,
                        loading = false,
                        error = "Could not build Self launch link.",
                    )
                    return@launch
                }
                _state.value = _state.value.copy(
                    verificationSessionId = result.verificationSessionId,
                    verificationState = SelfVerificationState.Pending,
                    launchUrl = launchUri.toString(),
                    loading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Could not start verification",
                )
            }
        }
    }

    fun consumeLaunchUrl() {
        _state.value = _state.value.copy(launchUrl = null)
    }

    fun reopenVerification() {
        val sessionId = _state.value.verificationSessionId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val session = verificationRepository.getSession(sessionId)
                val callbackUri = VerificationDeepLinks.buildCallbackUri(
                    verificationSessionId = sessionId,
                    provider = "self",
                )
                val launchUri = session.buildSelfLaunchUri(callbackUri)
                _state.value = _state.value.copy(
                    launchUrl = launchUri?.toString(),
                    loading = false,
                    error = if (launchUri == null) "Could not build Self launch link." else null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Could not reopen verification",
                )
            }
        }
    }

    fun handleCallback(result: VerificationCallbackResult) {
        val provider = when (result) {
            is VerificationCallbackResult.Completed -> result.provider
            is VerificationCallbackResult.Expired -> result.provider
            is VerificationCallbackResult.Failed -> result.provider
        }
        if (provider != "self") return

        when (result) {
            is VerificationCallbackResult.Completed -> completeVerification(result)
            is VerificationCallbackResult.Expired -> {
                verificationCoordinator.clearPendingSession()
                verificationCoordinator.clearCallbackResult()
                _state.value = _state.value.copy(
                    verificationState = SelfVerificationState.NotStarted,
                    loading = false,
                    error = "Verification session expired. Please try again.",
                )
            }
            is VerificationCallbackResult.Failed -> {
                verificationCoordinator.clearPendingSession()
                verificationCoordinator.clearCallbackResult()
                _state.value = _state.value.copy(
                    verificationState = SelfVerificationState.NotStarted,
                    loading = false,
                    error = result.reason,
                )
            }
        }
    }

    private fun completeVerification(result: VerificationCallbackResult.Completed) {
        val sessionId = result.verificationSessionId ?: _state.value.verificationSessionId ?: return
        if (completingSessionId == sessionId) return
        completingSessionId = sessionId

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                verificationRepository.completeSession(
                    verificationSessionId = sessionId,
                    input = CompleteVerificationSessionRequest(
                        proof = result.proof,
                    ),
                )
                verificationCoordinator.clearCallbackResult()
                verificationCoordinator.clearPendingSession()
                _state.value = _state.value.copy(
                    verificationState = SelfVerificationState.Verified,
                    verificationSessionId = sessionId,
                    isUniqueHumanVerified = true,
                    loading = false,
                )
            } catch (e: Exception) {
                completingSessionId = null
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Could not complete verification",
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_VERIFICATION_INTENT = "community_creation"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfVerificationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    verificationIntent: String = "community_creation",
) {
    val context = LocalContext.current
    val app = context.applicationContext as PirateApp
    val callbackResult by app.verificationCoordinator.callbackResults.collectAsState()
    val viewModel: SelfVerificationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(callbackResult) {
        callbackResult?.let(viewModel::handleCallback)
    }

    LaunchedEffect(state.launchUrl) {
        val launchUrl = state.launchUrl ?: return@LaunchedEffect
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(launchUrl)))
        }.onFailure {
            viewModel.consumeLaunchUrl()
        }.onSuccess {
            viewModel.consumeLaunchUrl()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Verify with ID",
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            PirateCard(modifier = Modifier.fillMaxWidth()) {
                when (state.verificationState) {
                    SelfVerificationState.Verified -> {
                        StatusCard(
                            title = "Verification complete",
                            description = "Your Pirate session now has the unique-human capability.",
                            tone = StatusTone.Success,
                        )
                    }

                    SelfVerificationState.Pending -> {
                        StatusCard(
                            title = "Finish verification",
                            description = "Complete the Self flow and return to Pirate to finish verification.",
                            tone = StatusTone.Warning,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PirateButton(
                            text = "Open Self",
                            onClick = viewModel::reopenVerification,
                            loading = state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    SelfVerificationState.NotStarted -> {
                        StatusCard(
                            title = "Verify with ID",
                            description = "Start the Self verification flow to unlock unique-human verification in Pirate.",
                            tone = StatusTone.Default,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PirateButton(
                            text = "Start verification",
                            onClick = { viewModel.startVerification(verificationIntent) },
                            loading = state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (state.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FormNote(message = state.error!!, tone = sc.pirate.app.ui.FormTone.Error)
                }
            }
        }
    }
}
