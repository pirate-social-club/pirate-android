package sc.pirate.app.verification

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import sc.pirate.app.ui.ButtonVariant
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

    fun startVerification(context: android.content.Context? = null) {
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
                if (context != null) {
                    openVeryApp(context)
                }
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
fun VeryVerificationDrawer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val viewModel: VeryVerificationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isPending = state.verificationState == VeryVerificationState.Pending

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PirateTokens.colors.bgPage,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = PirateTokens.colors.surfaceAccent,
                    border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = PhosphorIcons.HandPalm,
                            contentDescription = null,
                            tint = PirateTokens.colors.textPrimary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Text(
                    text = "Prove you're human",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PirateTokens.colors.textPrimary,
                )
            }

            Text(
                text = if (isPending) {
                    "Complete the palm scan in the Very.org app."
                } else {
                    "Use Very to scan your palm. The photo is not saved or stored."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = PirateTokens.colors.textPrimary,
            )

            if (state.error != null) {
                sc.pirate.app.ui.FormNote(message = state.error!!)
            }

            PirateButton(
                text = if (isPending) "Reopen verification" else "Verify",
                onClick = {
                    if (isPending && state.launchUrl != null) {
                        viewModel.openVeryApp(context)
                    } else {
                        viewModel.startVerification(context)
                    }
                },
                loading = state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = PirateTokens.colors.borderSoft)
                Text(
                    text = "Need the Very app?",
                    style = MaterialTheme.typography.labelMedium,
                    color = PirateTokens.colors.textSecondary,
                )
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.weight(1f), color = PirateTokens.colors.borderSoft)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PirateButton(
                    text = "Download",
                    onClick = { openUrl(context, VERY_IOS_DOWNLOAD_URL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    variant = ButtonVariant.Outline,
                    leadingIcon = PhosphorIcons.AppleLogo,
                )
                PirateButton(
                    text = "Download",
                    onClick = { openUrl(context, VERY_ANDROID_DOWNLOAD_URL) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    variant = ButtonVariant.Outline,
                    leadingIcon = PhosphorIcons.AndroidLogo,
                )
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
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
                            description = "Open Very to finish the provider flow. Pirate will stay pending until backend confirmation is available.",
                            tone = StatusTone.Warning,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PirateButton(
                            text = if (state.launchUrl == null) "Download" else "Reopen verification",
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

private const val VERY_ANDROID_DOWNLOAD_URL = "https://play.google.com/store/apps/details?id=xyz.veros.app&pli=1"
private const val VERY_IOS_DOWNLOAD_URL = "https://apps.apple.com/us/app/veryai-proof-of-reality/id6746761869"
