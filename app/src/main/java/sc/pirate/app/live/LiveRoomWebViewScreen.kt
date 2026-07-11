package sc.pirate.app.live

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import sc.pirate.app.BuildConfig
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.LiveRoomViewerAttachResponse
import sc.pirate.app.shared.requiresAgeProof
import sc.pirate.app.ui.PhosphorIcons

private sealed interface LiveRoomViewerState {
    data object Loading : LiveRoomViewerState
    data class Ready(val attach: LiveRoomViewerAttachResponse, val title: String) : LiveRoomViewerState
    data class Blocked(val title: String, val message: String) : LiveRoomViewerState
    data class Error(val message: String) : LiveRoomViewerState
}

@Composable
fun LiveRoomWebViewScreen(
    app: PirateApp,
    postId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val session by app.sessionStore.observe().collectAsState(initial = null)
    var viewerState by remember(postId) { mutableStateOf<LiveRoomViewerState>(LiveRoomViewerState.Loading) }

    LaunchedEffect(postId, session?.accessToken) {
        viewerState = LiveRoomViewerState.Loading
        viewerState = try {
            loadLiveRoomViewerState(
                app = app,
                postId = postId,
                hasSession = session != null,
            )
        } catch (error: Exception) {
            LiveRoomViewerState.Error(error.message ?: "Could not open this live room.")
        }
    }

    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val previousChrome = window?.captureChromeState()
        if (window != null) {
            window.enterLiveRoomImmersiveMode()
        }

        onDispose {
            if (window != null && previousChrome != null) {
                window.restoreChromeState(previousChrome)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val state = viewerState) {
            LiveRoomViewerState.Loading -> LiveRoomStatusMessage(
                message = "Connecting to the live room.",
                loading = true,
                modifier = Modifier.fillMaxSize(),
            )
            is LiveRoomViewerState.Ready -> LiveRoomViewerWebView(
                attach = state.attach,
                title = state.title,
                onRenew = { uid ->
                    renewLiveRoomViewerState(
                        app = app,
                        postId = postId,
                        hasSession = session != null,
                        uid = uid,
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
            is LiveRoomViewerState.Blocked -> LiveRoomStatusMessage(
                title = state.title,
                message = state.message,
                modifier = Modifier.fillMaxSize(),
            )
            is LiveRoomViewerState.Error -> LiveRoomStatusMessage(
                title = "Live room",
                message = state.message,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Icon(
                imageVector = PhosphorIcons.X,
                contentDescription = "Back",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun LiveRoomStatusMessage(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    loading: Boolean = false,
) {
    Box(
        modifier = modifier
            .background(Color.Black)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(color = Color.White)
            }
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.76f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiveRoomViewerWebView(
    attach: LiveRoomViewerAttachResponse,
    title: String,
    onRenew: suspend (Long) -> LiveRoomViewerAttachResponse?,
    modifier: Modifier = Modifier,
) {
    val baseOrigin = remember { buildWebBaseOrigin() }
    val html = remember(attach, title) { buildAgoraViewerHtml(attach, title) }
    val loadKey = remember(html) { "viewer:${html.hashCode()}" }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val renewBridge: LiveRoomRenewBridge = remember(onRenew, coroutineScope) {
        LiveRoomRenewBridge(coroutineScope, onRenew)
    }
    val liveRoomWebChromeClient = remember(context) { LiveRoomWebChromeClient(context) }

    DisposableEffect(liveRoomWebChromeClient, renewBridge) {
        onDispose {
            renewBridge.webView = null
            liveRoomWebChromeClient.hideCustomView()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                webViewClient = WebViewClient()
                webChromeClient = liveRoomWebChromeClient
                addJavascriptInterface(renewBridge, "PirateLiveRoom")
                renewBridge.webView = this
                loadLiveRoomHtml(loadKey, html, baseOrigin)
            }
        },
        update = { webView ->
            renewBridge.webView = webView
            webView.loadLiveRoomHtml(loadKey, html, baseOrigin)
        },
    )
}

private class LiveRoomRenewBridge(
    private val scope: CoroutineScope,
    private val onRenew: suspend (Long) -> LiveRoomViewerAttachResponse?,
) {
    @Volatile var webView: WebView? = null

    @JavascriptInterface
    fun renewToken(uidText: String) {
        val uid = uidText.toLongOrNull() ?: return
        scope.launch {
            val renewed = onRenew(uid) ?: return@launch
            val agora = renewed.agora ?: return@launch
            val token = agora.token?.takeIf { it.isNotBlank() } ?: return@launch
            val payload = JSONObject()
                .put("token", token)
                .put("tokenExpiresAt", agora.tokenExpiresAt ?: 0)
                .toString()
            val script = "window.__pirateApplyRenewedToken && window.__pirateApplyRenewedToken(${JSONObject.quote(payload)})"
            webView?.post {
                webView?.evaluateJavascript(script, null)
            }
        }
    }
}

private fun WebView.loadLiveRoomHtml(
    loadKey: String,
    html: String,
    baseOrigin: String,
) {
    if (tag == loadKey) return
    tag = loadKey
    loadDataWithBaseURL(baseOrigin, html, "text/html", "UTF-8", null)
}

private class LiveRoomWebChromeClient(
    private val context: Context,
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var originalSystemUiVisibility: Int? = null

    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        val activity = context.findActivity()
        val nextView = view
        if (activity == null || nextView == null) {
            callback?.onCustomViewHidden()
            return
        }
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        val decorView = activity.window.decorView as? ViewGroup
        if (decorView == null) {
            callback?.onCustomViewHidden()
            return
        }

        originalSystemUiVisibility = activity.window.decorView.systemUiVisibility
        customView = nextView
        customViewCallback = callback
        decorView.addView(
            nextView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        activity.window.enterLiveRoomImmersiveMode()
    }

    override fun onHideCustomView() {
        hideCustomView()
    }

    fun hideCustomView() {
        val view = customView ?: return
        val activity = context.findActivity()
        val decorView = activity?.window?.decorView as? ViewGroup
        decorView?.removeView(view)
        originalSystemUiVisibility?.let { activity?.window?.decorView?.systemUiVisibility = it }
        customViewCallback?.onCustomViewHidden()
        customView = null
        customViewCallback = null
        originalSystemUiVisibility = null
    }
}

private data class WindowChromeState(
    val systemUiVisibility: Int,
    val statusBarColor: Int,
    val navigationBarColor: Int,
)

private const val LIVE_ROOM_IMMERSIVE_FLAGS =
    View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE

private fun Window.captureChromeState(): WindowChromeState =
    WindowChromeState(
        systemUiVisibility = decorView.systemUiVisibility,
        statusBarColor = statusBarColor,
        navigationBarColor = navigationBarColor,
    )

private fun Window.enterLiveRoomImmersiveMode() {
    decorView.systemUiVisibility = LIVE_ROOM_IMMERSIVE_FLAGS
    statusBarColor = android.graphics.Color.TRANSPARENT
    navigationBarColor = android.graphics.Color.TRANSPARENT
}

private fun Window.restoreChromeState(state: WindowChromeState) {
    decorView.systemUiVisibility = state.systemUiVisibility
    statusBarColor = state.statusBarColor
    navigationBarColor = state.navigationBarColor
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private val viewerJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

private suspend fun loadLiveRoomViewerState(
    app: PirateApp,
    postId: String,
    hasSession: Boolean,
): LiveRoomViewerState {
    val postResponse = if (hasSession) {
        runCatching { app.repositories.postRepository.getPost(postId) }
            .getOrElse { app.repositories.postRepository.getPublicPost(postId) }
    } else {
        app.repositories.postRepository.getPublicPost(postId)
    }
    val post = postResponse.post
    if (postResponse.requiresAgeProof()) {
        return LiveRoomViewerState.Blocked(
            title = post.title ?: "Live room",
            message = "18+ proof is required before you can watch this live room.",
        )
    }
    val communityId = post.communityId.takeIf { it.isNotBlank() }
        ?: return LiveRoomViewerState.Blocked(
            title = post.title ?: "Live room",
            message = "This post is missing its community.",
        )
    val liveRoomId = post.anchorLiveRoom?.takeIf { it.isNotBlank() }
        ?: return LiveRoomViewerState.Blocked(
            title = post.title ?: "Live room",
            message = "This post is not attached to a live room.",
        )
    val access = if (hasSession) {
        runCatching { app.repositories.communityRepository.getLiveRoomAccess(communityId, liveRoomId) }
            .getOrElse { app.repositories.communityRepository.getPublicLiveRoomAccess(communityId, liveRoomId) }
    } else {
        app.repositories.communityRepository.getPublicLiveRoomAccess(communityId, liveRoomId)
    }
    val title = access.room.title.takeIf { it.isNotBlank() } ?: post.title ?: "Live room"
    if (!access.access.allowed) {
        val message = when (access.access.decisionReason) {
            "purchase_required" -> "A ticket is required for this live room."
            "membership_required" -> "Community access is required for this live room."
            "not_live" -> "This live room is not live yet."
            else -> "This live room is not available."
        }
        return LiveRoomViewerState.Blocked(title = title, message = message)
    }
    if (access.room.status != "live") {
        return LiveRoomViewerState.Blocked(
            title = title,
            message = when (access.room.status) {
                "ended" -> "This live room has ended."
                "canceled" -> "This live room was canceled."
                else -> "This live room is not live yet."
            },
        )
    }

    val attach = if (hasSession) {
        runCatching { app.repositories.communityRepository.viewerAttachLiveRoom(communityId, liveRoomId) }
            .getOrElse { app.repositories.communityRepository.publicViewerAttachLiveRoom(communityId, liveRoomId) }
    } else {
        app.repositories.communityRepository.publicViewerAttachLiveRoom(communityId, liveRoomId)
    }
    return LiveRoomViewerState.Ready(attach = attach, title = title)
}

private suspend fun renewLiveRoomViewerState(
    app: PirateApp,
    postId: String,
    hasSession: Boolean,
    uid: Long,
): LiveRoomViewerAttachResponse? {
    val postResponse = if (hasSession) {
        runCatching { app.repositories.postRepository.getPost(postId) }
            .getOrElse { app.repositories.postRepository.getPublicPost(postId) }
    } else {
        app.repositories.postRepository.getPublicPost(postId)
    }
    if (postResponse.requiresAgeProof()) return null
    val post = postResponse.post
    val communityId = post.communityId.takeIf { it.isNotBlank() } ?: return null
    val liveRoomId = post.anchorLiveRoom?.takeIf { it.isNotBlank() } ?: return null
    val request = sc.pirate.app.api.model.LiveRoomViewerRenewRequest(uid = uid)
    return if (hasSession) {
        runCatching { app.repositories.communityRepository.viewerRenewLiveRoom(communityId, liveRoomId, request) }
            .getOrElse { app.repositories.communityRepository.publicViewerRenewLiveRoom(communityId, liveRoomId, request) }
    } else {
        app.repositories.communityRepository.publicViewerRenewLiveRoom(communityId, liveRoomId, request)
    }
}

private fun buildAgoraViewerHtml(attach: LiveRoomViewerAttachResponse, title: String): String {
    val agora = attach.agora
    val configJson = viewerJson.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("title", title)
            put("configured", agora?.configured == true)
            put("appId", agora?.appId.orEmpty())
            put("channel", agora?.channel.orEmpty())
            put("token", agora?.token.orEmpty())
            put("tokenExpiresAt", agora?.tokenExpiresAt ?: 0)
            put("uid", agora?.uid ?: 0)
        },
    )
    val configLiteral = JSONObject.quote(configJson)
    return """
        <!doctype html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover" />
            <style>
              html, body, #video { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
              body { color: #fff; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
              #video { position: fixed; inset: 0; }
              #status {
                position: fixed;
                inset: 0;
                display: grid;
                place-items: center;
                padding: 28px;
                text-align: center;
                background: #000;
              }
              .stack { display: grid; gap: 12px; max-width: 420px; }
              .title { font-size: 18px; font-weight: 650; line-height: 1.25; }
              .message { color: rgba(255,255,255,0.72); font-size: 16px; line-height: 1.45; }
            </style>
            <script src="https://download.agora.io/sdk/release/AgoraRTC_N-4.24.0.js"></script>
          </head>
          <body>
            <div id="video"></div>
            <div id="status">
              <div class="stack">
                <div id="title" class="title"></div>
                <div id="message" class="message">Connecting to the live room.</div>
              </div>
            </div>
            <script>
              const config = JSON.parse($configLiteral);
              const statusEl = document.getElementById("status");
              const titleEl = document.getElementById("title");
              const messageEl = document.getElementById("message");
              const videoEl = document.getElementById("video");
              let client = null;
              let renewTimeout = null;
              titleEl.textContent = config.title || "Live room";

              function setMessage(message) {
                messageEl.textContent = message;
              }

              function scheduleRenew() {
                if (!window.PirateLiveRoom || !config.uid || !config.tokenExpiresAt) return;
                if (renewTimeout) window.clearTimeout(renewTimeout);
                const delay = Math.max((config.tokenExpiresAt * 1000) - Date.now() - 60000, 30000);
                renewTimeout = window.setTimeout(() => {
                  try {
                    window.PirateLiveRoom.renewToken(String(config.uid));
                  } catch (_error) {}
                }, delay);
              }

              window.__pirateApplyRenewedToken = async function(payloadJson) {
                try {
                  const payload = JSON.parse(payloadJson);
                  if (!payload.token || !client) return;
                  await client.renewToken(payload.token);
                  config.token = payload.token;
                  config.tokenExpiresAt = payload.tokenExpiresAt || 0;
                  scheduleRenew();
                } catch (error) {
                  setMessage(error && error.message ? error.message : String(error));
                }
              };

              async function joinRoom() {
                if (!config.configured || !config.appId || !config.channel) {
                  setMessage("Browser playback is unavailable for this room.");
                  return;
                }
                if (!window.AgoraRTC) {
                  setMessage("Could not load the live video runtime.");
                  return;
                }

                client = AgoraRTC.createClient({ mode: "live", codec: "vp8" });
                client.on("user-published", async (user, mediaType) => {
                  try {
                    await client.subscribe(user, mediaType);
                    if (mediaType === "video" && user.videoTrack) {
                      statusEl.style.display = "none";
                      videoEl.innerHTML = "";
                      user.videoTrack.play(videoEl);
                    }
                    if (mediaType === "audio" && user.audioTrack) {
                      user.audioTrack.play();
                    }
                  } catch (error) {
                    statusEl.style.display = "grid";
                    setMessage(error && error.message ? error.message : String(error));
                  }
                });
                client.on("user-unpublished", (_user, mediaType) => {
                  if (mediaType === "video") {
                    statusEl.style.display = "grid";
                    setMessage("Connected. Waiting for the broadcaster.");
                  }
                });
                client.on("user-left", () => {
                  statusEl.style.display = "grid";
                  setMessage("Connected. Waiting for the broadcaster.");
                });

                await client.setClientRole("audience");
                await client.join(config.appId, config.channel, config.token || null, config.uid || null);
                setMessage("Connected. Waiting for the broadcaster.");
                scheduleRenew();
                window.addEventListener("pagehide", () => {
                  if (renewTimeout) window.clearTimeout(renewTimeout);
                  client.leave().catch(() => {});
                });
              }

              joinRoom().catch((error) => {
                setMessage(error && error.message ? error.message : String(error));
              });
            </script>
          </body>
        </html>
    """.trimIndent()
}

private fun buildWebBaseOrigin(): String {
    val uri = Uri.parse(BuildConfig.WEB_BASE_URL.trim())
    return "${uri.scheme}://${uri.authority}/"
}
