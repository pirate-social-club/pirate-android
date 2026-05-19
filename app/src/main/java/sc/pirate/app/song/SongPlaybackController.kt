package sc.pirate.app.song

import android.content.Context
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
                songPlayer.play()
            }
            return
        }

        _state.value = SongPlaybackState(postId = postId, isBuffering = true)
        songPlayer.setMediaItem(MediaItem.fromUri(audioUrl))
        songPlayer.prepare()
        songPlayer.play()
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
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val current = _state.value
                    _state.value = when (playbackState) {
                        Player.STATE_BUFFERING -> current.copy(isBuffering = true)
                        Player.STATE_READY -> current.copy(isBuffering = false)
                        Player.STATE_ENDED -> SongPlaybackState()
                        else -> current
                    }
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
}

fun resolveSongAudioUrl(post: LocalizedPostResponse): String? {
    if (post.post.accessMode == "locked") return null
    return resolvePublicMediaSrc(post.post.mediaRefs.firstOrNull()?.storageRef)
}
