package sc.pirate.app.video

import android.app.Application
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import sc.pirate.app.BuildConfig
import sc.pirate.app.PirateApp
import sc.pirate.app.R
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.shared.resolvePublicMediaSrc
import sc.pirate.app.ui.PhosphorIcons

/**
 * A single playable page. Everything the surface draws is resolved once, at load time: deriving a
 * URL, a poster or an aspect ratio during a scroll frame is what made the old inline feed jump.
 */
data class VideoPagerItem(
    val postId: String,
    val communityId: String,
    val url: String,
    val posterUrl: String?,
    val handle: String,
    val avatarUrl: String?,
    val caption: String?,
    val songLabel: String?,
    val likeCount: Int,
    val commentCount: Int,
    val viewerVote: Int?,
    val post: LocalizedPostResponse,
) {
    val liked: Boolean get() = (viewerVote ?: 0) > 0
}

data class VideoPagerUiState(
    val items: List<VideoPagerItem> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val exhausted: Boolean = false,
)

class VideoPagerViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val feedRepository get() = app.repositories.feedRepository
    private val postRepository get() = app.repositories.postRepository

    private val _state = MutableStateFlow(VideoPagerUiState())
    val state: StateFlow<VideoPagerUiState> = _state.asStateFlow()

    private var loadGeneration = 0
    private val seenPostIds = mutableSetOf<String>()
    private val votesInFlight = mutableSetOf<String>()

    fun load() {
        if (!_state.value.loading && _state.value.items.isNotEmpty()) return
        val generation = ++loadGeneration
        viewModelScope.launch {
            try {
                val page = huntVideoPage(cursor = null, generation = generation)
                if (generation != loadGeneration) return@launch
                _state.value = VideoPagerUiState(
                    items = page.items,
                    loading = false,
                    nextCursor = page.nextCursor,
                    exhausted = page.nextCursor == null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation != loadGeneration) return@launch
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Could not load videos.")
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor
        if (cursor == null || current.loadingMore || current.exhausted) return
        val generation = loadGeneration
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val page = huntVideoPage(cursor = cursor, generation = generation)
                if (generation != loadGeneration) return@launch
                _state.value = _state.value.copy(
                    items = _state.value.items + page.items,
                    loadingMore = false,
                    nextCursor = page.nextCursor,
                    exhausted = page.nextCursor == null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation != loadGeneration) return@launch
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    /**
     * Optimistic like. The rail has to answer the tap on the frame it happens — a heart that waits
     * for a round trip reads as a dropped input, which is exactly the cheapness we are removing.
     */
    fun toggleLike(item: VideoPagerItem) {
        if (!votesInFlight.add(item.postId)) return
        val nextVote = if (item.liked) 0 else 1
        applyVote(item.postId, nextVote)
        viewModelScope.launch {
            try {
                val response = postRepository.votePost(item.postId, nextVote)
                applyVote(item.postId, response.value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Put the viewer's own state back rather than leaving a like that never landed.
                applyVote(item.postId, item.viewerVote ?: 0)
            } finally {
                votesInFlight.remove(item.postId)
            }
        }
    }

    private fun applyVote(postId: String, value: Int) {
        _state.value = _state.value.copy(items = applyVideoVote(_state.value.items, postId, value))
    }

    private class VideoPage(val items: List<VideoPagerItem>, val nextCursor: String?)

    /**
     * The home feed is mixed, so a single page can contain no video at all. Walk forward until a
     * page yields something rather than handing the pager an empty list, but stop at
     * [MAX_HUNT_PAGES] so a video-less corpus cannot spin the request loop.
     */
    private suspend fun huntVideoPage(cursor: String?, generation: Int): VideoPage {
        var nextCursor = cursor
        val collected = mutableListOf<VideoPagerItem>()
        for (page in 0 until MAX_HUNT_PAGES) {
            val response = feedRepository.home(cursor = nextCursor)
            if (generation != loadGeneration) return VideoPage(emptyList(), null)
            nextCursor = response.nextCursor
            response.items.forEach { entry ->
                val post = entry.post
                if (!isVideoPost(post)) return@forEach
                val url = resolveVideoUrl(post) ?: return@forEach
                val postId = post.post.postId
                if (postId.isBlank() || !seenPostIds.add(postId)) return@forEach
                val song = post.songPresentation
                collected += VideoPagerItem(
                    postId = postId,
                    communityId = entry.community.communityId ?: post.post.communityId,
                    url = url,
                    posterUrl = resolveVideoPosterUrl(post),
                    handle = entry.community.displayName,
                    avatarUrl = resolvePublicMediaSrc(entry.community.avatarRef),
                    caption = (post.post.caption ?: post.post.title)?.takeIf { it.isNotBlank() },
                    songLabel = song?.title?.takeIf { it.isNotBlank() },
                    likeCount = post.likeCount.takeIf { it > 0 } ?: post.upvoteCount,
                    commentCount = post.commentCount ?: 0,
                    viewerVote = post.viewerVote,
                    post = post,
                )
            }
            if (collected.isNotEmpty() || nextCursor == null) break
        }
        return VideoPage(collected, nextCursor)
    }

    private companion object {
        const val MAX_HUNT_PAGES = 4
    }
}

/** Compact counts, matching the feed's other surfaces: 1.2K rather than 1203. */
fun compactCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> trimZero(value / 1_000.0) + "K"
    else -> trimZero(value / 1_000_000.0) + "M"
}

private fun trimZero(value: Double): String {
    val rounded = kotlin.math.round(value * 10) / 10
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}

/**
 * Fullscreen vertical video surface.
 *
 * Playback is owned by a [VideoPlayerPool] rather than by composition, the page after the current
 * one is prepared before the viewer reaches it, and a poster frame is drawn for the whole life of
 * a page so buffering never reads as emptiness.
 */
@Composable
fun VideoPagerScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoPagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) { viewModel.load() }

    val soundPreference = remember { VideoSoundPreference(context) }
    // Autoplay always begins muted; the stored preference only decides whether we unmute once the
    // first page is ready, so opening the app in public is never loud.
    var muted by remember { mutableStateOf(true) }
    var storedSoundApplied by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }

    val preload = remember {
        val app = context.applicationContext as PirateApp
        VideoPreloadCoordinator(app, app.videoMediaCache.mediaSourceFactory)
    }
    val pool = remember(preload) {
        VideoPlayerPool(
            createPlayer = preload::createPlayer,
            preloadedSourceFor = preload::mediaSourceFor,
        )
    }
    DisposableEffect(pool, preload) {
        onDispose {
            // Players first: they hold periods the manager owns.
            pool.releaseAll()
            preload.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pool) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pool.pauseAll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(muted, pool.heldKeys) { pool.setMuted(muted) }

    // Restore the viewer's sound choice once a player exists, never before: the feed must open
    // muted even for someone who previously chose sound, and only unmute once there is something
    // to unmute. Guarded so a later swipe cannot re-apply it over a fresh mute.
    LaunchedEffect(pool.heldKeys) {
        if (storedSoundApplied || pool.heldKeys.isEmpty()) return@LaunchedEffect
        storedSoundApplied = true
        if (!soundPreference.muted) muted = false
    }

    if (state.loading) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val error = state.error
    if (error != null || state.items.isEmpty()) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                text = error ?: "No videos yet",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { state.items.size })

    LaunchedEffect(preload, state.items) {
        preload.setItems(state.items.map { it.url })
    }

    VideoPagerPlaybackEffect(
        haptics = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        items = state.items,
        onNearEnd = viewModel::loadMore,
        onSettled = { paused = false },
        pagerState = pagerState,
        pool = pool,
        preload = preload,
    )

    Box(modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            key = { index -> state.items[index].postId },
        ) { page ->
            val item = state.items[page]
            VideoPagerPage(
                active = page == pagerState.settledPage,
                item = item,
                muted = muted,
                onShare = { sharePost(context, item) },
                onToggleLike = { viewModel.toggleLike(item) },
                onToggleMuted = {
                    val next = !muted
                    muted = next
                    soundPreference.muted = next
                },
                onTogglePaused = {
                    val next = !paused
                    paused = next
                    if (next) pool.pauseAll() else pool.playOnly(item.postId)
                },
                paused = paused,
                pool = pool,
            )
        }
    }
}

