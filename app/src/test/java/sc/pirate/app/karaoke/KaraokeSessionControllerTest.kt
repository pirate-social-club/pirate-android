package sc.pirate.app.karaoke

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.KaraokeScoringPolicy
import sc.pirate.app.api.model.KaraokeSession

class KaraokeSessionControllerTest {
    @Test
    fun sendCapturedChunk_mapsCaptureClockToSongWindowAndSendsBinaryFrame() {
        val socket = FakeSocketClient()
        val controller = KaraokeSessionController(socketClient = socket)
        controller.attach(session = testSession(), postId = "post_1")

        assertTrue(controller.start(startedAtAudioMs = 5_000))
        controller.updateCaptureAnchor(KaraokeCaptureAnchor(captureMs = 1_000, songMs = 5_000))
        assertTrue(
            controller.sendCapturedChunk(
                KaraokeCapturedChunk(
                    chunkId = 7,
                    captureStartMs = 1_100,
                    pcm16MonoLittleEndian = byteArrayOf(0x34, 0x12, 0x78, 0x56),
                ),
            ),
        )

        assertEquals(1, socket.sentText.size)
        assertEquals(1, socket.sentBinary.size)
        assertArrayEquals(
            encodeKaraokeAudioFrame(
                KaraokeAudioFrame(
                    sequence = 2,
                    chunkId = 7,
                    sampleRate = 16_000,
                    songStartMs = 5_100,
                    songEndMs = 5_100 + karaokePcmDurationMs(4),
                    pcm16MonoLittleEndian = byteArrayOf(0x34, 0x12, 0x78, 0x56),
                ),
            ),
            socket.sentBinary.single(),
        )
    }

    @Test
    fun reset_allowsControllerReuseAfterFinish() {
        val socket = FakeSocketClient()
        val controller = KaraokeSessionController(socketClient = socket)
        controller.attach(session = testSession(), postId = "post_1")

        assertTrue(controller.start(startedAtAudioMs = 0))
        assertTrue(controller.finish(audioTimeMs = 1_000))

        controller.reset()
        controller.attach(session = testSession(id = "ks_2", attempt = "att_2"), postId = "post_1")

        assertTrue(controller.start(startedAtAudioMs = 0))
        assertEquals(3, socket.sentText.size)
        assertEquals(2, socket.closeCount)
    }

    private fun testSession(id: String = "ks_1", attempt: String = "att_1"): KaraokeSession =
        KaraokeSession(
            id = id,
            contractObject = "karaoke_session",
            attempt = attempt,
            protocolVersion = 1,
            websocketUrl = "wss://api.pirate.sc/karaoke/sessions/ks_1/websocket?token=redacted",
            tokenExpiresAt = 4_102_444_800,
            sessionExpiresAt = 4_102_448_400,
            scoringPolicy = KaraokeScoringPolicy(kind = "disabled"),
        )
}

private class FakeSocketClient : KaraokeSocketClient {
    val sentText = mutableListOf<String>()
    val sentBinary = mutableListOf<ByteArray>()
    var closeCount = 0

    override fun connect(
        session: KaraokeSession,
        listener: KaraokeSocketListener,
    ): KaraokeSocketConnection =
        KaraokeSocketConnection(
            sendTextBlock = { message ->
                sentText += message
                true
            },
            sendBinaryBlock = { bytes ->
                sentBinary += bytes
                true
            },
            closeBlock = { closeCount += 1 },
        )
}
