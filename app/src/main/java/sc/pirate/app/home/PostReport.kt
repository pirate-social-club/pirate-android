package sc.pirate.app.home

import sc.pirate.app.api.CreateUserReportRequest

private const val CHILD_SAFETY_NOTE_PREFIX = "Child safety concern"

enum class PostReportReason(
    val label: String,
    val apiReasonCode: String,
) {
    ChildSafety("Child safety concern", "sexual_content"),
    Harassment("Harassment or hate", "harassment"),
    SexualContent("Sexual content", "sexual_content"),
    GraphicContent("Graphic content", "graphic_content"),
    Spam("Spam", "spam"),
    Misleading("Misleading", "misleading"),
    Other("Other", "other"),
}

data class PostReportDraft(
    val reason: PostReportReason = PostReportReason.ChildSafety,
    val note: String = "",
)

fun buildPostReportRequest(draft: PostReportDraft): CreateUserReportRequest {
    val userNote = draft.note.trim().takeIf { it.isNotEmpty() }
    val note = when (draft.reason) {
        PostReportReason.ChildSafety -> {
            if (userNote == null) CHILD_SAFETY_NOTE_PREFIX else "$CHILD_SAFETY_NOTE_PREFIX: $userNote"
        }
        else -> userNote
    }

    return CreateUserReportRequest(
        reasonCode = draft.reason.apiReasonCode,
        note = note,
    )
}