/**
 * Binds the settled page to the pool: the settled page plays, the next one is prepared and left
 * paused. Keyed on the settled index rather than the scroll offset so playback never oscillates
 * while a drag is crossing a page boundary.
 */
@Composable
private fun VideoPagerPlaybackEffect(
    haptics: () -> Unit,
    items: List<VideoPagerItem>,
    onNearEnd: () -> Unit,
    onSettled: () -> Unit,
    pagerState: PagerState,
    pool: VideoPlayerPool,
    preload: VideoPreloadCoordinator,
) {
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                val current = items.getOrNull(settled) ?: return@collect
                // Move the preload window before binding players, so the next page is already
                // being prepared while the current one starts.
                preload.setCurrentIndex(settled)
                pool.obtain(current.postId, current.url)
                pool.playOnly(current.postId)
                onSettled()
                haptics()

                items.getOrNull(settled + 1)?.let { next ->
                    pool.obtain(next.postId, next.url)
                    pool.playOnly(current.postId)
                }

                if (settled >= items.size - NEAR_END_THRESHOLD) onNearEnd()
            }
    }
}

private const val NEAR_END_THRESHOLD = 3

@Composable
private fun VideoPagerPage(
    active: Boolean,
    item: VideoPagerItem,
    muted: Boolean,
    onShare: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleMuted: () -> Unit,
    onTogglePaused: () -> Unit,
    paused: Boolean,
    pool: VideoPlayerPool,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // No ripple: a spreading circle over full-bleed video looks like a rendering fault.
            .clickable(interactionSource = interactionSource, indication = null, onClick = onTogglePaused),
    ) {
        // Drawn unconditionally and never removed. The player's shutter is transparent, so the
        // poster is what the viewer sees until the first frame decodes; removing it on first frame
        // would cost a recomposition at the exact moment playback starts.
        if (item.posterUrl != null) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.caption ?: item.handle,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (item.postId in pool.heldKeys) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    (LayoutInflater.from(viewContext)
                        .inflate(R.layout.pirate_player_view_texture, null, false) as PlayerView).apply {
                        useController = false
                        setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                    }
                },
                update = { view ->
                    val player = pool.playerFor(item.postId)
                    if (view.player !== player) view.player = player
                },
                onRelease = { view -> view.player = null },
            )
        }

        // Scrims: without them white captions vanish over a bright frame. Kept as gradients rather
        // than a flat overlay so the middle of the video is never dimmed.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )

        if (paused) {
            Icon(
                imageVector = PhosphorIcons.Play,
                contentDescription = "Play",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(72.dp).shadow(8.dp, CircleShape),
            )
        }

        VideoSoundToggle(
            muted = muted,
            onToggle = onToggleMuted,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp),
        )

        VideoRail(
            item = item,
            onShare = onShare,
            onToggleLike = onToggleLike,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 10.dp, bottom = BOTTOM_BAR_CLEARANCE),
        )

        VideoCaption(
            item = item,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 88.dp, bottom = BOTTOM_BAR_CLEARANCE),
        )

        if (active) {
            VideoProgressBar(
                player = { pool.playerFor(item.postId) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth(),
            )
        }
    }
}

