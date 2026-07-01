package sc.pirate.app.karaoke

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val KARAOKE_TRANSPORT_PROTOCOL_VERSION: Int = 1

private val karaokeProtocolJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

class KaraokeSequenceCounter(initialValue: Long = 0) {
    private var nextValue = initialValue

    fun next(): Long {
        require(nextValue < UInt.MAX_VALUE.toLong()) { "Karaoke sequence exhausted" }
        nextValue += 1
        return nextValue
    }

    fun current(): Long = nextValue

    fun reset() {
        nextValue = 0
    }
}

data class KaraokeClientEnvelope(
    val protocolVersion: Int = KARAOKE_TRANSPORT_PROTOCOL_VERSION,
    val sessionId: String,
    val attemptId: String,
    val sequence: Long,
    val type: String,
    val fields: Map<String, KaraokeJsonValue> = emptyMap(),
)

sealed interface KaraokeJsonValue {
    data class StringValue(val value: String) : KaraokeJsonValue
    data class LongValue(val value: Long) : KaraokeJsonValue
    data class BooleanValue(val value: Boolean) : KaraokeJsonValue
}

fun KaraokeSequenceCounter.nextAudioFrame(
    chunkId: Long,
    songStartMs: Long,
    songEndMs: Long,
    pcm16MonoLittleEndian: ByteArray,
): KaraokeAudioFrame =
    KaraokeAudioFrame(
        sequence = next(),
        chunkId = chunkId,
        sampleRate = KARAOKE_AUDIO_SAMPLE_RATE_HZ,
        songStartMs = songStartMs,
        songEndMs = songEndMs,
        pcm16MonoLittleEndian = pcm16MonoLittleEndian,
    )

fun encodeClientEvent(envelope: KaraokeClientEnvelope): String {
    require(envelope.protocolVersion == KARAOKE_TRANSPORT_PROTOCOL_VERSION) { "Unsupported karaoke protocol version" }
    require(envelope.sessionId.isNotBlank()) { "sessionId is required" }
    require(envelope.attemptId.isNotBlank()) { "attemptId is required" }
    require(envelope.sequence in 1..UInt.MAX_VALUE.toLong()) { "sequence must fit uint32 and be positive" }
    require(envelope.type.isNotBlank()) { "type is required" }

    val payload = MutableMapPayload(
        protocolVersion = envelope.protocolVersion,
        sessionId = envelope.sessionId,
        attemptId = envelope.attemptId,
        sequence = envelope.sequence,
        type = envelope.type,
    )
    envelope.fields.forEach { (key, value) ->
        when (value) {
            is KaraokeJsonValue.BooleanValue -> payload.booleans[key] = value.value
            is KaraokeJsonValue.LongValue -> payload.longs[key] = value.value
            is KaraokeJsonValue.StringValue -> payload.strings[key] = value.value
        }
    }
    return payload.encode()
}

fun KaraokeSequenceCounter.startEvent(
    sessionId: String,
    attemptId: String,
    postId: String,
    startedAtAudioMs: Long,
): String = encodeClientEvent(
    KaraokeClientEnvelope(
        sessionId = sessionId,
        attemptId = attemptId,
        sequence = next(),
        type = "start",
        fields = mapOf(
            "postId" to KaraokeJsonValue.StringValue(postId),
            "startedAtAudioMs" to KaraokeJsonValue.LongValue(startedAtAudioMs),
        ),
    ),
)

fun KaraokeSequenceCounter.playbackSyncEvent(
    sessionId: String,
    attemptId: String,
    audioTimeMs: Long,
    playing: Boolean,
): String = encodeClientEvent(
    KaraokeClientEnvelope(
        sessionId = sessionId,
        attemptId = attemptId,
        sequence = next(),
        type = "playback_sync",
        fields = mapOf(
            "audioTimeMs" to KaraokeJsonValue.LongValue(audioTimeMs),
            "playing" to KaraokeJsonValue.BooleanValue(playing),
        ),
    ),
)

fun KaraokeSequenceCounter.finishEvent(
    sessionId: String,
    attemptId: String,
    audioTimeMs: Long,
): String = encodeClientEvent(
    KaraokeClientEnvelope(
        sessionId = sessionId,
        attemptId = attemptId,
        sequence = next(),
        type = "finish",
        fields = mapOf("audioTimeMs" to KaraokeJsonValue.LongValue(audioTimeMs)),
    ),
)

fun KaraokeSequenceCounter.abortEvent(
    sessionId: String,
    attemptId: String,
    code: String,
): String = encodeClientEvent(
    KaraokeClientEnvelope(
        sessionId = sessionId,
        attemptId = attemptId,
        sequence = next(),
        type = "abort",
        fields = mapOf("code" to KaraokeJsonValue.StringValue(code)),
    ),
)

@Serializable
private data class MutableMapPayload(
    val protocolVersion: Int,
    val sessionId: String,
    val attemptId: String,
    val sequence: Long,
    val type: String,
    @SerialName("_strings") val strings: MutableMap<String, String> = linkedMapOf(),
    @SerialName("_longs") val longs: MutableMap<String, Long> = linkedMapOf(),
    @SerialName("_booleans") val booleans: MutableMap<String, Boolean> = linkedMapOf(),
) {
    fun encode(): String {
        val flat = linkedMapOf<String, Any>(
            "protocolVersion" to protocolVersion,
            "sessionId" to sessionId,
            "attemptId" to attemptId,
            "sequence" to sequence,
            "type" to type,
        )
        strings.forEach { (key, value) -> flat[key] = value }
        longs.forEach { (key, value) -> flat[key] = value }
        booleans.forEach { (key, value) -> flat[key] = value }
        return encodeFlatPayload(flat)
    }
}

private fun encodeFlatPayload(fields: Map<String, Any>): String {
    val jsonObject = fields.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
        val encodedValue = when (value) {
            is Boolean -> value.toString()
            is Number -> value.toString()
            is String -> karaokeProtocolJson.encodeToString(value)
            else -> error("Unsupported karaoke JSON value")
        }
        "${karaokeProtocolJson.encodeToString(key)}:$encodedValue"
    }
    return jsonObject
}
