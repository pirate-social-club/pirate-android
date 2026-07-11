package sc.pirate.app.security

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AgentKeyStoreTest {
    @Test
    fun `persists only encrypted private key material`() {
        val persistence = MemoryAgentKeyPersistence()
        val store = AgentKeyStore(persistence, PrefixCipher())
        val record = StoredAgentKey(
            agentId = "agt_test",
            displayName = "Deckhand",
            ownershipProvider = "clawkey",
            publicKeyPem = "-----BEGIN PUBLIC KEY-----public-----END PUBLIC KEY-----",
            privateKeyPem = "-----BEGIN PRIVATE KEY-----secret-private-material-----END PRIVATE KEY-----",
            createdAt = "2026-07-11T00:00:00Z",
            updatedAt = "2026-07-11T00:00:00Z",
        )

        store.save(record)

        val persisted = persistence.read(record.agentId).orEmpty()
        assertFalse(persisted.contains("secret-private-material"))
        assertEquals(record, store.find(record.agentId))
        assertEquals(listOf(record), store.list())
        store.remove(record.agentId)
        assertNull(store.find(record.agentId))
    }
}

private class PrefixCipher : SessionCipher {
    override fun encrypt(plaintext: String): String = Base64.getEncoder().encodeToString(plaintext.toByteArray())
    override fun decrypt(payload: String): String = String(Base64.getDecoder().decode(payload))
}

private class MemoryAgentKeyPersistence : AgentKeyPersistence {
    private val values = mutableMapOf<String, String>()
    override fun read(agentId: String): String? = values[agentId]
    override fun readAll(): List<String> = values.values.toList()
    override fun write(agentId: String, encryptedValue: String) { values[agentId] = encryptedValue }
    override fun remove(agentId: String) { values.remove(agentId) }
}
