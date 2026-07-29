package sc.pirate.app.video

/**
 * Applies a vote to the feed, adjusting the like count the viewer sees.
 *
 * Pure and separate from the view model because this runs twice per like — once optimistically on
 * the tap, once with the server's answer — and once more on rollback if the request fails. Getting
 * the count drift wrong in any of those paths is invisible until a viewer watches their own like
 * count creep, so it is worth testing directly.
 *
 * Only the crossing between voted and not-voted moves the count: re-applying the same value, or
 * moving between two non-positive values, leaves it alone.
 */
fun applyVideoVote(
    items: List<VideoPagerItem>,
    postId: String,
    value: Int,
): List<VideoPagerItem> = items.map { candidate ->
    if (candidate.postId != postId) return@map candidate
    val previous = candidate.viewerVote ?: 0
    if (previous == value) return@map candidate
    val delta = (if (value > 0) 1 else 0) - (if (previous > 0) 1 else 0)
    candidate.copy(
        likeCount = (candidate.likeCount + delta).coerceAtLeast(0),
        viewerVote = value,
    )
}
