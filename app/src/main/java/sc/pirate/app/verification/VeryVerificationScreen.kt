package sc.pirate.app.verification

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sc.pirate.app.api.CompleteVerificationSessionRequest
import sc.pirate.app.api.StartVerificationSessionRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

enum class VeryVerificationState {
    NotStarted,
    Pending,
    Verified,
}

data class VeryVerificationUiState(
    val verificationState: VeryVerificationState = VeryVerificationState.NotStarted,
    val verificationSessionId: String? = null,
    val providerMode: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val isUniqueHumanVerified: Boolean = false,
)

class VeryVerificationViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val onboardingRepository get() = app.repositories.onboardingRepository
    private val verificationRepository get() = app.repositories.verificationRepository
    private val _state = MutableStateFlow(VeryVerificationUiState())
    val state: StateFlow<VeryVerificationUiState> = _state.asStateFlow()

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
                        VeryVerificationState.Verified
                    } else {
                        VeryVerificationState.NotStarted
                    },
                )
            } catch (_: Exception) { }
        }
    }

    fun startVerification(context: android.content.Context) {
        if (!VeryNativeSdk.isConfigured()) {
            _state.value = _state.value.copy(
                loading = false,
                error = "Native verification is not configured for this build. VERY_SDK_KEY is missing.",
            )
            return
        }

        if (!VeryNativeSdk.isSupported(context)) {
            _state.value = _state.value.copy(
                loading = false,
                error = "Native verification is not supported on this device.",
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                startNativeVerification(context)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Could not start verification",
                )
            }
        }
    }

    private suspend fun startNativeVerification(context: android.content.Context) {
        val session = try {
            verificationRepository.startSession(
                StartVerificationSessionRequest(
                    provider = "very",
                    providerMode = "native_sdk",
                    verificationIntent = "profile_verification",
                )
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                loading = false,
                error = "Could not start verification session: ${e.message ?: "network error"}",
            )
            return
        }

        if (session.providerMode != "native_sdk" || session.launch?.mode != "native_sdk") {
            _state.value = _state.value.copy(
                loading = false,
                error = "Server did not return a native_sdk session (got mode=${session.launch?.mode ?: "null"}). Native verification is unavailable.",
            )
            return
        }

        _state.value = _state.value.copy(
            verificationSessionId = session.verificationSessionId,
            providerMode = session.providerMode,
            verificationState = VeryVerificationState.Pending,
            loading = true,
        )

        val nativeResult = VeryNativeSdk.authenticate(context)
        val signedToken = nativeResult.signedToken?.takeIf { it.isNotBlank() }
        if (!nativeResult.isSuccess || signedToken == null) {
            _state.value = _state.value.copy(
                verificationState = VeryVerificationState.NotStarted,
                loading = false,
                error = nativeResult.errorMessage ?: "Very native verification did not return a signed token.",
            )
            return
        }

        val completed = verificationRepository.completeSession(
            verificationSessionId = session.verificationSessionId,
            input = CompleteVerificationSessionRequest(
                providerPayloadRef = JsonObject(
                    mapOf(
                        "mode" to JsonPrimitive("native_sdk"),
                        "signed_token" to JsonPrimitive(signedToken),
                    )
                ),
            ),
        )
        val verified = completed.status.equals("verified", ignoreCase = true)
        _state.value = _state.value.copy(
            verificationSessionId = completed.verificationSessionId,
            providerMode = completed.providerMode,
            verificationState = if (verified) VeryVerificationState.Verified else VeryVerificationState.Pending,
            isUniqueHumanVerified = verified,
            loading = false,
            error = if (verified) null else completed.failureReason ?: "Very verification is still pending.",
        )
    }

    fun refreshStatus() {
        val sessionId = _state.value.verificationSessionId ?: return

        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val session = verificationRepository.getSession(sessionId)
                val verified = session.status.equals("verified", ignoreCase = true)
                val expired = session.status.equals("expired", ignoreCase = true)
                val failed = session.status.equals("failed", ignoreCase = true)
                _state.value = _state.value.copy(
                    verificationState = when {
                        verified -> VeryVerificationState.Verified
                        expired || failed -> VeryVerificationState.NotStarted
                        else -> VeryVerificationState.Pending
                    },
                    isUniqueHumanVerified = verified,
                    providerMode = session.providerMode,
                    loading = false,
                    error = when {
                        verified -> null
                        expired -> "Very verification expired. Please try again."
                        failed -> session.failureReason ?: "Very verification failed. Please try again."
                        else -> "Very verification is still pending."
                    },
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Could not refresh verification status",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeryVerificationScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: VeryVerificationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Verification",
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
            modifier = Modifier.padding(innerPadding).padding(16.dp).fillMaxSize(),
        ) {
            Column {
                when (state.verificationState) {
                    VeryVerificationState.Verified -> {
                        StatusCard(
                            title = "Unique-human verification complete",
                            description = "Your Pirate session now has the unique-human capability.",
                            tone = StatusTone.Success,
                        )
                    }
                    VeryVerificationState.Pending -> {
                        StatusCard(
                            title = "Finish your Very verification",
                            description = "Finish the palm scan to complete verification.",
                            tone = StatusTone.Warning,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PirateButton(
                            text = "Retry scan",
                            onClick = { viewModel.startVerification(context) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PirateButton(
                            text = "Check status",
                            onClick = viewModel::refreshStatus,
                            loading = state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    VeryVerificationState.NotStarted -> {
                        StatusCard(
                            title = "Verify with Very",
                            description = "Start the unique-human verification step. You'll scan your palm with Very to confirm you're a unique person.",
                            tone = StatusTone.Default,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PirateButton(
                            text = "Verify",
                            onClick = { viewModel.startVerification(context) },
                            loading = state.loading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (state.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    sc.pirate.app.ui.FormNote(message = state.error!!)
                }
            }
        }
    }
}
