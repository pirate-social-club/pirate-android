package sc.pirate.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PirateDeepLinksTest {
    @Test
    fun `maps canonical web post and community links`() {
        assertEquals(
            "post/pst_123",
            PirateDeepLinks.routeFromParts("https", "pirate.sc", listOf("p", "pst_123")),
        )
        assertEquals(
            "community/%40music",
            PirateDeepLinks.routeFromParts("https", "PIRATE.SC", listOf("c", "@music")),
        )
    }

    @Test
    fun `keeps custom scheme compatibility`() {
        assertEquals(
            "post/pst_123",
            PirateDeepLinks.routeFromParts("pirate", "post", listOf("pst_123")),
        )
    }

    @Test
    fun `rejects untrusted hosts and unsupported paths`() {
        assertNull(PirateDeepLinks.routeFromParts("https", "evil.example", listOf("p", "pst_123")))
        assertNull(PirateDeepLinks.routeFromParts("http", "pirate.sc", listOf("p", "pst_123")))
        assertNull(PirateDeepLinks.routeFromParts("https", "pirate.sc", listOf("delete-account")))
    }
}
