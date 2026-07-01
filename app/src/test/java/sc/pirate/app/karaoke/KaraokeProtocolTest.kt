package sc.pirate.app.karaoke

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KaraokeProtocolTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun eventsUseSharedMonotonicSequenceWithAudioFrames() {
        val sequence = KaraokeSequenceCounter()
        val start = json.parseToJsonElement(
            sequence.startEvent(
                sessionId = "ks_1",
                attemptId = "att_1",
                postId = "post_1",
                startedAtAudioMs = 0,
            ),
        ).jsonObject
        val audio = sequence.nextAudioFrame(
            chunkId = 1,
            songStartMs = 100,
            songEndMs = 200,
            pcm16MonoLittleEndian = byteArrayOf(0, 0),
        )
        val sync = json.parseToJsonElement(
            sequence.playbackSyncEvent(
                sessionId = "ks_1",
                attemptId = "att_1",
                audioTimeMs = 200,
                playing = true,
            ),
        ).jsonObject

        assertEquals(1, start["sequence"]!!.jsonPrimitive.content.toLong())
        assertEquals(2, audio.sequence)
        assertEquals(3, sync["sequence"]!!.jsonPrimitive.content.toLong())
        assertEquals("start", start["type"]!!.jsonPrimitive.content)
        assertEquals("playback_sync", sync["type"]!!.jsonPrimitive.content)
        assertEquals(true, sync["playing"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun startEventUsesKaraokeRuntimeFieldNames() {
        val event = json.parseToJsonElement(
            KaraokeSequenceCounter().startEvent(
                sessionId = "ks_1",
                attemptId = "att_1",
                postId = "post_1",
                startedAtAudioMs = 42,
            ),
        ).jsonObject

        assertArrayEquals(
            arrayOf("protocolVersion", "sessionId", "attemptId", "sequence", "type", "postId", "startedAtAudioMs"),
            event.keys.toTypedArray(),
        )
        assertEquals(1, event["protocolVersion"]!!.jsonPrimitive.content.toInt())
        assertEquals("ks_1", event["sessionId"]!!.jsonPrimitive.content)
        assertEquals("att_1", event["attemptId"]!!.jsonPrimitive.content)
        assertEquals("post_1", event["postId"]!!.jsonPrimitive.content)
        assertEquals(42, event["startedAtAudioMs"]!!.jsonPrimitive.content.toLong())
    }
}
