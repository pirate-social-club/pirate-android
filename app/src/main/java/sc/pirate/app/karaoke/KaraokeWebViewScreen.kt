package sc.pirate.app.karaoke

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.json.JSONObject
import sc.pirate.app.BuildConfig
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.SessionExchangeResponse
import sc.pirate.app.ui.PhosphorIcons
import java.time.Instant

private const val TAG = "KaraokeWebView"

/**
 * P2 viability SPIKE (not the final karaoke UI). Loads the deployed web karaoke route in a
 * WebView from the REAL web origin (so the WS Origin gate sees a genuine origin) and seeds the
 * web session into localStorage["pirate_session"] BEFORE the SPA's first synchronous session
 * read, via addDocumentStartJavaScript.
 *
 * Acceptance criteria are runtime/device-only (in-page fetch() authed, WS connects through the
 * Origin gate, capture starts, server events render) — this screen wires them up and logs page
 * console output so they can be verified on-device. See android-karaoke-study-parity-plan.
 */
private sealed interface SpikeState {
    data object Loading : SpikeState
    data object NeedsAuth : SpikeState
    data class Ready(val seedScript: String) : SpikeState
}

@Composable
fun KaraokeWebViewScreen(
    app: PirateApp,
    postId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var state by remember(postId) { mutableStateOf<SpikeState>(SpikeState.Loading) }

    // Request the app-level mic permission; the in-WebView grant (onPermissionRequest) is
    // separate and only matters once the OS permission is held.
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        Log.d(TAG, "RECORD_AUDIO granted=$granted")
    }
    LaunchedEffect(Unit) {
        if (!micGranted) micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(postId) {
        state = SpikeState.Loading
        val session = app.sessionStore.get()
        state = if (session == null) SpikeState.NeedsAuth else SpikeState.Ready(buildSeedScript(session))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (val current = state) {
            SpikeState.Loading -> SpikeMessage("Preparing karaoke…")
            SpikeState.NeedsAuth -> SpikeMessage("Sign in to use karaoke.")
            is SpikeState.Ready -> KaraokeWebView(
                postId = postId,
                seedScript = current.seedScript,
                modifier = Modifier.fillMaxSize(),
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        ) {
            Icon(PhosphorIcons.X, contentDescription = "Back", tint = Color.White)
        }
    }
}

@Composable
private fun SpikeMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KaraokeWebView(
    postId: String,
    seedScript: String,
    modifier: Modifier = Modifier,
) {
    val webOrigin = remember { buildWebOrigin() }
    val karaokeUrl = remember(postId, webOrigin) { "$webOrigin/p/${Uri.encode(postId)}/karaoke" }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                webChromeClient = KaraokeWebChromeClient(webOrigin)

                val docStartSupported =
                    WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
                if (docStartSupported) {
                    // Inject the seed BEFORE any page script for this origin — satisfies the
                    // "seed before first synchronous session read" constraint on a direct load.
                    WebViewCompat.addDocumentStartJavaScript(this, seedScript, setOf(webOrigin))
                    webViewClient = WebViewClient()
                    loadUrl(karaokeUrl)
                } else {
                    // Fallback: seed on first load, then reload once so the SPA module
                    // re-initializes with the seed already present.
                    Log.w(TAG, "DOCUMENT_START_SCRIPT unsupported; using seed-then-reload fallback")
                    webViewClient = SeedThenReloadClient(seedScript)
                    loadUrl(karaokeUrl)
                }
            }
        },
    )
}

/** Fallback for WebView providers without DOCUMENT_START_SCRIPT: seed + reload exactly once. */
private class SeedThenReloadClient(private val seedScript: String) : WebViewClient() {
    private var seeded = false
    override fun onPageFinished(view: WebView, url: String?) {
        if (seeded) return
        seeded = true
        view.evaluateJavascript(seedScript) {
            view.reload()
        }
    }
}

private class KaraokeWebChromeClient(private val trustedOrigin: String) : WebChromeClient() {
    override fun onPermissionRequest(request: PermissionRequest) {
        val wantsAudio = request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
        val originOk = request.origin?.toString()?.trimEnd('/') == trustedOrigin
        if (wantsAudio && originOk) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else {
            Log.w(TAG, "Denying WebView permission request origin=${request.origin} resources=${request.resources.joinToString()}")
            request.deny()
        }
    }

    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        Log.d(TAG, "[web] ${message.messageLevel()} ${message.message()} @${message.sourceId()}:${message.lineNumber()}")
        return true
    }
}

private val seedJson = Json { encodeDefaults = false; ignoreUnknownKeys = true }

/**
 * Builds the document-start script that writes the web `StoredSession` shape into
 * localStorage. Top-level keys are camelCase (accessToken/walletAttachments/storedAt); nested
 * objects are the raw snake_case API sub-trees, taken directly from the encoded
 * SessionExchangeResponse (no lossy per-field reconstruction).
 */
private fun buildSeedScript(session: SessionExchangeResponse): String {
    val encoded = seedJson.encodeToJsonElement(SessionExchangeResponse.serializer(), session).jsonObject
    val stored = JsonObject(
        mapOf(
            "accessToken" to (encoded["access_token"] ?: JsonNull),
            "user" to (encoded["user"] ?: JsonNull),
            "profile" to (encoded["profile"] ?: JsonNull),
            "onboarding" to (encoded["onboarding"] ?: JsonNull),
            "walletAttachments" to (encoded["wallet_attachments"] ?: JsonArray(emptyList())),
            "storedAt" to JsonPrimitive(Instant.now().toString()),
        ),
    )
    val quoted = JSONObject.quote(stored.toString())
    return "try { window.localStorage.setItem('pirate_session', $quoted); } catch (e) {}"
}

/** Origin (scheme://authority, no trailing slash) of the configured web app. */
private fun buildWebOrigin(): String {
    val uri = Uri.parse(BuildConfig.WEB_BASE_URL.trim())
    return "${uri.scheme}://${uri.authority}"
}
