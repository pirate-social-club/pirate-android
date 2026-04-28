package sc.pirate.app.api

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object SessionExpiry {

    private val json = Json { ignoreUnknownKeys = true }

    fun accessTokenExpiryMs(token: String): Long? {
        val segment = token.split(".").getOrNull(1) ?: return null
        val decoded = try {
            val normalized = segment.replace("-", "+").replace("_", "/")
            val padded = normalized.padEnd((normalized.length + 3) / 4 * 4, '=')
            String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val exp = try {
            json.parseToJsonElement(decoded).jsonObject["exp"]?.jsonPrimitive?.longOrNull
        } catch (_: Exception) {
            null
        } ?: return null
        return exp * 1000
    }

    fun isExpired(token: String): Boolean {
        val expiry = accessTokenExpiryMs(token) ?: return false
        return expiry <= System.currentTimeMillis()
    }
}
