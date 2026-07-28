package sc.pirate.app.video

import android.app.Application
import android.graphics.Color as AndroidColor
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.R
import sc.pirate.app.api.model.LocalizedPostResponse

/**
 * A single playable page. Resolved once at load time so the pager never re-derives a URL or an
 * aspect ratio during a scroll frame — the old feed detected aspect ratio after the image loaded,
 * which is why items visibly jumped.
 */
data class VideoPagerItem(
    val postId: String,
    val communityId: String,
    val url: String,
    val posterUrl: String?,
    val handle: String,
    val caption: String?,
    val post: LocalizedPostResponse,
)

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

    private val _state = MutableStateFlow(VideoPagerUiState())
    val state: StateFlow<VideoPagerUiState> = _state.asStateFlow()

    private var loadGeneration = 0
    private val seenPostIds = mutableSetOf<String>()

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

    private class VideoPage(val items: List<VideoPagerItem>, val nextCursor: String?)

    /**
     * The home feed is mixed, so a single page can contain no video at all. Walk forward until the
     * page yields something rather than handing the pager an empty list and stopping — but stop at
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
                collected += VideoPagerItem(
                    postId = postId,
                    communityId = entry.community.communityId ?: post.post.communityId,
                    url = url,
                    posterUrl = resolveVideoPosterUrl(post),
                    handle = entry.community.displayName,
                    caption = (post.post.caption ?: post.post.title)?.takeIf { it.isNotBlank() },
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

/**
 * Fullscreen vertical video surface.
 *
 * Smoothness here comes from three rules, all of which the old inline feed broke:
 * playback is owned by a [VideoPlayerPool] rather than by composition, the page after the
 * current one is prepared before the viewer reaches it, and a poster frame is drawn from the
 * first frame of the page's life so buffering is never visible as emptiness.
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

    val pool = remember {
        val app = context.applicationContext as PirateApp
        VideoPlayerPool(app, app.videoMediaCache.mediaSourceFactory)
    }
    DisposableEffect(pool) {
        onDispose { pool.releaseAll() }
    }

    // Leaving the app must stop audio; returning resumes the page the viewer was on.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, pool) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pool.pauseAll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    VideoPagerPlaybackEffect(
        haptics = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
        items = state.items,
        onNearEnd = viewModel::loadMore,
        pagerState = pagerState,
        pool = pool,
    )

    VerticalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().background(Color.Black),
        // One warmed neighbour on each side matches the pool, so a page the pager has already
        // laid out is a page the pool can already be holding.
        beyondViewportPageCount = 1,
        key = { index -> state.items[index].postId },
    ) { page ->
        VideoPagerPage(
            item = state.items[page],
            pool = pool,
        )
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
    pagerState: PagerState,
    pool: VideoPlayerPool,
) {
    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settled ->
                val current = items.getOrNull(settled) ?: return@collect
                pool.obtain(current.postId, current.url)
                pool.playOnly(current.postId)
                haptics()

                // Prepare the next page while the current one plays. This is the whole point of a
                // two-player pool: by the time the viewer swipes, its first frame is decoded.
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
    item: VideoPagerItem,
    pool: VideoPlayerPool,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Drawn unconditionally and never removed: the player's shutter is transparent, so the
        // poster is what the viewer sees until the first frame decodes. Removing it on first
        // frame would cost a recomposition at the exact moment playback starts.
        if (item.posterUrl != null) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.caption ?: item.handle,
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
                        // Transparent shutter lets the poster show through until the first frame,
                        // instead of the black fill the layout would otherwise paint over it.
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = item.handle,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
            item.caption?.let { caption ->
                Text(
                    text = caption,
                    color = Color.White,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
