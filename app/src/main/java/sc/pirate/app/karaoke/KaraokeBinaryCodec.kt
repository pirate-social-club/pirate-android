package sc.pirate.app.karaoke

import java.nio.ByteBuffer
import java.nio.ByteOrder

const val KARAOKE_BINARY_PROTOCOL_VERSION: Int = 1
const val KARAOKE_AUDIO_SAMPLE_RATE_HZ: Int = 16_000

private const val MAGIC_K: Byte = 0x4B
private const val MAGIC_A: Byte = 0x41
private const val MAGIC_R: Byte = 0x52
private const val KARAOKE_AUDIO_HEADER_BYTES: Int = 28
private const val KARAOKE_AUDIO_MAX_FRAME_BYTES: Int = 200_000

data class KaraokeAudioFrame(
    val sequence: Long,
    val chunkId: Long,
    val sampleRate: Int,
    val songStartMs: Long,
    val songEndMs: Long,
    val pcm16MonoLittleEndian: ByteArray,
) {
    init {
        require(sequence in 0..UInt.MAX_VALUE.toLong()) { "sequence must fit uint32" }
        require(chunkId in 1..UInt.MAX_VALUE.toLong()) { "chunkId must fit uint32 and be positive" }
        require(sampleRate == KARAOKE_AUDIO_SAMPLE_RATE_HZ) { "sampleRate must be 16000" }
        require(songStartMs in 0..UInt.MAX_VALUE.toLong()) { "songStartMs must fit uint32" }
        require(songEndMs in songStartMs..UInt.MAX_VALUE.toLong()) { "songEndMs must fit uint32 and be >= songStartMs" }
        require(pcm16MonoLittleEndian.size % 2 == 0) { "PCM16 payload must have an even byte length" }
        require(pcm16MonoLittleEndian.size <= KARAOKE_AUDIO_MAX_FRAME_BYTES) { "PCM16 payload too large" }
    }
}

fun encodeKaraokeAudioFrame(frame: KaraokeAudioFrame): ByteArray {
    val output = ByteArray(KARAOKE_AUDIO_HEADER_BYTES + frame.pcm16MonoLittleEndian.size)
    val header = ByteBuffer.wrap(output)
        .order(ByteOrder.BIG_ENDIAN)

    header.put(MAGIC_K)
    header.put(MAGIC_A)
    header.put(MAGIC_R)
    header.put(MAGIC_A)
    header.put(KARAOKE_BINARY_PROTOCOL_VERSION.toByte())
    header.put(0)
    header.putShort(KARAOKE_AUDIO_HEADER_BYTES.toShort())
    header.putInt(frame.sequence.toInt())
    header.putInt(frame.chunkId.toInt())
    header.putInt(frame.sampleRate)
    header.putInt(frame.songStartMs.toInt())
    header.putInt(frame.songEndMs.toInt())
    frame.pcm16MonoLittleEndian.copyInto(output, destinationOffset = KARAOKE_AUDIO_HEADER_BYTES)
    return output
}
