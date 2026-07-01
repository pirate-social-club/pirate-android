package sc.pirate.app.karaoke

import sc.pirate.app.api.model.KaraokeSession

class KaraokeSessionController(
    private val socketClient: KaraokeWebSocketClient = KaraokeWebSocketClient(),
    private val sequence: KaraokeSequenceCounter = KaraokeSequenceCounter(),
) {
    private var session: KaraokeSession? = null
    private var postId: String? = null
    private var connection: KaraokeSocketConnection? = null
    private var started = false
    private var closed = false

    fun attach(session: KaraokeSession, postId: String, listener: KaraokeSocketListener = NoopKaraokeSocketListener): KaraokeSessionPhase {
        require(!closed) { "Karaoke session controller is closed" }
        this.session = session
        this.postId = postId
        connection = socketClient.connect(session, listener)
        return KaraokeSessionPhase.Connecting
    }

    fun start(startedAtAudioMs: Long = 0): Boolean {
        val currentSession = session ?: return false
        val currentPostId = postId ?: return false
        if (started || closed) return false
        started = true
        return connection?.sendText(
            sequence.startEvent(
                sessionId = currentSession.id,
                attemptId = currentSession.attempt,
                postId = currentPostId,
                startedAtAudioMs = startedAtAudioMs,
            ),
        ) == true
    }

    fun playbackSync(audioTimeMs: Long, playing: Boolean): Boolean {
        val currentSession = session ?: return false
        if (!started || closed) return false
        return connection?.sendText(
            sequence.playbackSyncEvent(
                sessionId = currentSession.id,
                attemptId = currentSession.attempt,
                audioTimeMs = audioTimeMs,
                playing = playing,
            ),
        ) == true
    }

    fun sendAudioChunk(
        chunkId: Long,
        songStartMs: Long,
        songEndMs: Long,
        pcm16MonoLittleEndian: ByteArray,
    ): Boolean {
        if (!started || closed) return false
        val frame = sequence.nextAudioFrame(
            chunkId = chunkId,
            songStartMs = songStartMs,
            songEndMs = songEndMs,
            pcm16MonoLittleEndian = pcm16MonoLittleEndian,
        )
        return connection?.sendBinary(encodeKaraokeAudioFrame(frame)) == true
    }

    fun finish(audioTimeMs: Long): Boolean {
        val currentSession = session ?: return false
        if (!started || closed) return false
        closed = true
        val sent = connection?.sendText(
            sequence.finishEvent(
                sessionId = currentSession.id,
                attemptId = currentSession.attempt,
                audioTimeMs = audioTimeMs,
            ),
        ) == true
        connection?.close()
        return sent
    }

    fun abort(code: String): Boolean {
        val currentSession = session ?: return false
        if (closed) return false
        closed = true
        val sent = connection?.sendText(
            sequence.abortEvent(
                sessionId = currentSession.id,
                attemptId = currentSession.attempt,
                code = code,
            ),
        ) == true
        connection?.close()
        return sent
    }
}

enum class KaraokeSessionPhase {
    Connecting,
    Live,
    Closed,
}

private object NoopKaraokeSocketListener : KaraokeSocketListener