/** The bottom nav overlays the video, so every overlaid control clears its height. */
private val BOTTOM_BAR_CLEARANCE = 76.dp

@Composable
private fun VideoSoundToggle(muted: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (muted) PhosphorIcons.SpeakerSlashFill else PhosphorIcons.SpeakerHighFill,
            contentDescription = if (muted) "Sound on" else "Mute video",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun VideoRail(
    item: VideoPagerItem,
    onShare: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (item.avatarUrl != null) {
            AsyncImage(
                model = item.avatarUrl,
                contentDescription = "Publisher ${item.handle}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.DarkGray),
            )
        }
        VideoRailAction(
            icon = PhosphorIcons.HeartFill,
            label = "Like",
            // Filled either way; colour carries the state, because an outline disappears against
            // a bright frame at this size.
            tint = if (item.liked) Color(0xFFFF2D55) else Color.White,
            value = compactCount(item.likeCount),
            onClick = onToggleLike,
        )
        // Comments are deliberately absent until there is a sheet behind them. A rail button that
        // does nothing teaches viewers the rail is decorative, which is worse than one fewer icon.
        VideoRailAction(
            icon = PhosphorIcons.ShareFatFill,
            label = "Share",
            value = "Share",
            onClick = onShare,
        )
    }
}

@Composable
private fun VideoRailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color = Color.White,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) {
            Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        } else {
            Modifier
        },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(34.dp).shadow(4.dp, CircleShape),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun VideoCaption(item: VideoPagerItem, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = item.handle,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.caption?.let { caption ->
            Text(
                text = caption,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        item.songLabel?.let { song ->
            Text(
                text = song,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A hairline progress line. Polled rather than driven by a listener because ExoPlayer has no
 * per-frame position callback; 150ms is under the threshold where the line reads as stepping.
 */
@Composable
private fun VideoProgressBar(
    player: () -> androidx.media3.exoplayer.ExoPlayer?,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = player()
            val duration = current?.duration ?: 0L
            progress = if (current != null && duration > 0L) {
                (current.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            delay(150)
        }
    }
    Box(modifier.height(2.dp).background(Color.White.copy(alpha = 0.25f))) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(2.dp)
                .background(Color.White),
        )
    }
}

/**
 * Hands the post to the system share sheet. Shares the public web page rather than the media URL:
 * a raw media link has no attribution, no caption, and stops working the moment storage moves.
 */
private fun sharePost(context: Context, item: VideoPagerItem) {
    val base = BuildConfig.WEB_BASE_URL.trim().trimEnd('/')
    val url = "$base/p/${item.postId}"
    val text = listOfNotNull(item.caption?.takeIf { it.isNotBlank() }, url).joinToString("\n\n")
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
