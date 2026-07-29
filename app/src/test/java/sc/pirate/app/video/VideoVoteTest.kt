package sc.pirate.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.Post

private fun item(id: String, likeCount: Int, viewerVote: Int?) = VideoPagerItem(
    postId = id,
    communityId = "cmt_1",
    url = "https://media.test/$id.mp4",
    posterUrl = null,
    handle = "handle",
    avatarUrl = null,
    caption = null,
    songLabel = null,
    likeCount = likeCount,
    commentCount = 0,
    viewerVote = viewerVote,
    post = LocalizedPostResponse(post = Post()),
)

class VideoVoteTest {

    @Test
    fun `liking an unvoted post increments the count`() {
        val result = applyVideoVote(listOf(item("a", likeCount = 4, viewerVote = null)), "a", 1)

        assertEquals(5, result[0].likeCount)
        assertTrue(result[0].liked)
    }

    @Test
    fun `clearing a like decrements the count`() {
        val result = applyVideoVote(listOf(item("a", likeCount = 5, viewerVote = 1)), "a", 0)

        assertEquals(4, result[0].likeCount)
        assertFalse(result[0].liked)
    }

    /**
     * The server's answer is applied after the optimistic one. When it agrees, the count must not
     * move a second time — that double-count is invisible until a viewer watches their own like
     * total creep upward.
     */
    @Test
    fun `re-applying the same vote does not move the count`() {
        val optimistic = applyVideoVote(listOf(item("a", likeCount = 4, viewerVote = null)), "a", 1)
        val confirmed = applyVideoVote(optimistic, "a", 1)

        assertEquals(5, confirmed[0].likeCount)
        assertSame(optimistic[0], confirmed[0])
    }

    @Test
    fun `rollback after a failed like restores the original count`() {
        val original = item("a", likeCount = 4, viewerVote = null)
        val optimistic = applyVideoVote(listOf(original), "a", 1)
        val rolledBack = applyVideoVote(optimistic, "a", 0)

        assertEquals(original.likeCount, rolledBack[0].likeCount)
        assertFalse(rolledBack[0].liked)
    }

    /** A downvote is not a like, so crossing between two non-positive values changes nothing. */
    @Test
    fun `moving between non-positive votes leaves the like count alone`() {
        val result = applyVideoVote(listOf(item("a", likeCount = 7, viewerVote = -1)), "a", 0)

        assertEquals(7, result[0].likeCount)
    }

    @Test
    fun `downvoting a liked post removes the like`() {
        val result = applyVideoVote(listOf(item("a", likeCount = 3, viewerVote = 1)), "a", -1)

        assertEquals(2, result[0].likeCount)
        assertFalse(result[0].liked)
    }

    @Test
    fun `a count never goes negative`() {
        val result = applyVideoVote(listOf(item("a", likeCount = 0, viewerVote = 1)), "a", 0)

        assertEquals(0, result[0].likeCount)
    }

    @Test
    fun `other posts are untouched and keep their identity`() {
        val other = item("b", likeCount = 9, viewerVote = null)
        val result = applyVideoVote(listOf(item("a", likeCount = 1, viewerVote = null), other), "a", 1)

        assertSame(other, result[1])
        assertEquals(9, result[1].likeCount)
    }

    @Test
    fun `an unknown post id changes nothing`() {
        val items = listOf(item("a", likeCount = 1, viewerVote = null))
        val result = applyVideoVote(items, "ghost", 1)

        assertEquals(1, result[0].likeCount)
        assertSame(items[0], result[0])
    }
}

class CompactCountTest {

    @Test
    fun `counts below a thousand are exact`() {
        assertEquals("0", compactCount(0))
        assertEquals("999", compactCount(999))
    }

    @Test
    fun `thousands are abbreviated`() {
        assertEquals("1K", compactCount(1_000))
        assertEquals("1.2K", compactCount(1_234))
        assertEquals("12.3K", compactCount(12_345))
    }

    @Test
    fun `millions are abbreviated`() {
        assertEquals("1M", compactCount(1_000_000))
        assertEquals("2.5M", compactCount(2_500_000))
    }

    /** A trailing .0 reads as spurious precision on a rail glyph. */
    @Test
    fun `whole values drop the decimal`() {
        assertEquals("5K", compactCount(5_000))
        assertEquals("3M", compactCount(3_000_000))
    }
}
