package sc.pirate.app.live

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import org.json.JSONObject
import sc.pirate.app.BuildConfig
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.OnboardingStatus
import sc.pirate.app.api.model.Profile
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.api.model.WalletAttachmentSummary
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.PhosphorIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRoomWebViewScreen(
    app: PirateApp,
    postId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session by app.sessionStore.observe().collectAsState(initial = null)
    val targetUrl = remember(postId) { buildLiveRoomWebUrl(postId) }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Live room",
                        color = PirateTokens.colors.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = PhosphorIcons.X,
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
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            val activeSession = session
            LiveRoomWebView(
                session = activeSession,
                targetUrl = targetUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveRoomWebView(
    session: SessionExchangeResponse?,
    targetUrl: String,
    modifier: Modifier = Modifier,
) {
    val sessionJson = remember(session) { session?.let(::buildWebStoredSessionJson) }
    val bootstrapHtml = remember(sessionJson, targetUrl) {
        sessionJson?.let {
            buildBootstrapHtml(
                sessionJson = it,
                targetUrl = targetUrl,
            )
        }
    }
    val baseOrigin = remember { buildWebBaseOrigin() }
    val loadKey = remember(sessionJson, targetUrl) {
        if (sessionJson == null) {
            "public:$targetUrl"
        } else {
            "session:${session?.accessToken.hashCode()}:$targetUrl"
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
                webChromeClient = WebChromeClient()
                loadLiveRoomUrl(loadKey, bootstrapHtml, baseOrigin, targetUrl)
            }
        },
        update = { webView ->
            webView.loadLiveRoomUrl(loadKey, bootstrapHtml, baseOrigin, targetUrl)
        },
    )
}

private fun WebView.loadLiveRoomUrl(
    loadKey: String,
    bootstrapHtml: String?,
    baseOrigin: String,
    targetUrl: String,
) {
    if (tag == loadKey) return
    tag = loadKey
    if (bootstrapHtml == null) {
        loadUrl(targetUrl)
    } else {
        loadDataWithBaseURL(baseOrigin, bootstrapHtml, "text/html", "UTF-8", null)
    }
}

private val webSessionJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

private fun buildWebStoredSessionJson(session: SessionExchangeResponse): String =
    webSessionJson.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        buildJsonObject {
            put("accessToken", session.accessToken)
            put(
                "user",
                buildJsonObject {
                    put("id", session.user.userId)
                    put("user_id", session.user.userId)
                    val primaryWalletAttachment = session.walletAttachments
                        .firstOrNull { it.isPrimary }
                        ?.walletAttachmentId
                        ?: session.walletAttachments.firstOrNull()?.walletAttachmentId
                    if (!primaryWalletAttachment.isNullOrBlank()) {
                        put("primary_wallet_attachment", primaryWalletAttachment)
                    }
                },
            )
            put("profile", webSessionJson.encodeToJsonElement(Profile.serializer(), session.profile))
            put("onboarding", webSessionJson.encodeToJsonElement(OnboardingStatus.serializer(), session.onboarding))
            put(
                "walletAttachments",
                webSessionJson.encodeToJsonElement(
                    ListSerializer(WalletAttachmentSummary.serializer()),
                    session.walletAttachments,
                ),
            )
            put("storedAt", java.time.Instant.now().toString())
        },
    )

private fun buildBootstrapHtml(sessionJson: String, targetUrl: String): String {
    val sessionLiteral = JSONObject.quote(sessionJson)
    val targetLiteral = JSONObject.quote(targetUrl)
    return """
        <!doctype html>
        <html>
          <head><meta name="viewport" content="width=device-width,initial-scale=1" /></head>
          <body style="margin:0;background:#0f1115;color:#f8fafc;font-family:sans-serif;">
            <script>
              try {
                localStorage.setItem("pirate_session", $sessionLiteral);
                window.location.replace($targetLiteral);
              } catch (error) {
                document.body.textContent = "Could not open the live room.";
              }
            </script>
          </body>
        </html>
    """.trimIndent()
}

private fun buildLiveRoomWebUrl(postId: String): String {
    val base = BuildConfig.WEB_BASE_URL.trim().trimEnd('/')
    return "$base/p/${Uri.encode(postId)}/live?source=android"
}

private fun buildWebBaseOrigin(): String {
    val uri = Uri.parse(BuildConfig.WEB_BASE_URL.trim())
    return "${uri.scheme}://${uri.authority}/"
}
