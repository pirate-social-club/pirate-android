package sc.pirate.app.verification

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import sc.pirate.app.api.StartVerificationSessionRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.PirateCard
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
    val launchUrl: String? = null,
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

    fun startVerification() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = verificationRepository.startSession(
                    StartVerificationSessionRequest(
                        provider = "very",
                        verificationIntent = "profile_verification",
                    )
                )
                _state.value = _state.value.copy(
                    verificationSessionId = result.verificationSessionId,
                    verificationState = VeryVerificationState.Pending,
                    launchUrl = result.launch?.veryWidget?.verifyUrl,
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
                    launchUrl = session.launch?.veryWidget?.verifyUrl ?: _state.value.launchUrl,
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

    fun openVeryApp(context: android.content.Context) {
        val target = _state.value.launchUrl ?: VERY_DOWNLOAD_URL
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            _state.value = _state.value.copy(
                error = "Very is not available on this device. Install Very, then return to Pirate.",
            )
            return
        }

        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            _state.value = _state.value.copy(
                error = "Very is not available on this device. Install Very, then return to Pirate.",
            )
        }
    }

    private companion object {
        const val VERY_DOWNLOAD_URL = "https://very.org"
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
            PirateCard {
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
                            description = "Open Very to finish the provider flow. Pirate will stay pending until backend confirmation is available.",
                            tone = StatusTone.Warning,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PirateButton(
                            text = if (state.launchUrl == null) "Open Very download" else "Open Very",
                            onClick = { viewModel.openVeryApp(context) },
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
                            text = "Start Very Verification",
                            onClick = viewModel::startVerification,
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
