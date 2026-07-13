package sc.pirate.app.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserBlockLedgerTest {
    @Test
    fun `block records are isolated by signed in account`() {
        val ledger = UserBlockLedger()
            .withBlocked("USR_VIEWER_A", blockedUser("usr_target", 100))
            .withBlocked("usr_viewer_b", blockedUser("usr_other", 200))

        assertEquals(listOf("usr_target"), ledger.entriesFor("usr_viewer_a").map { it.userId })
        assertEquals(listOf("usr_other"), ledger.entriesFor("USR_VIEWER_B").map { it.userId })
    }

    @Test
    fun `blocking the same user replaces metadata without duplicates`() {
        val ledger = UserBlockLedger()
            .withBlocked("usr_viewer", blockedUser("USR_TARGET", 100, inbox = "old-inbox"))
            .withBlocked("USR_VIEWER", blockedUser("usr_target", 200, inbox = "new-inbox"))

        val records = ledger.entriesFor("usr_viewer")
        assertEquals(1, records.size)
        assertEquals("new-inbox", records.single().xmtpInbox)
        assertEquals(200L, records.single().blockedAtEpochMs)
    }

    @Test
    fun `unblocking only changes the active account`() {
        val ledger = UserBlockLedger()
            .withBlocked("usr_viewer_a", blockedUser("usr_target", 100))
            .withBlocked("usr_viewer_b", blockedUser("usr_target", 200))
            .withoutBlocked("usr_viewer_a", "USR_TARGET")

        assertTrue(ledger.entriesFor("usr_viewer_a").isEmpty())
        assertEquals(1, ledger.entriesFor("usr_viewer_b").size)
    }

    @Test
    fun `self block is rejected case insensitively`() {
        assertThrows(IllegalArgumentException::class.java) {
            UserBlockLedger().withBlocked("usr_viewer", blockedUser("USR_VIEWER", 100))
        }
    }

    @Test
    fun `content visibility matches normalized author ids`() {
        val blocked = setOf("usr_blocked")

        assertFalse(isAuthorVisible("USR_BLOCKED", blocked))
        assertTrue(isAuthorVisible("usr_allowed", blocked))
        assertTrue(isAuthorVisible(null, blocked))
    }

    private fun blockedUser(userId: String, time: Long, inbox: String? = null) = BlockedUser(
        userId = userId,
        xmtpInbox = inbox,
        blockedAtEpochMs = time,
    )
}
