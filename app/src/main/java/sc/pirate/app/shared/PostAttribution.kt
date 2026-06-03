package sc.pirate.app.shared

import sc.pirate.app.api.model.LocalizedPostResponse
import sc.pirate.app.api.model.PostDerivativeSource

fun videoUsesSongAttributionLabel(post: LocalizedPostResponse): String? {
    if (post.post.postType != "video") return null
    val sources = post.derivativeSources
        ?.filter { it.relationshipType == "references_song" }
        .orEmpty()
    if (sources.isEmpty()) return null

    val first = sources.first()
    val title = first.title.trimOrNull() ?: "Untitled song"
    val artist = first.creatorLabel()
    val suffix = if (sources.size > 1) " +${sources.size - 1}" else ""
    return if (artist != null) {
        "Uses $title by $artist$suffix"
    } else {
        "Uses $title$suffix"
    }
}

private fun PostDerivativeSource.creatorLabel(): String? =
    creatorHandle.trimOrNull() ?: creatorDisplayName.trimOrNull()

private fun String?.trimOrNull(): String? =
    this?.trim()?.takeIf { it.isNotBlank() }
