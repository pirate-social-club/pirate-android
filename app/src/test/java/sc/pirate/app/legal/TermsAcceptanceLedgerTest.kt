package sc.pirate.app.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TermsAcceptanceLedgerTest {
    @Test
    fun `acceptance is scoped to the signed in account`() {
        val ledger = TermsAcceptanceLedger()
            .withAcceptance("USR_ONE", acceptance("2026-03-23", 100))

        assertTrue(ledger.accepts("usr_one", "2026-03-23"))
        assertFalse(ledger.accepts("usr_two", "2026-03-23"))
    }

    @Test
    fun `new terms version requires fresh acceptance`() {
        val ledger = TermsAcceptanceLedger()
            .withAcceptance("usr_one", acceptance("2026-03-23", 100))

        assertFalse(ledger.accepts("usr_one", "2026-08-01"))
        assertEquals("2026-03-23", ledger.acceptanceFor("USR_ONE")?.version)
    }

    @Test
    fun `new acceptance replaces the prior version only for that account`() {
        val ledger = TermsAcceptanceLedger()
            .withAcceptance("usr_one", acceptance("v1", 100))
            .withAcceptance("usr_two", acceptance("v1", 200))
            .withAcceptance("USR_ONE", acceptance("v2", 300))

        assertEquals("v2", ledger.acceptanceFor("usr_one")?.version)
        assertEquals(300L, ledger.acceptanceFor("usr_one")?.acceptedAtEpochMs)
        assertEquals("v1", ledger.acceptanceFor("usr_two")?.version)
        assertNull(ledger.acceptanceFor("usr_three"))
    }

    @Test
    fun `blank account and version are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TermsAcceptanceLedger().withAcceptance("", acceptance("v1", 100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TermsAcceptanceLedger().withAcceptance("usr_one", acceptance("", 100))
        }
    }

    private fun acceptance(version: String, time: Long) = TermsAcceptance(
        version = version,
        acceptedAtEpochMs = time,
    )
}
