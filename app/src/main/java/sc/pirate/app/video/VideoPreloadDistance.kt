package sc.pirate.app.video

/**
 * How much of a page to have ready, as a function of how far it sits from the page being watched.
 *
 * Split out from the preload manager wiring because this is the part with a judgement in it: too
 * shallow and a swipe still waits on the network, too deep and the feed spends the viewer's data
 * on videos they will never reach. Pure, so the policy is testable without a device.
 *
 * Backward distances get nothing — the player pool still holds the previous page, and the media
 * cache holds its bytes, so scrolling back is already free.
 */
enum class VideoPreloadDepth {
    /** Buffer real media, enough to start playing instantly. */
    LOADED,

    /** Prepare the source and pick tracks, but load no media. */
    TRACKS_SELECTED,

    /** Prepare the source only: manifest and metadata. */
    SOURCE_PREPARED,

    /** Far enough away that speculative work is waste. */
    NONE,
}

/** Milliseconds of media to buffer for the page immediately after the one playing. */
const val VIDEO_PRELOAD_NEXT_DURATION_MS = 3_000L

fun videoPreloadDepthFor(currentIndex: Int, itemIndex: Int): VideoPreloadDepth =
    when (itemIndex - currentIndex) {
        // The page being watched is owned by a real player, not by the preload manager.
        0 -> VideoPreloadDepth.NONE
        1 -> VideoPreloadDepth.LOADED
        2 -> VideoPreloadDepth.TRACKS_SELECTED
        3 -> VideoPreloadDepth.SOURCE_PREPARED
        else -> VideoPreloadDepth.NONE
    }
