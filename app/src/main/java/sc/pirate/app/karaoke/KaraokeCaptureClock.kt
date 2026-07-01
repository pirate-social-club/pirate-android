package sc.pirate.app.karaoke

import kotlin.math.roundToLong

data class KaraokeCaptureAnchor(
    val captureMs: Long,
    val songMs: Long,
    val playbackRate: Double = 1.0,
) {
    init {
        require(captureMs >= 0) { "captureMs must be non-negative" }
        require(songMs >= 0) { "songMs must be non-negative" }
        require(playbackRate > 0.0) { "playbackRate must be positive" }
    }
}

data class KaraokeSongWindow(
    val songStartMs: Long,
    val songEndMs: Long,
) {
    init {
        require(songStartMs >= 0) { "songStartMs must be non-negative" }
        require(songEndMs >= songStartMs) { "songEndMs must be >= songStartMs" }
    }
}

class KaraokeCaptureClock(
    initialAnchor: KaraokeCaptureAnchor,
) {
    private var anchor = initialAnchor

    fun updateAnchor(next: KaraokeCaptureAnchor) {
        anchor = next
    }

    fun mapCaptureWindow(captureStartMs: Long, captureDurationMs: Long): KaraokeSongWindow {
        require(captureStartMs >= 0) { "captureStartMs must be non-negative" }
        require(captureDurationMs >= 0) { "captureDurationMs must be non-negative" }
        val start = captureToSongMs(captureStartMs)
        val end = captureToSongMs(captureStartMs + captureDurationMs)
        return KaraokeSongWindow(
            songStartMs = start.coerceAtLeast(0),
            songEndMs = end.coerceAtLeast(start.coerceAtLeast(0)),
        )
    }

    private fun captureToSongMs(captureMs: Long): Long {
        val elapsedCaptureMs = captureMs - anchor.captureMs
        return (anchor.songMs + elapsedCaptureMs * anchor.playbackRate).roundToLong()
    }
}

fun karaokePcmDurationMs(byteCount: Int, sampleRate: Int = KARAOKE_AUDIO_SAMPLE_RATE_HZ): Long {
    require(byteCount >= 0) { "byteCount must be non-negative" }
    require(byteCount % 2 == 0) { "PCM16 byte count must be even" }
    require(sampleRate > 0) { "sampleRate must be positive" }
    val samples = byteCount / 2
    return (samples * 1000L) / sampleRate
}
