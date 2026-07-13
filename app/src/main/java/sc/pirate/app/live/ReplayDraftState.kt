package sc.pirate.app.live

import sc.pirate.app.api.model.LiveRoomReplayDraft
import sc.pirate.app.api.model.UpdateLiveRoomReplayDraftRequest

data class ReplayDraftValidation(
    val canPublish: Boolean,
    val message: String? = null,
)

fun validateReplayDraftForFreePublish(
    draft: LiveRoomReplayDraft?,
    title: String,
    caption: String,
): ReplayDraftValidation {
    if (draft?.status != "ready" || draft.replayStatus != "review_pending") {
        return ReplayDraftValidation(false, "The recording is not ready to publish yet.")
    }
    if (title.trim().isBlank()) return ReplayDraftValidation(false, "Add a replay title.")
    if (title.trim().length > 140) return ReplayDraftValidation(false, "Keep the title to 140 characters or fewer.")
    if (caption.trim().length > 2000) return ReplayDraftValidation(false, "Keep the caption to 2,000 characters or fewer.")
    val allocations = draft.replayAsset?.allocations.orEmpty()
    if (allocations.isEmpty() || allocations.sumOf { it.shareBps } != 10_000) {
        return ReplayDraftValidation(false, "The replay royalty split must total 100%.")
    }
    if (allocations.any { it.participantUser.isNullOrBlank() && it.externalPartyRef.isNullOrBlank() }) {
        return ReplayDraftValidation(false, "Every replay split needs a recipient.")
    }
    return ReplayDraftValidation(true)
}

fun buildFreeReplayDraftUpdate(title: String, caption: String): UpdateLiveRoomReplayDraftRequest =
    UpdateLiveRoomReplayDraftRequest(
        title = title.trim(),
        caption = caption.trim().ifBlank { null },
        accessMode = "free",
    )
