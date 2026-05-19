package sc.pirate.app.live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.LiveRoom
import sc.pirate.app.api.model.LiveRoomAgoraBlock
import sc.pirate.app.api.model.LiveRoomAttachRequest
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton

private const val TOKEN_RENEW_LEAD_SECONDS = 120L

private data class ProducerAttach(
    val room: LiveRoom,
    val agora: LiveRoomAgoraBlock?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRoomBroadcasterScreen(
    communityId: String,
    liveRoomId: String,
    role: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as PirateApp
    val scope = rememberCoroutineScope()
    val normalizedRole = role.takeIf { it == "guest" } ?: "host"
    val isHost = normalizedRole == "host"
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var loading by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var canceling by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf<ProducerAttach?>(null) }
    var engine by remember { mutableStateOf<RtcEngine?>(null) }
    var joined by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            startAfterPermission = false
            error = "Microphone access is required to broadcast."
        }
    }

    suspend fun attachProducer(refresh: Boolean): ProducerAttach {
        val request = LiveRoomAttachRequest(refresh = refresh)
        return if (isHost) {
            val response = app.repositories.communityRepository.hostAttachLiveRoom(communityId, liveRoomId, request)
            ProducerAttach(room = response.room, agora = response.agora)
        } else {
            if (!refresh) {
                runCatching { app.repositories.communityRepository.guestAcceptLiveRoom(communityId, liveRoomId) }
            }
            val response = app.repositories.communityRepository.guestAttachLiveRoom(communityId, liveRoomId, request)
            ProducerAttach(room = response.room, agora = response.agora)
        }
    }

    fun joinAgora(nextAttach: ProducerAttach) {
        val agora = nextAttach.agora
        val appId = agora?.appId?.takeIf { it.isNotBlank() }
        val channel = agora?.channel?.takeIf { it.isNotBlank() }
        val token = agora?.token?.takeIf { it.isNotBlank() }
        val uid = agora?.uid?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
        if (agora?.configured != true || appId == null || channel == null || token == null || uid == null) {
            error = "Agora is not configured for this live room."
            return
        }
        val eventHandler = object : IRtcEngineEventHandler() {
            override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                mainHandler.post {
                    joined = true
                    statusMessage = "Broadcasting live."
                }
            }

            override fun onLeaveChannel(stats: RtcStats?) {
                mainHandler.post {
                    joined = false
                    statusMessage = "Left broadcast."
                }
            }

            override fun onError(err: Int) {
                mainHandler.post {
                    error = "Agora error $err"
                }
            }
        }
        val rtcEngine = runCatching {
            RtcEngine.create(context.applicationContext, appId, eventHandler)
        }.getOrElse {
            error = it.message ?: "Could not start Agora."
            return
        }
        rtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
        rtcEngine.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
        rtcEngine.enableAudio()
        val result = rtcEngine.joinChannel(token, channel, "", uid)
        if (result != 0) {
            error = "Could not join Agora channel ($result)."
            runCatching { rtcEngine.leaveChannel() }
            RtcEngine.destroy()
            return
        }
        engine = rtcEngine
    }

    fun startBroadcast() {
        if (!hasAudioPermission) {
            startAfterPermission = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (loading || joined) return
        scope.launch {
            loading = true
            error = null
            statusMessage = "Connecting broadcast..."
            runCatching {
                val nextAttach = attachProducer(refresh = false)
                attach = nextAttach
                joinAgora(nextAttach)
            }.onFailure {
                error = it.message ?: "Could not start broadcast."
            }
            loading = false
        }
    }

    LaunchedEffect(hasAudioPermission, startAfterPermission) {
        if (hasAudioPermission && startAfterPermission) {
            startAfterPermission = false
            startBroadcast()
        }
    }

    LaunchedEffect(attach?.agora?.tokenExpiresAt, engine) {
        val rtcEngine = engine ?: return@LaunchedEffect
        while (true) {
            val expiresAt = attach?.agora?.tokenExpiresAt ?: return@LaunchedEffect
            val now = System.currentTimeMillis() / 1000
            val delaySeconds = (expiresAt - now - TOKEN_RENEW_LEAD_SECONDS).coerceAtLeast(15)
            delay(delaySeconds * 1000)
            runCatching {
                val refreshed = attachProducer(refresh = true)
                attach = refreshed
                refreshed.agora?.token?.takeIf { it.isNotBlank() }?.let(rtcEngine::renewToken)
            }.onFailure {
                error = it.message ?: "Could not renew broadcast token."
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { engine?.leaveChannel() }
            if (engine != null) {
                RtcEngine.destroy()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = PirateTokens.colors.bgPage,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isHost) "Host live room" else "Guest live room",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PhosphorIcons.CaretLeft,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = attach?.room?.title?.takeIf { it.isNotBlank() } ?: "Live room",
                style = MaterialTheme.typography.headlineSmall,
                color = PirateTokens.colors.textPrimary,
            )
            Text(
                text = when {
                    joined -> "Your microphone is live on Agora."
                    loading -> "Connecting to Agora."
                    hasAudioPermission -> "Ready to broadcast."
                    else -> "Microphone permission is required before broadcasting."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = PirateTokens.colors.textSecondary,
            )
            statusMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                )
            }
            error?.let {
                FormNote(message = it, tone = FormTone.Error)
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PirateTokens.colors.accentBrand)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!joined) {
                    PirateButton(
                        text = if (hasAudioPermission) "Start broadcast" else "Allow microphone",
                        onClick = ::startBroadcast,
                        loading = loading,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isHost && !joined && !loading) {
                    PirateButton(
                        text = if (canceling) "Canceling" else "Cancel room",
                        onClick = {
                            scope.launch {
                                canceling = true
                                runCatching {
                                    app.repositories.communityRepository.cancelLiveRoom(communityId, liveRoomId)
                                    onBack()
                                }.onFailure {
                                    error = it.message ?: "Could not cancel live room."
                                }
                                canceling = false
                            }
                        },
                        loading = canceling,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isHost && joined) {
                    PirateButton(
                        text = if (ending) "Ending" else "End live",
                        onClick = {
                            scope.launch {
                                ending = true
                                runCatching {
                                    app.repositories.communityRepository.endLiveRoom(communityId, liveRoomId)
                                    engine?.leaveChannel()
                                    joined = false
                                    onBack()
                                }.onFailure {
                                    error = it.message ?: "Could not end live room."
                                }
                                ending = false
                            }
                        },
                        loading = ending,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (joined) {
                PirateButton(
                    text = "Leave device",
                    onClick = {
                        runCatching { engine?.leaveChannel() }
                        joined = false
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
