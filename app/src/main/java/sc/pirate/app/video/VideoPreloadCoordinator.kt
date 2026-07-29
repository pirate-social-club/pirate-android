package sc.pirate.app.video

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl

/**
 * Prepares pages the viewer has not reached yet.
 *
 * The player pool already warms the immediate next page, but a pool is bounded by decoder
 * instances, which are a hard per-device limit. [DefaultPreloadManager] carries preparation
 * further down the feed without dedicating a player to each page, which is what makes a fast run
 * of swipes stay smooth rather than only the first one.
 *
 * Players are built through the manager's own builder so they share its playback looper —
 * preloaded periods are only reusable by a player on that looper. Everything here is
 * best-effort: [mediaSourceFor] returning null simply means the caller falls back to preparing
 * the URL directly, so a preload failure can never cost playback.
 */
@OptIn(UnstableApi::class)
class VideoPreloadCoordinator(
    context: Context,
    mediaSourceFactory: MediaSource.Factory,
) {
    private val appContext = context.applicationContext
    private var currentIndex = 0
    private var released = false

    /** Page index by media id, so the status control can answer in feed terms. */
    private val indexByMediaId = mutableMapOf<String, Int>()
    private val mediaItemsByUrl = mutableMapOf<String, MediaItem>()

    private val statusControl = TargetPreloadStatusControl<Int> { rankingData ->
        when (videoPreloadDepthFor(currentIndex = currentIndex, itemIndex = rankingData)) {
            VideoPreloadDepth.LOADED -> DefaultPreloadManager.Status(
                DefaultPreloadManager.Status.STAGE_LOADED_FOR_DURATION_MS,
                VIDEO_PRELOAD_NEXT_DURATION_MS,
            )
            VideoPreloadDepth.TRACKS_SELECTED ->
                DefaultPreloadManager.Status(DefaultPreloadManager.Status.STAGE_TRACKS_SELECTED)
            VideoPreloadDepth.SOURCE_PREPARED ->
                DefaultPreloadManager.Status(DefaultPreloadManager.Status.STAGE_SOURCE_PREPARED)
            // Null is the manager's own "do not preload this" answer.
            VideoPreloadDepth.NONE -> null
        }
    }

    private val builder = DefaultPreloadManager.Builder(appContext, statusControl)
        .setMediaSourceFactory(mediaSourceFactory)

    private val manager: DefaultPreloadManager = builder.build()

    /**
     * A player on the manager's playback looper. Built here rather than by the pool so that
     * preloaded periods can actually be handed to it.
     */
    @MainThread
    fun createPlayer(): ExoPlayer =
        builder.buildExoPlayer(ExoPlayer.Builder(appContext))
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

    /**
     * Replaces the known feed. Called on every page append; re-adding an existing url is harmless
     * and keeps ranking data correct after the list grows.
     */
    @MainThread
    fun setItems(urls: List<String>) {
        if (released) return
        urls.forEachIndexed { index, url ->
            val mediaItem = mediaItemsByUrl.getOrPut(url) { MediaItem.fromUri(url) }
            val known = indexByMediaId[mediaItem.mediaId]
            indexByMediaId[mediaItem.mediaId] = index
            if (known == null) manager.add(mediaItem, index)
        }
        manager.invalidate()
    }

    /** Moves the preload window. Cheap enough to call on every settle. */
    @MainThread
    fun setCurrentIndex(index: Int) {
        if (released || currentIndex == index) return
        currentIndex = index
        manager.setCurrentPlayingIndex(index)
        manager.invalidate()
    }

    /**
     * The preloaded source for [url], or null when the manager has nothing for it. Null is not an
     * error: the caller prepares the URL directly and the only cost is a cold start.
     */
    @MainThread
    fun mediaSourceFor(url: String): MediaSource? {
        if (released) return null
        val mediaItem = mediaItemsByUrl[url] ?: return null
        return runCatching { manager.getMediaSource(mediaItem) }.getOrNull()
    }

    @MainThread
    fun release() {
        if (released) return
        released = true
        manager.release()
        indexByMediaId.clear()
        mediaItemsByUrl.clear()
    }
}
