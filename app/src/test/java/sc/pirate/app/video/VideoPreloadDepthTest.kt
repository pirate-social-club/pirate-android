package sc.pirate.app.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPreloadDepthTest {

    @Test
    fun `the playing page is not preloaded`() {
        // It is owned by a real player; asking the manager to prepare it too would duplicate work.
        assertEquals(VideoPreloadDepth.NONE, videoPreloadDepthFor(currentIndex = 4, itemIndex = 4))
    }

    @Test
    fun `depth decreases with forward distance`() {
        assertEquals(VideoPreloadDepth.LOADED, videoPreloadDepthFor(currentIndex = 0, itemIndex = 1))
        assertEquals(VideoPreloadDepth.TRACKS_SELECTED, videoPreloadDepthFor(currentIndex = 0, itemIndex = 2))
        assertEquals(VideoPreloadDepth.SOURCE_PREPARED, videoPreloadDepthFor(currentIndex = 0, itemIndex = 3))
    }

    @Test
    fun `pages beyond the window are left alone`() {
        assertEquals(VideoPreloadDepth.NONE, videoPreloadDepthFor(currentIndex = 0, itemIndex = 4))
        assertEquals(VideoPreloadDepth.NONE, videoPreloadDepthFor(currentIndex = 0, itemIndex = 40))
    }

    /**
     * Backscrolling is already free: the pool still holds the previous page's player and the media
     * cache holds its bytes. Spending bandwidth there would take it from the forward direction,
     * which is the one the viewer is actually moving in.
     */
    @Test
    fun `backward pages are never preloaded`() {
        assertEquals(VideoPreloadDepth.NONE, videoPreloadDepthFor(currentIndex = 5, itemIndex = 4))
        assertEquals(VideoPreloadDepth.NONE, videoPreloadDepthFor(currentIndex = 5, itemIndex = 0))
    }

    @Test
    fun `the window travels with the current page`() {
        assertEquals(VideoPreloadDepth.LOADED, videoPreloadDepthFor(currentIndex = 12, itemIndex = 13))
        assertEquals(VideoPreloadDepth.SOURCE_PREPARED, videoPreloadDepthFor(currentIndex = 12, itemIndex = 15))
        assertEquals(VideoPreloadDepth.NONE, videoPreloadDepthFor(currentIndex = 12, itemIndex = 16))
    }

    @Test
    fun `only one page is fully buffered at a time`() {
        val fullyLoaded = (0..20).filter {
            videoPreloadDepthFor(currentIndex = 7, itemIndex = it) == VideoPreloadDepth.LOADED
        }
        assertEquals(listOf(8), fullyLoaded)
    }
}
