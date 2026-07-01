package sc.pirate.app.karaoke

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.KaraokeSession
import sc.pirate.app.api.model.SongKaraokePayload
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone
import java.time.Instant
import java.util.UUID

private const val TAG = "KaraokeScreen"

data class KaraokeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val payload: SongKaraokePayload? = null,
    val session: KaraokeSession? = null,
    val captureActive: Boolean = false,
    val captureMessage: String? = null,
)

class KaraokeViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val _state = MutableStateFlow(KaraokeUiState())
    val state: StateFlow<KaraokeUiState> = _state.asStateFlow()
    private val controller = KaraokeSessionController()
    private val capture = AudioRecordKaraokeCapture()

    private var currentKeyFor: String? = null
    private var currentIdempotencyKey: String? = null
    private var currentPostId: String? = null

    fun load(communityId: String, postId: String, hasSession: Boolean) {
        currentPostId = postId
        viewModelScope.launch {
            _state.value = KaraokeUiState(loading = true)
            if (!hasSession) {
                _state.value = KaraokeUiState(loading = false, error = "Sign in to sing this song.")
                return@launch
            }

            try {
                val payload = app.apiClient.communities.getKaraokePayload(communityId, postId)
                val session = app.apiClient.communities.createKaraokeSession(
                    communityId = communityId,
                    postId = postId,
                    idempotencyKey = sessionIdempotencyKey(communityId, postId),
                )
                validateSession(session)
                _state.value = KaraokeUiState(loading = false, payload = payload, session = session)
            } catch (error: Exception) {
                _state.value = KaraokeUiState(
                    loading = false,
                    error = error.message ?: "Could not prepare karaoke.",
                )
            }
        }
    }

    private fun sessionIdempotencyKey(communityId: String, postId: String): String {
        val keyFor = "$communityId:$postId"
        val existing = currentIdempotencyKey
        if (existing != null && currentKeyFor == keyFor) return existing
        val next = UUID.randomUUID().toString()
        currentKeyFor = keyFor
        currentIdempotencyKey = next
        return next
    }

    private fun validateSession(session: KaraokeSession) {
        require(session.contractObject == "karaoke_session") { "Unexpected karaoke session response." }
        require(session.protocolVersion == 1) { "Unsupported karaoke protocol." }
        require(session.id.isNotBlank() && session.attempt.isNotBlank()) { "Karaoke session is incomplete." }
        require(session.websocketUrl.startsWith("wss://")) { "Karaoke WebSocket URL is invalid." }
        val now = Instant.now().epochSecond
        require(session.tokenExpiresAt > now) { "Karaoke token expired before use." }
        require(session.sessionExpiresAt >= session.tokenExpiresAt) { "Karaoke session expiry is invalid." }
    }

    @SuppressLint("MissingPermission")
    fun startCapture() {
        val current = _state.value
        val session = current.session ?: return
        val postId = currentPostId ?: return
        if (current.captureActive) return

        val listener = object : KaraokeSocketListener {
            override fun onOpen() {
                Log.d(TAG, "Karaoke websocket opened")
            }

            override fun onText(message: String) {
                Log.d(TAG, "Karaoke server event received")
            }

            override fun onClosed() {
                _state.value = _state.value.copy(captureActive = false, captureMessage = "Karaoke connection closed.")
            }

            override fun onFailure(message: String) {
                _state.value = _state.value.copy(captureActive = false, captureMessage = message)
                capture.stop()
            }
        }

        runCatching {
            controller.attach(session = session, postId = postId, listener = listener)
            controller.start(startedAtAudioMs = 0)
            controller.updateCaptureAnchor(KaraokeCaptureAnchor(captureMs = System.nanoTime() / 1_000_000L, songMs = 0))
            capture.start(
                scope = viewModelScope,
                onChunk = { chunk ->
                    if (!controller.sendCapturedChunk(chunk)) {
                        _state.value = _state.value.copy(captureMessage = "Could not send microphone audio.")
                    }
                },
                onFailure = { message ->
                    _state.value = _state.value.copy(captureActive = false, captureMessage = message)
                    controller.abort("capture_failed")
                },
            )
            _state.value = _state.value.copy(captureActive = true, captureMessage = "Listening…")
        }.onFailure { error ->
            _state.value = _state.value.copy(
                captureActive = false,
                captureMessage = error.message ?: "Could not start karaoke capture.",
            )
        }
    }

    fun stopCapture() {
        capture.stop()
        controller.finish(audioTimeMs = 0)
        _state.value = _state.value.copy(captureActive = false, captureMessage = "Capture stopped.")
    }

    override fun onCleared() {
        capture.stop()
        controller.abort("screen_closed")
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeScreen(
    communityId: String,
    postId: String,
    hasSession: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: KaraokeViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startCapture()
    }

    LaunchedEffect(communityId, postId, hasSession) {
        viewModel.load(communityId, postId, hasSession)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Karaoke", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PhosphorIcons.X,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                state.error != null -> StatusCard(
                    title = "Karaoke unavailable",
                    description = state.error.orEmpty(),
                    tone = StatusTone.Warning,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                )
                else -> KaraokeReadySurface(
                    state = state,
                    onStart = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            viewModel.startCapture()
                        } else {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStop = viewModel::stopCapture,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun KaraokeReadySurface(
    state: KaraokeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val payload = state.payload
    val session = state.session
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = payload?.title ?: "Ready to sing",
            style = MaterialTheme.typography.titleLarge,
            color = PirateTokens.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        payload?.artistName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${payload?.karaokeLines?.size ?: 0} timed lines",
            style = MaterialTheme.typography.bodyMedium,
            color = PirateTokens.colors.textSecondary,
        )
        Text(
            text = "Session ${session?.id?.take(10).orEmpty()} prepared.",
            style = MaterialTheme.typography.bodySmall,
            color = PirateTokens.colors.textSecondary,
        )
        PirateButton(
            text = if (state.captureActive) "Stop" else "Start singing",
            onClick = if (state.captureActive) onStop else onStart,
            modifier = Modifier.fillMaxWidth(),
        )
        state.captureMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}
