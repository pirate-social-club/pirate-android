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
import androidx.compose.runtime.remember
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
    private var uniqueHumanVerified: Boolean = false
    private var activeRequestedCapabilities: List<String> = emptyList()

    init {
        checkExistingVerification()
    }

    private fun checkExistingVerification() {
        viewModelScope.launch {
            try {
                val status = onboardingRepository.getStatus()
                uniqueHumanVerified = status.uniqueHumanVerificationStatus == "verified"
                _state.value = _state.value.copy(
                    isUniqueHumanVerified = uniqueHumanVerified,
                )
                applyExistingVerificationState()
            } catch (_: Exception) { }
        }
    }

    fun configureVerification(
        verificationIntent: String,
        requestedCapabilities: List<String> = emptyList(),
    ) {
        val previousCapabilities = activeRequestedCapabilities
        activeRequestedCapabilities = resolveRequestedCapabilities(
            verificationIntent = verificationIntent,
            requestedCapabilities = requestedCapabilities,
        )
        if (
            previousCapabilities != activeRequestedCapabilities &&
            _state.value.verificationState != SelfVerificationState.Pending
        ) {
            _state.value = _state.value.copy(verificationSessionId = null)
        }
        applyExistingVerificationState()
    }

    private fun applyExistingVerificationState() {
        val state = _state.value
        if (state.verificationState == SelfVerificationState.Pending || state.verificationSessionId != null) {
            return
        }
        val canUseExistingUniqueHumanStatus =
            activeRequestedCapabilities.isNotEmpty() &&
                activeRequestedCapabilities.all { it == "unique_human" }
        _state.value = state.copy(
            verificationState = if (canUseExistingUniqueHumanStatus && uniqueHumanVerified) {
                SelfVerificationState.Verified
            } else {
                SelfVerificationState.NotStarted
            },
        )
    }

    fun startVerification(
        verificationIntent: String = DEFAULT_VERIFICATION_INTENT,
        requestedCapabilities: List<String> = defaultCapabilitiesForIntent(verificationIntent),
    ) {
        viewModelScope.launch {
            activeRequestedCapabilities = resolveRequestedCapabilities(
                verificationIntent = verificationIntent,
                requestedCapabilities = requestedCapabilities,
            )
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = verificationRepository.startSession(
                    StartVerificationSessionRequest(
                        provider = "self",
                        providerMode = "qr_deeplink",
                        requestedCapabilities = activeRequestedCapabilities,
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
                val verifiedUniqueHuman =
                    _state.value.isUniqueHumanVerified ||
                        activeRequestedCapabilities.contains("unique_human")
                _state.value = _state.value.copy(
                    verificationState = SelfVerificationState.Verified,
                    verificationSessionId = sessionId,
                    isUniqueHumanVerified = verifiedUniqueHuman,
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
        fun defaultCapabilitiesForIntent(verificationIntent: String): List<String> =
            defaultSelfCapabilitiesForIntent(verificationIntent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfVerificationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    verificationIntent: String = "community_creation",
    requestedCapabilities: List<String> = emptyList(),
    onVerified: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PirateApp
    val callbackResult by app.verificationCoordinator.callbackResults.collectAsState()
    val viewModel: SelfVerificationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val resolvedRequestedCapabilities = remember(verificationIntent, requestedCapabilities) {
        resolveRequestedCapabilities(
            verificationIntent = verificationIntent,
            requestedCapabilities = requestedCapabilities,
        )
    }
    val isAgeVerification = resolvedRequestedCapabilities.contains("age_over_18")

    LaunchedEffect(verificationIntent, resolvedRequestedCapabilities) {
        viewModel.configureVerification(
            verificationIntent = verificationIntent,
            requestedCapabilities = resolvedRequestedCapabilities,
        )
    }

    LaunchedEffect(callbackResult) {
        callbackResult?.let(viewModel::handleCallback)
    }

    LaunchedEffect(state.verificationState, isAgeVerification) {
        if (isAgeVerification && state.verificationState == SelfVerificationState.Verified) {
            onVerified?.invoke()
        }
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
                            description = if (isAgeVerification) {
                                "Your Pirate session now has the 18+ capability."
                            } else {
                                "Your Pirate session now has the unique-human capability."
                            },
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
                            description = if (isAgeVerification) {
                                "Start the Self verification flow to prove you are 18+."
                            } else {
                                "Start the Self verification flow to unlock unique-human verification in Pirate."
                            },
                            tone = StatusTone.Default,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PirateButton(
                            text = "Start verification",
                            onClick = {
                                viewModel.startVerification(
                                    verificationIntent = verificationIntent,
                                    requestedCapabilities = resolvedRequestedCapabilities,
                                )
                            },
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

private val supportedSelfCapabilities = setOf("unique_human", "age_over_18", "nationality", "gender")

private fun defaultSelfCapabilitiesForIntent(verificationIntent: String): List<String> =
    when (verificationIntent) {
        "post_access_18_plus" -> listOf("age_over_18")
        else -> listOf("unique_human")
    }

private fun resolveRequestedCapabilities(
    verificationIntent: String,
    requestedCapabilities: List<String>,
): List<String> {
    val selectedCapabilities = requestedCapabilities.ifEmpty {
        defaultSelfCapabilitiesForIntent(verificationIntent)
    }
    return selectedCapabilities
        .map { it.trim() }
        .filter { it in supportedSelfCapabilities }
        .distinct()
}
