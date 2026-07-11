package sc.pirate.app.security

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.bouncycastle.jce.provider.BouncyCastleProvider

private const val CANONICAL_VERSION = "pirate-agent-action-proof-v2"
private const val SIGNATURE_VERSION = "pirate-agent-action-signature-v2"

@Serializable
data class AgentActionProof(
    val nonce: String,
    @SerialName("signed_at") val signedAt: Long,
    @SerialName("canonical_request_hash") val canonicalRequestHash: String,
    val signature: String,
)

object AgentActionProofSigner {
    private val json = Json { explicitNulls = true }
    private val provider = BouncyCastleProvider()

    fun canonicalizeRequest(method: String, url: String, body: String? = null): String {
        val uri = URI(url.trim())
        require(uri.isAbsolute && !uri.host.isNullOrBlank()) { "Agent action proof URL must be absolute." }
        val origin = buildString {
            append(uri.scheme.lowercase())
            append("://")
            append(uri.host.lowercase())
            if (uri.port >= 0 && uri.port != defaultPort(uri.scheme)) append(":${uri.port}")
        }
        val path = uri.rawPath.orEmpty().ifBlank { "/" }.let {
            if (it == "/") it else it.trimEnd('/')
        }
        val query = canonicalQuery(uri.rawQuery)
        val canonicalBody = body?.takeIf { it.isNotEmpty() }?.let {
            json.encodeToString(JsonElement.serializer(), sortJson(json.parseToJsonElement(it)))
        }.orEmpty()
        return listOf(CANONICAL_VERSION, method.trim().uppercase(), origin, path, query, canonicalBody).joinToString("\n")
    }

    fun requestHash(method: String, url: String, body: String? = null): String =
        MessageDigest.getInstance("SHA-256")
            .digest(canonicalizeRequest(method, url, body).toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun signaturePayload(nonce: String, signedAt: Long, canonicalRequestHash: String): String =
        listOf(SIGNATURE_VERSION, nonce.trim(), signedAt.toString(), canonicalRequestHash.trim()).joinToString("\n")

    fun sign(
        method: String,
        url: String,
        body: String?,
        privateKeyPem: String,
        signedAt: Long = System.currentTimeMillis() / 1000,
        nonce: String = UUID.randomUUID().toString(),
    ): AgentActionProof {
        val hash = requestHash(method, url, body)
        val keyBytes = decodePem(privateKeyPem)
        val privateKey = KeyFactory.getInstance("Ed25519", provider)
            .generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val signature = Signature.getInstance("Ed25519", provider).apply {
            initSign(privateKey)
            update(signaturePayload(nonce, signedAt, hash).toByteArray(StandardCharsets.UTF_8))
        }.sign()
        return AgentActionProof(
            nonce = nonce,
            signedAt = signedAt,
            canonicalRequestHash = hash,
            signature = Base64.getEncoder().encodeToString(signature),
        )
    }

    private fun sortJson(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.entries.sortedBy { it.key }.associate { it.key to sortJson(it.value) })
        is JsonArray -> JsonArray(value.map(::sortJson))
        else -> value
    }

    private fun canonicalQuery(rawQuery: String?): String = rawQuery.orEmpty()
        .takeIf { it.isNotEmpty() }
        ?.split('&')
        ?.map { part ->
            val pieces = part.split('=', limit = 2)
            decodeQuery(pieces[0]) to decodeQuery(pieces.getOrElse(1) { "" })
        }
        ?.sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second }))
        ?.joinToString("&") { (key, value) -> "${encodeComponent(key)}=${encodeComponent(value)}" }
        .orEmpty()

    private fun decodeQuery(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.toString())

    private fun encodeComponent(value: String): String = buildString {
        value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val char = unsigned.toChar()
            if (unsigned in 'a'.code..'z'.code || unsigned in 'A'.code..'Z'.code ||
                unsigned in '0'.code..'9'.code || char in "-_.!~*'()"
            ) append(char)
            else append("%%%02X".format(unsigned))
        }
    }

    private fun decodePem(value: String): ByteArray = Base64.getDecoder().decode(
        value.replace(Regex("-----BEGIN [^-]+-----"), "")
            .replace(Regex("-----END [^-]+-----"), "")
            .replace(Regex("\\s+"), ""),
    )

    private fun defaultPort(scheme: String): Int = if (scheme.equals("https", true)) 443 else 80
}
