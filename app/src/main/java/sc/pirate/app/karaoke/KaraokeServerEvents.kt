package sc.pirate.app.karaoke

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

private val karaokeServerEventJson = Json {
    ignoreUnknownKeys = true
}

@Serializable
data class KaraokeServerEvent(
    val protocolVersion: Int,
    val sessionId: String? = null,
    val attemptId: String? = null,
    val sequence: Long,
    val type: String,
    val eventId: String,
    val text: String? = null,
    val words: List<KaraokeServerRecognizedWord> = emptyList(),
    val result: JsonObject? = null,
    val summary: JsonObject? = null,
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class KaraokeServerRecognizedWord(
    val text: String,
    @SerialName("startMs") val startMs: Long? = null,
    @SerialName("endMs") val endMs: Long? = null,
    val confidence: Double? = null,
)

fun parseKaraokeServerEvent(message: String): KaraokeServerEvent? {
    val event = runCatching {
        karaokeServerEventJson.decodeFromString(KaraokeServerEvent.serializer(), message)
    }.getOrNull() ?: return null
    if (event.protocolVersion != KARAOKE_TRANSPORT_PROTOCOL_VERSION) return null
    if (event.eventId.isBlank() || event.sequence < 0) return null
    return when (event.type) {
        "stt_partial" -> event.takeIf { it.text != null }
        "stt_final" -> event.takeIf { it.text != null }
        "line_score" -> event.takeIf { it.result != null }
        "summary" -> event.takeIf { it.summary != null }
        "session_error" -> event.takeIf { !it.code.isNullOrBlank() }
        else -> null
    }
}

fun JsonObject.scoreValue(): Double? =
    this["score"]?.jsonPrimitive?.doubleOrNull

fun JsonObject.finalScoreValue(): Double? =
    this["finalScore"]?.jsonPrimitive?.doubleOrNull

fun JsonObject.lineCountValue(): Int? =
    this["lineCount"]?.jsonPrimitive?.intOrNull

fun JsonObject.scoredLineCountValue(): Int? =
    this["scoredLineCount"]?.jsonPrimitive?.intOrNull
