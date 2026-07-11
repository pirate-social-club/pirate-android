package sc.pirate.app.security

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredAgentKey(
    val agentId: String,
    val displayName: String,
    val ownershipProvider: String,
    val publicKeyPem: String,
    val privateKeyPem: String,
    val createdAt: String,
    val updatedAt: String,
)

internal interface AgentKeyPersistence {
    fun read(agentId: String): String?
    fun readAll(): List<String>
    fun write(agentId: String, encryptedValue: String)
    fun remove(agentId: String)
}

private class SharedPreferencesAgentKeyPersistence(context: Context) : AgentKeyPersistence {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(agentId: String): String? = preferences.getString(agentId, null)
    override fun readAll(): List<String> = preferences.all.values.mapNotNull { it as? String }
    override fun write(agentId: String, encryptedValue: String) {
        check(preferences.edit().putString(agentId, encryptedValue).commit()) { "Could not persist agent key." }
    }
    override fun remove(agentId: String) {
        check(preferences.edit().remove(agentId).commit()) { "Could not remove agent key." }
    }

    private companion object {
        const val PREFERENCES_NAME = "pirate_agent_keys_v1"
    }
}

class AgentKeyStore internal constructor(
    private val persistence: AgentKeyPersistence,
    private val cipher: SessionCipher,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(record: StoredAgentKey) {
        require(record.agentId.isNotBlank()) { "Agent id is required." }
        require(record.privateKeyPem.contains("PRIVATE KEY")) { "A PKCS#8 private key is required." }
        val plaintext = json.encodeToString(StoredAgentKey.serializer(), record)
        persistence.write(record.agentId, cipher.encrypt(plaintext))
    }

    fun find(agentId: String): StoredAgentKey? = persistence.read(agentId)?.let(::decrypt)

    fun list(): List<StoredAgentKey> = persistence.readAll().mapNotNull { encrypted ->
        runCatching { decrypt(encrypted) }.getOrNull()
    }

    fun remove(agentId: String) = persistence.remove(agentId)

    private fun decrypt(encrypted: String): StoredAgentKey =
        json.decodeFromString(StoredAgentKey.serializer(), cipher.decrypt(encrypted))

    companion object {
        fun create(context: Context): AgentKeyStore = AgentKeyStore(
            persistence = SharedPreferencesAgentKeyPersistence(context.applicationContext),
            cipher = AndroidKeystoreSessionCipher(keyAlias = "pirate_agent_keys_v1"),
        )
    }
}
