package sc.pirate.app.safety

import sc.pirate.app.api.model.CommentListItem
import sc.pirate.app.api.model.HomeFeedResponse
import sc.pirate.app.api.model.LocalizedPostResponse

fun HomeFeedResponse.withoutBlockedAuthors(blockedUserIds: Set<String>): HomeFeedResponse =
    copy(items = items.filter { isAuthorVisible(it.post.post.authorUserId, blockedUserIds) })

fun List<LocalizedPostResponse>.withoutBlockedPostAuthors(blockedUserIds: Set<String>): List<LocalizedPostResponse> =
    filter { isAuthorVisible(it.post.authorUserId, blockedUserIds) }

fun List<CommentListItem>.withoutBlockedCommentAuthors(blockedUserIds: Set<String>): List<CommentListItem> =
    filter { isAuthorVisible(it.comment.authorUserId, blockedUserIds) }

internal fun isAuthorVisible(authorUserId: String?, blockedUserIds: Set<String>): Boolean {
    val author = authorUserId?.let(::normalizePirateUserId)?.takeIf(String::isNotBlank) ?: return true
    return author !in blockedUserIds
}
