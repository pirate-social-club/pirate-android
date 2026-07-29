package sc.pirate.app.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import sc.pirate.app.api.model.SongKaraokeLine

class KaraokeLyricsTest {
    @Test
    fun activeKaraokeLineIndex_returnsLineContainingPosition() {
        assertEquals(1, activeKaraokeLineIndex(testLines(), 1_250))
    }

    @Test
    fun activeKaraokeLineIndex_holdsPreviousLineBetweenTimedLines() {
        assertEquals(0, activeKaraokeLineIndex(testLines(), 700))
    }

    @Test
    fun activeKaraokeLineIndex_returnsNullForEmptyLines() {
        assertNull(activeKaraokeLineIndex(emptyList(), 100))
    }

    private fun testLines(): List<SongKaraokeLine> = listOf(
        SongKaraokeLine(id = "l0", index = 0, kind = "lyric", text = "first", startMs = 0, endMs = 500),
        SongKaraokeLine(id = "l1", index = 1, kind = "lyric", text = "second", startMs = 1_000, endMs = 1_500),
    )
}
