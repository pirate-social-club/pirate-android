package sc.pirate.app.home

import android.os.SystemClock
import sc.pirate.app.api.model.HomeFeedResponse

data class HomeFeedCacheKey(
    val userId: String?,
    val sort: String,
    val timeRange: String?,
)

data class CachedHomeFeed(
    val feed: HomeFeedResponse,
    val fresh: Boolean,
)

class HomeFeedCache(
    private val freshTtlMillis: Long = 60_000,
    private val staleTtlMillis: Long = 5 * 60_000,
    private val maxEntries: Int = 24,
) {
    private data class Entry(
        val feed: HomeFeedResponse,
        val storedAtMillis: Long,
    )

    private val entries = mutableMapOf<HomeFeedCacheKey, Entry>()

    @Synchronized
    fun get(key: HomeFeedCacheKey, nowMillis: Long = SystemClock.elapsedRealtime()): CachedHomeFeed? {
        val entry = entries[key] ?: return null
        val ageMillis = nowMillis - entry.storedAtMillis
        if (ageMillis > staleTtlMillis) {
            entries.remove(key)
            return null
        }
        return CachedHomeFeed(
            feed = entry.feed,
            fresh = ageMillis <= freshTtlMillis,
        )
    }

    @Synchronized
    fun put(key: HomeFeedCacheKey, feed: HomeFeedResponse, nowMillis: Long = SystemClock.elapsedRealtime()) {
        if (!entries.containsKey(key) && entries.size >= maxEntries) {
            val oldestKey = entries.minByOrNull { it.value.storedAtMillis }?.key
            if (oldestKey != null) entries.remove(oldestKey)
        }
        entries[key] = Entry(feed, nowMillis)
    }
}
