package sc.pirate.app.post

import android.os.SystemClock
import sc.pirate.app.api.model.HomeFeedCommunitySummary
import sc.pirate.app.api.model.LocalizedPostResponse

data class CachedPostPreview(
    val post: LocalizedPostResponse,
    val communitySummary: HomeFeedCommunitySummary?,
    val storedAtMillis: Long,
)

class PostPreviewCache(
    private val staleTtlMillis: Long = 5 * 60_000,
    private val maxEntries: Int = 150,
) {
    private data class Entry(
        val preview: CachedPostPreview,
        val storedAtMillis: Long,
    )

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun get(postId: String, nowMillis: Long = SystemClock.elapsedRealtime()): CachedPostPreview? {
        val entry = entries[postId] ?: return null
        val ageMillis = nowMillis - entry.storedAtMillis
        if (ageMillis > staleTtlMillis) {
            entries.remove(postId)
            return null
        }
        return entry.preview
    }

    @Synchronized
    fun put(
        post: LocalizedPostResponse,
        communitySummary: HomeFeedCommunitySummary?,
        nowMillis: Long = SystemClock.elapsedRealtime(),
    ) {
        val postId = post.post.postId
        if (!entries.containsKey(postId) && entries.size >= maxEntries) {
            val oldestKey = entries.minByOrNull { it.value.storedAtMillis }?.key
            if (oldestKey != null) entries.remove(oldestKey)
        }
        entries[postId] = Entry(
            preview = CachedPostPreview(
                post = post,
                communitySummary = communitySummary,
                storedAtMillis = nowMillis,
            ),
            storedAtMillis = nowMillis,
        )
    }

    @Synchronized
    fun invalidate(postId: String) {
        entries.remove(postId)
    }
}
