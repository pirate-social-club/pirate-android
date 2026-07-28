package sc.pirate.app.video

/**
 * The bookkeeping half of [VideoPlayerPool], with no player attached.
 *
 * Which page owns which player, and which page loses its player when a new one is needed, is the
 * only part of pooling that can actually be wrong — the ExoPlayer plumbing around it is
 * mechanical. Keeping it here means it can be tested without a device or a Robolectric runtime.
 *
 * Order is least-recently-touched first, so eviction always takes the page furthest from the
 * viewer's attention rather than the one that merely arrived first.
 */
class VideoLeaseTable(private val capacity: Int) {
    init {
        require(capacity >= 1) { "A pool needs at least one lease, got $capacity" }
    }

    private val order = ArrayDeque<String>()

    /** Current holders, coldest first. */
    val keys: List<String> get() = order.toList()

    fun holds(key: String): Boolean = order.contains(key)

    /**
     * Marks [key] most-recently-used if it is already held.
     *
     * Returns true when the caller can reuse the existing binding untouched, false when it must
     * go on to [admit].
     */
    fun touch(key: String): Boolean {
        if (!order.remove(key)) return false
        order.addLast(key)
        return true
    }

    sealed interface Admission {
        /** No lease was free; the player bound to [evictedKey] should be rebound to the new page. */
        data class Reuse(val evictedKey: String) : Admission
        /** Under capacity: the caller should construct a player. */
        data object Create : Admission
    }

    /**
     * Admits [key], evicting the coldest holder if the table is full. Call only after [touch]
     * returned false, otherwise a held page would be admitted twice.
     */
    fun admit(key: String): Admission {
        val admission = if (order.size >= capacity) {
            Admission.Reuse(order.removeFirst())
        } else {
            Admission.Create
        }
        order.addLast(key)
        return admission
    }

    fun clear() {
        order.clear()
    }
}
