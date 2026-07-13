package sc.pirate.app.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongSummaryTest {
    @Test
    fun formatSongTime_formatsElapsedPlayback() {
        assertEquals("0:00", formatSongTime(0))
        assertEquals("0:09", formatSongTime(9_999))
        assertEquals("2:05", formatSongTime(125_000))
        assertEquals("61:01", formatSongTime(3_661_000))
    }

    @Test
    fun formatSongTime_clampsNegativePlaybackToZero() {
        assertEquals("0:00", formatSongTime(-1))
    }

    @Test
    fun songDurationLabel_hidesUnknownDurations() {
        assertNull(songDurationLabel(null))
        assertNull(songDurationLabel(0))
        assertEquals("3:30", songDurationLabel(210_000))
    }

    @Test
    fun songSeekPosition_mapsTrackBoundsAndMidpoint() {
        assertEquals(
            0L,
            songSeekPosition(pointerX = 5f, width = 105f, durationMs = 200_000, thumbRadiusPx = 5f),
        )
        assertEquals(
            100_000L,
            songSeekPosition(pointerX = 52.5f, width = 105f, durationMs = 200_000, thumbRadiusPx = 5f),
        )
        assertEquals(
            200_000L,
            songSeekPosition(pointerX = 100f, width = 105f, durationMs = 200_000, thumbRadiusPx = 5f),
        )
    }
}
