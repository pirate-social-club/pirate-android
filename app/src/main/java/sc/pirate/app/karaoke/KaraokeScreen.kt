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
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val playback: KaraokePlaybackState = KaraokePlaybackState(),
    val captureActive: Boolean = false,
    val captureMessage: String? = null,
    val playbackPositionMs: Long = 0,
    val partialTranscript: String = "",
    val latestLineScore: JsonObject? = null,
    val summary: JsonObject? = null,
)

class KaraokeViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val _state = MutableStateFlow(KaraokeUiState())
    val state: StateFlow<KaraokeUiState> = _state.asStateFlow()
    private val controller = KaraokeSessionController()
    private val capture = AudioRecordKaraokeCapture()
    private val playback = ExoPlayerKaraokeInstrumentalPlayback(application)

    private var currentKeyFor: String? = null
    private var currentIdempotencyKey: String? = null
    private var currentPostId: String? = null
    private var playbackSyncJob: Job? = null
    private var playbackPositionJob: Job? = null

    init {
        viewModelScope.launch {
            playback.state.collect { playbackState ->
                _state.update { it.copy(playback = playbackState) }
                if (playbackState.ended && _state.value.captureActive) {
                    stopCapture()
                }
            }
        }
    }

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
                playback.prepare(payload.instrumentalAudioUrl)
                _state.value = KaraokeUiState(
                    loading = false,
                    payload = payload,
                    session = session,
                    playback = playback.state.value,
                    playbackPositionMs = playback.currentPositionMs,
                )
                startPlaybackPositionLoop()
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
                handleServerEvent(message)
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
            val captureNow = captureClockMs()
            val audioNow = playback.currentPositionMs
            controller.start(startedAtAudioMs = audioNow)
            controller.updateCaptureAnchor(KaraokeCaptureAnchor(captureMs = captureNow, songMs = audioNow))
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
            _state.value = _state.value.copy(captureActive = true, captureMessage = "Singing")
            startPlaybackSyncLoop()
            playback.play()
        }.onFailure { error ->
            _state.value = _state.value.copy(
                captureActive = false,
                captureMessage = error.message ?: "Could not start karaoke capture.",
            )
        }
    }

    private fun handleServerEvent(message: String) {
        val event = parseKaraokeServerEvent(message) ?: return
        when (event.type) {
            "stt_partial" -> _state.update { it.copy(partialTranscript = event.text.orEmpty()) }
            "stt_final" -> _state.update { it.copy(partialTranscript = "") }
            "line_score" -> _state.update { it.copy(latestLineScore = event.result, partialTranscript = "") }
            "summary" -> _state.update {
                it.copy(
                    captureActive = false,
                    captureMessage = "Scoring complete.",
                    partialTranscript = "",
                    summary = event.summary,
                )
            }
            "session_error" -> _state.update {
                it.copy(
                    captureActive = false,
                    captureMessage = event.message ?: "Karaoke session error: ${event.code}",
                )
            }
        }
    }

    fun stopCapture() {
        capture.stop()
        playbackSyncJob?.cancel()
        playbackSyncJob = null
        val audioTimeMs = controller.currentAudioTimeMs(captureClockMs()) ?: playback.currentPositionMs
        playback.pause()
        controller.playbackSync(audioTimeMs = audioTimeMs, playing = false)
        controller.finish(audioTimeMs = audioTimeMs)
        _state.value = _state.value.copy(captureActive = false, captureMessage = "Capture stopped.")
    }

    override fun onCleared() {
        capture.stop()
        playbackSyncJob?.cancel()
        playbackSyncJob = null
        playbackPositionJob?.cancel()
        playbackPositionJob = null
        playback.release()
        controller.abort("screen_closed")
        super.onCleared()
    }

    private fun startPlaybackSyncLoop() {
        playbackSyncJob?.cancel()
        playbackSyncJob = viewModelScope.launch {
            while (_state.value.captureActive || playback.state.value.isPlaying || playback.state.value.isBuffering) {
                val captureNow = captureClockMs()
                val audioNow = playback.currentPositionMs
                controller.updateCaptureAnchor(KaraokeCaptureAnchor(captureMs = captureNow, songMs = audioNow))
                controller.playbackSync(audioTimeMs = audioNow, playing = playback.state.value.isPlaying)
                delay(2_000)
            }
        }
    }

    private fun startPlaybackPositionLoop() {
        playbackPositionJob?.cancel()
        playbackPositionJob = viewModelScope.launch {
            while (_state.value.payload != null) {
                _state.update { it.copy(playbackPositionMs = playback.currentPositionMs) }
                delay(100)
            }
        }
    }

    private fun captureClockMs(): Long = System.nanoTime() / 1_000_000L
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
        KaraokeLyricsSurface(
            payload = payload,
            playbackPositionMs = state.playbackPositionMs,
        )
        state.summary?.let { summary ->
            val finalScore = summary.finalScoreValue()
            val scoredLineCount = summary.scoredLineCountValue()
            val lineCount = summary.lineCountValue()
            Text(
                text = buildString {
                    append("Final score")
                    if (finalScore != null) append(": ${scorePercent(finalScore)}")
                    if (scoredLineCount != null && lineCount != null) append(" · $scoredLineCount/$lineCount lines")
                },
                style = MaterialTheme.typography.titleMedium,
                color = PirateTokens.colors.textPrimary,
            )
        }
        state.latestLineScore?.scoreValue()?.let { score ->
            Text(
                text = "Last line: ${scorePercent(score)}",
                style = MaterialTheme.typography.bodyMedium,
                color = PirateTokens.colors.textSecondary,
            )
        }
        if (state.partialTranscript.isNotBlank()) {
            Text(
                text = state.partialTranscript,
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PirateButton(
            text = if (state.captureActive) "Stop" else "Start singing",
            onClick = if (state.captureActive) onStop else onStart,
            enabled = state.playback.error == null && !state.playback.isBuffering,
            modifier = Modifier.fillMaxWidth(),
        )
        state.playback.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
        state.captureMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = PirateTokens.colors.textSecondary,
            )
        }
    }
}

private fun scorePercent(score: Double): String =
    "${(score.coerceIn(0.0, 1.0) * 100).toInt()}%"

@Composable
private fun KaraokeLyricsSurface(
    payload: SongKaraokePayload?,
    playbackPositionMs: Long,
) {
    val lines = payload?.karaokeLines.orEmpty()
    val activeIndex = activeKaraokeLineIndex(lines, playbackPositionMs) ?: return
    val visible = listOf(activeIndex - 1, activeIndex, activeIndex + 1)
        .filter { it in lines.indices }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        visible.forEach { index ->
            val active = index == activeIndex
            Text(
                text = lines[index].text,
                style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (active) PirateTokens.colors.accentBrand else PirateTokens.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
