package sc.pirate.app.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Whether a song post offers karaoke, as the server decides it.
 *
 * Inferring this from alignment status and timed lyrics — as the post screen does — can disagree
 * with the server on locking, entitlement, age gating and failed generation. The rail asks the
 * server instead.
 */
@Serializable
data class SongKaraokeCapability(
    /** ready | locked | processing | unavailable */
    val status: String,
) {
    val ready: Boolean get() = status == "ready"
}
