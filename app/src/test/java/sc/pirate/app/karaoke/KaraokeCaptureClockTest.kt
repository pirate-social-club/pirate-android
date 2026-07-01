package sc.pirate.app.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KaraokeCaptureClockTest {
    @Test
    fun mapCaptureWindow_mapsMicClockToSongClockFromAnchor() {
        val clock = KaraokeCaptureClock(
            KaraokeCaptureAnchor(
                captureMs = 1_000,
                songMs = 5_000,
                playbackRate = 1.0,
            ),
        )

        assertEquals(
            KaraokeSongWindow(songStartMs = 5_250, songEndMs = 5_350),
            clock.mapCaptureWindow(captureStartMs = 1_250, captureDurationMs = 100),
        )
    }

    @Test
    fun updateAnchor_startsNewEpochAfterSeekOrResume() {
        val clock = KaraokeCaptureClock(KaraokeCaptureAnchor(captureMs = 0, songMs = 0))

        clock.updateAnchor(KaraokeCaptureAnchor(captureMs = 10_000, songMs = 45_000))

        assertEquals(
            KaraokeSongWindow(songStartMs = 45_100, songEndMs = 45_200),
            clock.mapCaptureWindow(captureStartMs = 10_100, captureDurationMs = 100),
        )
    }

    @Test
    fun songPositionAt_mapsCaptureTimeFromAnchor() {
        val clock = KaraokeCaptureClock(KaraokeCaptureAnchor(captureMs = 10_000, songMs = 45_000))

        assertEquals(45_250, clock.songPositionAt(10_250))
    }

    @Test
    fun mapCaptureWindow_appliesPlaybackRate() {
        val clock = KaraokeCaptureClock(
            KaraokeCaptureAnchor(
                captureMs = 2_000,
                songMs = 8_000,
                playbackRate = 0.5,
            ),
        )

        assertEquals(
            KaraokeSongWindow(songStartMs = 8_050, songEndMs = 8_100),
            clock.mapCaptureWindow(captureStartMs = 2_100, captureDurationMs = 100),
        )
    }

    @Test
    fun karaokePcmDurationMs_usesPcm16MonoSampleCount() {
        assertEquals(100, karaokePcmDurationMs(byteCount = 3_200))
        assertEquals(50, karaokePcmDurationMs(byteCount = 1_600))
    }

    @Test
    fun karaokePcmDurationMs_rejectsOddByteCounts() {
        assertThrows(IllegalArgumentException::class.java) {
            karaokePcmDurationMs(byteCount = 1)
        }
    }
}
