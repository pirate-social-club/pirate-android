package sc.pirate.app.live

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.LiveRoom
import sc.pirate.app.api.model.LiveRoomAgoraBlock
import sc.pirate.app.api.model.LiveRoomAttachRequest
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.ButtonVariant

private const val TOKEN_RENEW_LEAD_SECONDS = 120L
private const val MAX_AGORA_UID = 0xFFFF_FFFFL

private data class ProducerAttach(
    val room: LiveRoom,
    val agora: LiveRoomAgoraBlock?,
)

@Composable
fun LiveRoomBroadcasterScreen(
    communityId: String,
    liveRoomId: String,
    role: String,
    onEnded: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as PirateApp
    val scope = rememberCoroutineScope()
    val normalizedRole = role.takeIf { it == "guest" } ?: "host"
    val isHost = normalizedRole == "host"
    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasAudioPermission by remember {
        mutableStateOf(hasPermission(Manifest.permission.RECORD_AUDIO))
    }
    var hasCameraPermission by remember {
        mutableStateOf(hasPermission(Manifest.permission.CAMERA))
    }
    var loading by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }
    var canceling by remember { mutableStateOf(false) }
    var startAfterPermission by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf<ProducerAttach?>(null) }
    var engine by remember { mutableStateOf<RtcEngine?>(null) }
    var joined by remember { mutableStateOf(false) }
    var renewingToken by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraEnabled by remember { mutableStateOf(true) }
    var localVideoView by remember { mutableStateOf<SurfaceView?>(null) }
    val attachedRoomIsLive = attach?.room?.status == "live"
    val hasBroadcastPermissions = hasAudioPermission && hasCameraPermission
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasAudioPermission = hasPermission(Manifest.permission.RECORD_AUDIO)
        hasCameraPermission = hasPermission(Manifest.permission.CAMERA)
        if (!hasAudioPermission || !hasCameraPermission) {
            startAfterPermission = false
            error = "Camera and microphone access are required to broadcast."
        }
    }

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val previousSystemUiVisibility = view.systemUiVisibility
        val previousStatusBarColor = window?.statusBarColor
        val previousNavigationBarColor = window?.navigationBarColor
        @Suppress("DEPRECATION")
        view.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        window?.statusBarColor = AndroidColor.TRANSPARENT
        window?.navigationBarColor = AndroidColor.TRANSPARENT
        onDispose {
            @Suppress("DEPRECATION")
            view.systemUiVisibility = previousSystemUiVisibility
            if (previousStatusBarColor != null) window.statusBarColor = previousStatusBarColor
            if (previousNavigationBarColor != null) window.navigationBarColor = previousNavigationBarColor
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

    fun renewBroadcastToken(rtcEngine: RtcEngine) {
        if (renewingToken) return
        scope.launch {
            renewingToken = true
            runCatching {
                val refreshed = attachProducer(refresh = true)
                attach = refreshed
                val renewedToken = refreshed.agora?.token?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Attach did not return an Agora token.")
                rtcEngine.renewToken(renewedToken)
            }.onFailure {
                error = it.message ?: "Could not renew broadcast token."
            }
            renewingToken = false
        }
    }

    fun applyCameraEnabled(rtcEngine: RtcEngine, enabled: Boolean) {
        rtcEngine.muteLocalVideoStream(!enabled)
        rtcEngine.enableLocalVideo(enabled)
        if (enabled) {
            rtcEngine.startPreview()
        } else {
            rtcEngine.stopPreview()
        }
    }

    fun joinAgora(nextAttach: ProducerAttach) {
        val agora = nextAttach.agora
        val appId = agora?.appId?.takeIf { it.isNotBlank() }
        val channel = agora?.channel?.takeIf { it.isNotBlank() }
        val token = agora?.token?.takeIf { it.isNotBlank() }
        val uid = agora?.uid?.takeIf { it in 1..MAX_AGORA_UID }?.toInt()
        if (agora?.configured != true || appId == null || channel == null || token == null) {
            error = "Agora is not configured for this live room."
            return
        }
        if (uid == null) {
            error = "Live room returned an invalid Agora UID."
            return
        }
        val eventHandler = object : IRtcEngineEventHandler() {
            override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                mainHandler.post {
                    joined = true
                }
            }

            override fun onLeaveChannel(stats: RtcStats?) {
                mainHandler.post {
                    joined = false
                }
            }

            override fun onError(err: Int) {
                mainHandler.post {
                    error = "Agora error $err"
                }
            }

            override fun onTokenPrivilegeWillExpire(token: String?) {
                mainHandler.post {
                    engine?.let(::renewBroadcastToken)
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
        rtcEngine.setAudioProfile(
            Constants.AUDIO_PROFILE_MUSIC_HIGH_QUALITY_STEREO,
            Constants.AUDIO_SCENARIO_GAME_STREAMING,
        )
        rtcEngine.enableVideo()
        rtcEngine.setVideoEncoderConfiguration(
            VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_640x360,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE,
            ),
        )
        val preview = localVideoView ?: SurfaceView(context).also { localVideoView = it }
        rtcEngine.setupLocalVideo(VideoCanvas(preview, Constants.RENDER_MODE_HIDDEN, 0))
        rtcEngine.muteLocalVideoStream(!cameraEnabled)
        rtcEngine.muteAllRemoteAudioStreams(true)
        rtcEngine.muteAllRemoteVideoStreams(true)
        val result = rtcEngine.joinChannel(token, channel, "", uid)
        if (result != 0) {
            error = "Could not join Agora channel ($result)."
            runCatching { rtcEngine.leaveChannel() }
            runCatching { rtcEngine.stopPreview() }
            RtcEngine.destroy()
            return
        }
        engine = rtcEngine
    }

    fun startBroadcast() {
        if (!hasBroadcastPermissions) {
            startAfterPermission = true
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
            return
        }
        if (loading || joined) return
        scope.launch {
            loading = true
            error = null
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

    LaunchedEffect(hasBroadcastPermissions, startAfterPermission) {
        if (hasBroadcastPermissions && startAfterPermission) {
            startAfterPermission = false
            startBroadcast()
        }
    }

    LaunchedEffect(cameraEnabled, engine) {
        engine?.let { applyCameraEnabled(it, cameraEnabled) }
    }

    LaunchedEffect(attach?.agora?.tokenExpiresAt, engine) {
        val rtcEngine = engine ?: return@LaunchedEffect
        while (true) {
            val expiresAt = attach?.agora?.tokenExpiresAt ?: return@LaunchedEffect
            val now = System.currentTimeMillis() / 1000
            val delaySeconds = (expiresAt - now - TOKEN_RENEW_LEAD_SECONDS).coerceAtLeast(15)
            delay(delaySeconds * 1000)
            renewBroadcastToken(rtcEngine)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { engine?.stopPreview() }
            runCatching { engine?.leaveChannel() }
            if (engine != null) {
                RtcEngine.destroy()
            }
        }
    }

    Box(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { viewContext ->
                    SurfaceView(viewContext).also { surface ->
                        localVideoView = surface
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!hasCameraPermission || !cameraEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (!hasCameraPermission) "Camera permission required" else "Camera off",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.52f), CircleShape),
            ) {
                Icon(
                    imageVector = PhosphorIcons.CaretLeft,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = when {
                    joined && cameraEnabled -> "LIVE"
                    joined -> "AUDIO"
                    loading -> "CONNECTING"
                    else -> "READY"
                },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .background(
                        color = if (joined) Color(0xFFB91C1C).copy(alpha = 0.90f) else Color.Black.copy(alpha = 0.52f),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        error?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 78.dp),
            ) {
                FormNote(message = it, tone = FormTone.Error)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.44f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!joined) {
                PirateButton(
                    text = if (hasBroadcastPermissions) "Start" else "Allow camera and mic",
                    onClick = ::startBroadcast,
                    loading = loading,
                    modifier = Modifier.weight(1f),
                )
                if (isHost && !attachedRoomIsLive && !loading) {
                    PirateButton(
                        text = if (canceling) "Canceling" else "Cancel",
                        onClick = {
                            scope.launch {
                                canceling = true
                                error = null
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
                        variant = ButtonVariant.Outline,
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        cameraEnabled = !cameraEnabled
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.16f), CircleShape),
                ) {
                    Icon(
                        imageVector = PhosphorIcons.VideoCamera,
                        contentDescription = if (cameraEnabled) "Turn camera off" else "Turn camera on",
                        tint = Color.White,
                    )
                }
                TextButton(
                    onClick = {
                        error = null
                        runCatching { engine?.stopPreview() }
                        runCatching { engine?.leaveChannel() }
                        joined = false
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) {
                    Text("Leave")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isHost && attachedRoomIsLive) {
                    PirateButton(
                        text = if (ending) "Ending" else "End",
                        onClick = {
                            scope.launch {
                                ending = true
                                error = null
                                runCatching {
                                    app.repositories.communityRepository.endLiveRoom(communityId, liveRoomId)
                                    engine?.stopPreview()
                                    engine?.leaveChannel()
                                    joined = false
                                    if (attach?.room?.recordingEnabled == true) onEnded() else onBack()
                                }.onFailure {
                                    error = it.message ?: "Could not end live room."
                                }
                                ending = false
                            }
                        },
                        loading = ending,
                    )
                }
            }
        }
    }
}
