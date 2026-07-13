package sc.pirate.app.song

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.shared.resolvePublicMediaSrc

data class SongPlaybackState(
    val postId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val error: String? = null,
    val errorNonce: Long = 0,
)

class SongPlaybackController(
    context: Context,
    private val onPlayRequested: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(SongPlaybackState())
    val state: StateFlow<SongPlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressUpdate = object : Runnable {
        override fun run() {
            updateProgress()
            if (player?.isPlaying == true) {
                progressHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    @MainThread
    fun toggle(post: LocalizedPostResponse) {
        val postId = post.post.postId
        val audioUrl = resolveSongAudioUrl(post)
        if (audioUrl == null) {
            _state.value = SongPlaybackState(
                postId = postId,
                error = "This song is locked or unavailable.",
                errorNonce = System.nanoTime(),
            )
            return
        }

        onPlayRequested()
        val songPlayer = ensurePlayer()
        val current = _state.value
        if (current.postId == postId && current.error == null) {
            if (songPlayer.isPlaying) {
                songPlayer.pause()
            } else {
                if (songPlayer.playbackState == Player.STATE_ENDED) songPlayer.seekTo(0)
                songPlayer.play()
            }
            return
        }

        _state.value = SongPlaybackState(
            postId = postId,
            isBuffering = true,
            durationMs = post.songPresentation?.durationMs?.takeIf { it > 0 },
        )
        songPlayer.setMediaItem(MediaItem.fromUri(audioUrl))
        songPlayer.prepare()
        songPlayer.play()
    }

    @MainThread
    fun seek(postId: String, positionMs: Long) {
        val songPlayer = player ?: return
        if (_state.value.postId != postId) return
        val durationMs = resolvedDuration(songPlayer) ?: _state.value.durationMs
        val targetMs = durationMs?.let { positionMs.coerceIn(0, it) } ?: positionMs.coerceAtLeast(0)
        songPlayer.seekTo(targetMs)
        _state.value = _state.value.copy(positionMs = targetMs, durationMs = durationMs)
    }

    @MainThread
    fun pause() {
        player?.pause()
    }

    @MainThread
    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        return ExoPlayer.Builder(appContext).build().also { nextPlayer ->
            nextPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            nextPlayer.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.value = _state.value.copy(
                        isPlaying = isPlaying,
                        isBuffering = false,
                    )
                    progressHandler.removeCallbacks(progressUpdate)
                    updateProgress()
                    if (isPlaying) progressHandler.post(progressUpdate)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val current = _state.value
                    _state.value = when (playbackState) {
                        Player.STATE_BUFFERING -> current.copy(isBuffering = true)
                        Player.STATE_READY -> current.copy(isBuffering = false)
                        Player.STATE_ENDED -> current.copy(
                            isPlaying = false,
                            isBuffering = false,
                            positionMs = current.durationMs ?: current.positionMs,
                        )
                        else -> current
                    }
                    updateProgress()
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = error.message ?: "Could not play song.",
                        errorNonce = System.nanoTime(),
                    )
                }
            })
            player = nextPlayer
        }
    }

    @MainThread
    private fun updateProgress() {
        val songPlayer = player ?: return
        val current = _state.value
        if (current.postId == null) return
        val durationMs = resolvedDuration(songPlayer) ?: current.durationMs
        val positionMs = songPlayer.currentPosition.coerceAtLeast(0).let { position ->
            durationMs?.let { position.coerceAtMost(it) } ?: position
        }
        _state.value = current.copy(positionMs = positionMs, durationMs = durationMs)
    }

    private fun resolvedDuration(songPlayer: Player): Long? =
        songPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0 }

    private companion object {
        const val PROGRESS_UPDATE_INTERVAL_MS = 250L
    }
}

fun resolveSongAudioUrl(post: LocalizedPostResponse): String? {
    if (post.post.accessMode == "locked") return null
    return resolvePublicMediaSrc(post.post.mediaRefs.firstOrNull()?.storageRef)
}
