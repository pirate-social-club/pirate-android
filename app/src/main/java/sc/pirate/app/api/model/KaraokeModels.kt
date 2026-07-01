package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SongKaraokePayload(
    @SerialName("object") val contractObject: String? = null,
    val id: String,
    val title: String,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("artwork_src") val artworkSrc: String? = null,
    @SerialName("instrumental_audio_url") val instrumentalAudioUrl: String,
    @SerialName("karaoke_lines") val karaokeLines: List<SongKaraokeLine> = emptyList(),
    @SerialName("raw_lines") val rawLines: List<String> = emptyList(),
)

@Serializable
data class SongKaraokeLine(
    val id: String,
    val index: Int,
    val kind: String,
    val text: String,
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    val words: List<SongKaraokeWord> = emptyList(),
)

@Serializable
data class SongKaraokeWord(
    val text: String,
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    val confidence: Double? = null,
)

@Serializable
data class KaraokeSessionCreateRequest(
    @SerialName("client") val client: String = "android_native",
)

@Serializable
data class KaraokeSession(
    val id: String,
    @SerialName("object") val contractObject: String,
    val attempt: String,
    @SerialName("protocol_version") val protocolVersion: Int,
    @SerialName("websocket_url") val websocketUrl: String,
    @SerialName("token_expires_at") val tokenExpiresAt: Long,
    @SerialName("session_expires_at") val sessionExpiresAt: Long,
    @SerialName("scoring_policy") val scoringPolicy: KaraokeScoringPolicy,
)

@Serializable
data class KaraokeScoringPolicy(
    val kind: String,
    val provider: String? = null,
    val model: String? = null,
    val retention: String? = null,
    @SerialName("voice_coach_enabled") val voiceCoachEnabled: Boolean? = null,
)
