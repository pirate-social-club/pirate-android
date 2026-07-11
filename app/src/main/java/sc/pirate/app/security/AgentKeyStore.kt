package sc.pirate.app.security

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName

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

@Serializable
data class AgentSigningBundle(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("public_key_pem") val publicKeyPem: String,
    @SerialName("private_key_pem") val privateKeyPem: String,
)

fun parseAgentSigningBundle(raw: String): AgentSigningBundle =
    Json { ignoreUnknownKeys = true }.decodeFromString(AgentSigningBundle.serializer(), raw)

fun agentPublicKeysMatch(left: String, right: String): Boolean {
    val leftBytes = canonicalPublicKeyBytes(left) ?: return false
    val rightBytes = canonicalPublicKeyBytes(right) ?: return false
    return leftBytes.contentEquals(rightBytes)
}

private fun canonicalPublicKeyBytes(value: String): ByteArray? {
    val normalized = value
        .replace(Regex("-----BEGIN [^-]+-----"), "")
        .replace(Regex("-----END [^-]+-----"), "")
        .replace(Regex("\\s+"), "")
    val decoded = runCatching { java.util.Base64.getUrlDecoder().decode(padBase64(normalized)) }
        .recoverCatching { java.util.Base64.getDecoder().decode(padBase64(normalized)) }
        .getOrNull()
    if (decoded != null) return if (decoded.size >= 32) decoded.takeLast(32).toByteArray() else decoded
    return normalized.takeIf { it.length == 64 && it.all(Char::isHexDigit) }
        ?.chunked(2)
        ?.map { it.toInt(16).toByte() }
        ?.toByteArray()
}

private fun padBase64(value: String): String = value + "=".repeat((4 - value.length % 4) % 4)

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

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
