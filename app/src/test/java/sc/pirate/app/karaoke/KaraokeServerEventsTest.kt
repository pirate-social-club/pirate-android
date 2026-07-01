package sc.pirate.app.karaoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KaraokeServerEventsTest {
    @Test
    fun parseKaraokeServerEvent_acceptsSummaryEvent() {
        val event = parseKaraokeServerEvent(
            """
            {
              "protocolVersion": 1,
              "sessionId": "ks_1",
              "attemptId": "att_1",
              "sequence": 4,
              "eventId": "evt_4",
              "type": "summary",
              "summary": {
                "finalScore": 0.85,
                "scoredLineCount": 2,
                "lineCount": 3
              }
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        assertEquals("summary", event?.type)
        assertEquals(0.85, event?.summary?.finalScoreValue() ?: 0.0, 0.001)
        assertEquals(2, event?.summary?.scoredLineCountValue())
        assertEquals(3, event?.summary?.lineCountValue())
    }

    @Test
    fun parseKaraokeServerEvent_rejectsUnknownTypes() {
        val event = parseKaraokeServerEvent(
            """{"protocolVersion":1,"sequence":1,"eventId":"evt_1","type":"unknown"}""",
        )

        assertNull(event)
    }
}
