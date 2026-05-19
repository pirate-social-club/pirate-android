package sc.pirate.app.video

import android.content.Context
import android.view.LayoutInflater
import androidx.annotation.MainThread
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sc.pirate.app.R
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.PostMediaRef
import sc.pirate.app.shared.resolvePublicMediaSrc

data class VideoPlaybackState(
    val postId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val muted: Boolean = true,
    val showControls: Boolean = false,
    val error: String? = null,
    val errorNonce: Long = 0,
)

class VideoPlaybackController(
    context: Context,
    private val onPlayRequested: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VideoPlaybackState())
    val state: StateFlow<VideoPlaybackState> = _state.asStateFlow()

    private var player: ExoPlayer? = null

    @MainThread
    fun playPreview(post: LocalizedPostResponse) {
        play(post = post, muted = true, showControls = false, repeatMode = Player.REPEAT_MODE_ONE)
    }

    @MainThread
    fun playDetail(post: LocalizedPostResponse) {
        play(post = post, muted = false, showControls = true, repeatMode = Player.REPEAT_MODE_OFF)
    }

    @MainThread
    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(isPlaying = false, isBuffering = false)
    }

    @MainThread
    private fun play(
        post: LocalizedPostResponse,
        muted: Boolean,
        showControls: Boolean,
        repeatMode: Int,
    ) {
        val postId = post.post.postId
        val videoUrl = resolveVideoUrl(post)
        if (videoUrl == null) {
            _state.value = VideoPlaybackState(
                postId = postId,
                muted = muted,
                showControls = showControls,
                error = "This video is locked or unavailable.",
                errorNonce = System.nanoTime(),
            )
            return
        }

        onPlayRequested()
        val videoPlayer = ensurePlayer()
        val current = _state.value
        videoPlayer.volume = if (muted) 0f else 1f
        videoPlayer.repeatMode = repeatMode
        if (current.postId == postId && current.error == null) {
            _state.value = current.copy(muted = muted, showControls = showControls)
            videoPlayer.play()
            return
        }

        _state.value = VideoPlaybackState(
            postId = postId,
            isBuffering = true,
            muted = muted,
            showControls = showControls,
        )
        videoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        videoPlayer.prepare()
        videoPlayer.play()
    }

    @MainThread
    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        return ExoPlayer.Builder(appContext).build().also { nextPlayer ->
            nextPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
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
                        Player.STATE_ENDED -> current.copy(isPlaying = false, isBuffering = false)
                        else -> current
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isBuffering = false,
                        error = error.message ?: "Could not play video.",
                        errorNonce = System.nanoTime(),
                    )
                }
            })
            player = nextPlayer
        }
    }

    @MainThread
    fun currentPlayer(): ExoPlayer? = player
}

@Composable
fun VideoPlayerView(
    player: Player?,
    showControls: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { context ->
            if (showControls) {
                PlayerView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            } else {
                (LayoutInflater.from(context)
                    .inflate(R.layout.pirate_player_view_texture, null, false) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            }
        },
        update = { view ->
            view.useController = showControls
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            view.player = player
        },
    )
}

fun resolveVideoUrl(post: LocalizedPostResponse): String? {
    if (post.post.accessMode == "locked") return null
    return post.primaryVideoMedia()?.storageRef?.let(::resolvePublicMediaSrc)
}

fun resolveVideoPosterUrl(post: LocalizedPostResponse): String? =
    post.primaryVideoMedia()?.let { media ->
        (media.posterRef ?: media.storageRef).let(::resolvePublicMediaSrc)
    }

fun videoAspectRatio(post: LocalizedPostResponse): Float {
    val media = post.primaryVideoMedia()
    val width = media?.posterWidth ?: media?.width
    val height = media?.posterHeight ?: media?.height
    val ratio = if (width != null && height != null && width > 0 && height > 0) {
        width.toFloat() / height.toFloat()
    } else {
        16f / 9f
    }
    return ratio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
}

fun isVideoPost(post: LocalizedPostResponse): Boolean =
    post.post.postType == "video" || post.primaryVideoMedia() != null

private fun LocalizedPostResponse.primaryVideoMedia(): PostMediaRef? =
    post.mediaRefs.firstOrNull { it.mimeType?.startsWith("video/") == true }
