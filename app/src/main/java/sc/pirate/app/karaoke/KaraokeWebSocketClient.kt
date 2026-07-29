package sc.pirate.app.karaoke

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import sc.pirate.app.BuildConfig
import sc.pirate.app.api.model.KaraokeSession
import java.util.concurrent.TimeUnit

private const val NORMAL_CLOSURE = 1000

interface KaraokeSocketClient {
    fun connect(
        session: KaraokeSession,
        listener: KaraokeSocketListener,
    ): KaraokeSocketConnection
}

class KaraokeWebSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build(),
    private val origin: String = BuildConfig.KARAOKE_WEBSOCKET_ORIGIN,
) : KaraokeSocketClient {
    override fun connect(
        session: KaraokeSession,
        listener: KaraokeSocketListener,
    ): KaraokeSocketConnection {
        val request = Request.Builder()
            .url(session.websocketUrl)
            .header("Origin", origin)
            .build()
        val socket = client.newWebSocket(request, ListenerAdapter(listener))
        return KaraokeSocketConnection(socket)
    }
}

class KaraokeSocketConnection internal constructor(
    private val sendTextBlock: (String) -> Boolean,
    private val sendBinaryBlock: (ByteArray) -> Boolean,
    private val closeBlock: () -> Unit,
) {
    internal constructor(socket: WebSocket) : this(
        sendTextBlock = socket::send,
        sendBinaryBlock = { bytes -> socket.send(ByteString.of(*bytes)) },
        closeBlock = { socket.close(NORMAL_CLOSURE, "closed") },
    )

    fun sendText(message: String): Boolean = sendTextBlock(message)

    fun sendBinary(bytes: ByteArray): Boolean = sendBinaryBlock(bytes)

    fun close() {
        closeBlock()
    }
}

interface KaraokeSocketListener {
    fun onOpen() = Unit
    fun onText(message: String) = Unit
    fun onBinary(bytes: ByteArray) = Unit
    fun onClosed() = Unit
    fun onFailure(message: String) = Unit
}

private class ListenerAdapter(
    private val delegate: KaraokeSocketListener,
) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        delegate.onOpen()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        delegate.onText(text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        delegate.onBinary(bytes.toByteArray())
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        delegate.onClosed()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        delegate.onFailure(sanitizeWebSocketFailure(t.message, response?.code))
    }
}

private fun sanitizeWebSocketFailure(message: String?, status: Int?): String {
    val prefix = status?.let { "WebSocket failed with status $it" } ?: "WebSocket failed"
    val detail = message
        ?.replace(Regex("""token=[^&\s]+"""), "token=[redacted]")
        ?.takeIf { it.isNotBlank() }
    return detail?.let { "$prefix: $it" } ?: prefix
}
