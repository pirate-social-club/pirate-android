package sc.pirate.app.karaoke

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class KaraokePlaybackState(
    val prepared: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val ended: Boolean = false,
    val error: String? = null,
)

interface KaraokeInstrumentalPlayback {
    val state: StateFlow<KaraokePlaybackState>
    val currentPositionMs: Long
    fun prepare(url: String)
    fun play()
    fun pause()
    fun stop()
    fun release()
}

class ExoPlayerKaraokeInstrumentalPlayback(
    context: Context,
) : KaraokeInstrumentalPlayback {
    private val appContext = context.applicationContext
    private val player: ExoPlayer = ExoPlayer.Builder(appContext).build()
    private val _state = MutableStateFlow(KaraokePlaybackState())
    override val state: StateFlow<KaraokePlaybackState> = _state.asStateFlow()

    override val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0)

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(
                    isPlaying = isPlaying,
                    isBuffering = false,
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val current = _state.value
                _state.value = when (playbackState) {
                    Player.STATE_BUFFERING -> current.copy(isBuffering = true, ended = false)
                    Player.STATE_READY -> current.copy(prepared = true, isBuffering = false, ended = false)
                    Player.STATE_ENDED -> current.copy(isPlaying = false, isBuffering = false, ended = true)
                    else -> current
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    error = error.message ?: "Could not play karaoke audio.",
                )
            }
        })
    }

    override fun prepare(url: String) {
        if (url.isBlank()) {
            _state.value = KaraokePlaybackState(error = "Karaoke audio is unavailable.")
            return
        }
        _state.value = KaraokePlaybackState(isBuffering = true)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
        _state.value = KaraokePlaybackState(prepared = false)
    }

    override fun release() {
        player.release()
    }
}
