package sc.pirate.app.security

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.Assert.assertTrue

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

    @Test
    fun `matches SPKI PEM with raw Ed25519 public key`() {
        val raw = ByteArray(32) { it.toByte() }
        val spkiPrefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
        val pem = "-----BEGIN PUBLIC KEY-----\n${Base64.getEncoder().encodeToString(spkiPrefix + raw)}\n-----END PUBLIC KEY-----"
        assertTrue(agentPublicKeysMatch(pem, Base64.getUrlEncoder().withoutPadding().encodeToString(raw)))
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
