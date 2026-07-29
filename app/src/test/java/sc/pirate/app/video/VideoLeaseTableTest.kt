package sc.pirate.app.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLeaseTableTest {

    @Test
    fun `admits up to capacity without evicting`() {
        val table = VideoLeaseTable(capacity = 2)

        assertEquals(VideoLeaseTable.Admission.Create, table.admit("a"))
        assertEquals(VideoLeaseTable.Admission.Create, table.admit("b"))

        assertEquals(listOf("a", "b"), table.keys)
    }

    @Test
    fun `evicts the coldest page when full`() {
        val table = VideoLeaseTable(capacity = 2)
        table.admit("a")
        table.admit("b")

        assertEquals(VideoLeaseTable.Admission.Reuse("a"), table.admit("c"))
        assertEquals(listOf("b", "c"), table.keys)
    }

    @Test
    fun `touching a held page makes it survive the next eviction`() {
        val table = VideoLeaseTable(capacity = 2)
        table.admit("a")
        table.admit("b")

        assertTrue(table.touch("a"))

        // "b" is now the coldest, so a third page must take b's player, not a's.
        assertEquals(VideoLeaseTable.Admission.Reuse("b"), table.admit("c"))
        assertEquals(listOf("a", "c"), table.keys)
    }

    @Test
    fun `touching an absent page reports a miss and changes nothing`() {
        val table = VideoLeaseTable(capacity = 2)
        table.admit("a")

        assertFalse(table.touch("ghost"))
        assertEquals(listOf("a"), table.keys)
    }

    /**
     * The pager's steady state: forward swipes warm N+1 while N plays. The page being watched and
     * the page about to be watched must both keep their players at every step.
     */
    @Test
    fun `forward paging keeps the current and next page resident`() {
        val table = VideoLeaseTable(capacity = 2)
        val pages = listOf("p0", "p1", "p2", "p3", "p4")

        pages.forEachIndexed { index, current ->
            if (!table.touch(current)) table.admit(current)
            val next = pages.getOrNull(index + 1)
            if (next != null && !table.touch(next)) table.admit(next)

            assertTrue("current $current evicted", table.holds(current))
            if (next != null) assertTrue("next $next not warmed", table.holds(next))
        }
    }

    /**
     * Backscrolling one page must not thrash: the page behind is still held from when it played,
     * so returning to it should be a touch rather than an eviction.
     */
    @Test
    fun `stepping back one page reuses the held lease`() {
        val table = VideoLeaseTable(capacity = 2)
        table.admit("p0")
        table.admit("p1")

        assertTrue(table.touch("p0"))
        assertEquals(listOf("p1", "p0"), table.keys)
    }

    @Test
    fun `capacity of one always evicts the previous page`() {
        val table = VideoLeaseTable(capacity = 1)
        table.admit("a")

        assertEquals(VideoLeaseTable.Admission.Reuse("a"), table.admit("b"))
        assertEquals(listOf("b"), table.keys)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a zero capacity pool`() {
        VideoLeaseTable(capacity = 0)
    }

    @Test
    fun `clear drops every lease`() {
        val table = VideoLeaseTable(capacity = 2)
        table.admit("a")
        table.admit("b")

        table.clear()

        assertEquals(emptyList<String>(), table.keys)
        assertFalse(table.holds("a"))
    }
}
