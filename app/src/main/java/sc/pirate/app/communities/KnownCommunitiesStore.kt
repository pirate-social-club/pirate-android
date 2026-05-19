package sc.pirate.app.communities

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME = "known_communities"
private const val PREFS_KEY = "items"
private const val MAX_KNOWN_COMMUNITIES = 50

@Serializable
data class KnownCommunity(
    @SerialName("avatar_ref") val avatarRef: String? = null,
    @SerialName("community_id") val communityId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("route_slug") val routeSlug: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

class KnownCommunitiesStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun remember(
        communityId: String,
        displayName: String,
        avatarRef: String? = null,
        routeSlug: String? = null,
    ) {
        val id = communityId.trim()
        val name = displayName.trim()
        if (id.isBlank() || name.isBlank()) return

        val existing = getRecent().firstOrNull { it.communityId == id }
        val next = KnownCommunity(
            avatarRef = avatarRef?.trim()?.takeIf { it.isNotBlank() } ?: existing?.avatarRef,
            communityId = id,
            displayName = name,
            routeSlug = routeSlug?.trim()?.takeIf { it.isNotBlank() } ?: existing?.routeSlug,
            updatedAt = System.currentTimeMillis(),
        )
        val merged = (listOf(next) + getRecent().filterNot { it.communityId == id })
            .sortedByDescending { it.updatedAt }
            .take(MAX_KNOWN_COMMUNITIES)
        prefs.edit().putString(PREFS_KEY, json.encodeToString(merged)).apply()
    }

    fun getRecent(): List<KnownCommunity> {
        val raw = prefs.getString(PREFS_KEY, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<KnownCommunity>>(raw)
                .filter { it.communityId.isNotBlank() && it.displayName.isNotBlank() }
                .sortedByDescending { it.updatedAt }
                .take(MAX_KNOWN_COMMUNITIES)
        }.getOrDefault(emptyList())
    }

    fun clear() {
        prefs.edit().remove(PREFS_KEY).apply()
    }
}
