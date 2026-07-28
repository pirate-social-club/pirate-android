package sc.pirate.app.video

import android.content.Context
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource

/**
 * A small, explicitly-owned set of [ExoPlayer] instances shared by a video pager.
 *
 * The feed's old behaviour — one player constructed inside composition per item — put player
 * construction, surface allocation and the first network read all on the frame an item scrolled
 * in. A pager only ever needs the page being watched plus the one about to be watched, so two
 * players are enough: one plays, one is prepared and paused so its first frame is already
 * decoded when the pager settles on it.
 *
 * Every method is main-thread only; ExoPlayer is not thread-safe and the pager drives this
 * entirely from composition callbacks. Lease ordering lives in [VideoLeaseTable].
 */
class VideoPlayerPool(
    context: Context,
    private val mediaSourceFactory: MediaSource.Factory,
    capacity: Int = DEFAULT_CAPACITY,
) {
    private val appContext = context.applicationContext
    private val leases = VideoLeaseTable(capacity)
    private val playersByKey = mutableMapOf<String, ExoPlayer>()
    private var released = false

    /**
     * Snapshot state, not a plain field: pages read this during composition to decide whether to
     * attach a surface, so binding a player has to recompose the page that just gained one.
     */
    var heldKeys: Set<String> by mutableStateOf(emptySet())
        private set

    /**
     * The player bound to [key], preparing [url] on it if this is a new binding. The returned
     * player is never started here: the caller decides which single page is audible via
     * [playOnly], so a warmed page stays paused on its first frame.
     */
    @MainThread
    fun obtain(key: String, url: String): ExoPlayer {
        check(!released) { "VideoPlayerPool used after release()" }
        if (leases.touch(key)) return playersByKey.getValue(key)

        val player = when (val admission = leases.admit(key)) {
            is VideoLeaseTable.Admission.Reuse -> {
                // Reuse the coldest player rather than constructing one: construction is the cost
                // this pool exists to keep off the scroll frame.
                val recycled = playersByKey.remove(admission.evictedKey)
                    ?: error("Lease table held ${admission.evictedKey} with no player")
                recycled.also { it.stop() }
            }
            VideoLeaseTable.Admission.Create -> createPlayer()
        }

        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        playersByKey[key] = player
        publishHeldKeys()
        return player
    }

    /** The player bound to [key], or null once the pool has evicted that page. */
    @MainThread
    fun playerFor(key: String): ExoPlayer? = playersByKey[key]

    /**
     * Starts the page bound to [key] and pauses every other held player, so exactly one video is
     * ever audible. A key with no lease pauses everything, which is what a pager wants while it
     * is between pages.
     */
    @MainThread
    fun playOnly(key: String?) {
        check(!released) { "VideoPlayerPool used after release()" }
        playersByKey.forEach { (heldKey, player) ->
            if (heldKey == key) player.play() else player.pause()
        }
    }

    @MainThread
    fun pauseAll() {
        if (released) return
        playersByKey.values.forEach { it.pause() }
    }

    @MainThread
    fun releaseAll() {
        if (released) return
        released = true
        playersByKey.values.forEach { it.release() }
        playersByKey.clear()
        leases.clear()
        publishHeldKeys()
    }

    private fun publishHeldKeys() {
        heldKeys = playersByKey.keys.toSet()
    }

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .also { player ->
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.playWhenReady = false
            }

    companion object {
        /**
         * One playing page plus one warmed neighbour. Raising this costs decoder instances, which
         * are a hard per-device limit rather than a memory tradeoff — measure before changing it.
         */
        const val DEFAULT_CAPACITY = 2
    }
}
