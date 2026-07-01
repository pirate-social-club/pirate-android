package sc.pirate.app.karaoke

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KaraokeBinaryCodecTest {
    @Test
    fun encodeKaraokeAudioFrame_writesProtocolHeaderAndPcmPayload() {
        val encoded = encodeKaraokeAudioFrame(
            KaraokeAudioFrame(
                sequence = 2,
                chunkId = 3,
                sampleRate = 16_000,
                songStartMs = 120,
                songEndMs = 220,
                pcm16MonoLittleEndian = byteArrayOf(0x34, 0x12, 0x78, 0x56),
            ),
        )

        assertEquals(32, encoded.size)
        assertArrayEquals(
            byteArrayOf(
                0x4B, 0x41, 0x52, 0x41,
                0x01, 0x00, 0x00, 0x1C,
                0x00, 0x00, 0x00, 0x02,
                0x00, 0x00, 0x00, 0x03,
                0x00, 0x00, 0x3E, 0x80.toByte(),
                0x00, 0x00, 0x00, 0x78,
                0x00, 0x00, 0x00, 0xDC.toByte(),
                0x34, 0x12, 0x78, 0x56,
            ),
            encoded,
        )
    }

    @Test
    fun encodeKaraokeAudioFrame_rejectsInvalidFrameInvariants() {
        assertThrows(IllegalArgumentException::class.java) {
            KaraokeAudioFrame(
                sequence = 1,
                chunkId = 0,
                sampleRate = 16_000,
                songStartMs = 0,
                songEndMs = 100,
                pcm16MonoLittleEndian = byteArrayOf(0),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            KaraokeAudioFrame(
                sequence = 1,
                chunkId = 1,
                sampleRate = 48_000,
                songStartMs = 0,
                songEndMs = 100,
                pcm16MonoLittleEndian = byteArrayOf(0, 0),
            )
        }
    }
}
