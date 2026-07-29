package sc.pirate.app.video

import sc.pirate.app.api.model.DerivativeSource
import sc.pirate.app.api.model.LocalizedPostResponse

/**
 * Resolving a video's Study and Sing actions.
 *
 * Capabilities never live on a video post. A video that uses a song carries a Story Protocol
 * attribution naming the song post, and that song post is where `study_capability` and
 * `karaoke_capability` are decided. So the rail is a two-step lookup: find the reference, then
 * read the referenced post.
 *
 * Split out and pure so the selection and gating rules are testable without a network or a device
 * — the previous attempt read capabilities straight off the video and could never light up.
 */

const val RELATIONSHIP_REFERENCES_SONG = "references_song"
const val CAPABILITY_READY = "ready"
const val AGE_GATE_PROOF_REQUIRED = "proof_required"

/** The song this video references, or null when it is not a derivative of one. */
fun referencedSong(post: LocalizedPostResponse): DerivativeSource? =
    post.derivativeSources.firstOrNull {
        it.relationshipType == RELATIONSHIP_REFERENCES_SONG && !it.sourcePost.isNullOrBlank()
    }

/** What the rail may offer for one video, once its song post has been fetched. */
data class VideoSongCapabilities(
    val songPostId: String,
    val songCommunityId: String,
    val studyReady: Boolean,
    val karaokeReady: Boolean,
) {
    companion object {
        val NONE = VideoSongCapabilities("", "", studyReady = false, karaokeReady = false)
    }
}

/**
 * Reads the resolved song post.
 *
 * An age-gated song offers nothing: web forces both actions unavailable when the viewer still
 * owes proof, and a rail that offered them would send the viewer to a screen that refuses them.
 * Karaoke comes from the server's own capability rather than being inferred from alignment and
 * timed lyrics, which cannot see locking, entitlement or failed generation.
 */
fun resolveVideoSongCapabilities(
    songPostId: String,
    songCommunityId: String,
    songPost: LocalizedPostResponse,
): VideoSongCapabilities {
    val ageBlocked = songPost.ageGateViewerState == AGE_GATE_PROOF_REQUIRED
    return VideoSongCapabilities(
        songPostId = songPostId,
        songCommunityId = songCommunityId.ifBlank { songPost.post.communityId },
        studyReady = !ageBlocked && songPost.studyCapability?.status == CAPABILITY_READY,
        karaokeReady = !ageBlocked && songPost.karaokeCapability?.status == CAPABILITY_READY,
    )
}
