package sc.pirate.app.live

import java.time.Duration
import java.time.Instant
import java.util.Locale
import sc.pirate.app.api.model.CommunityListing
import sc.pirate.app.api.model.CommunityPurchase
import sc.pirate.app.api.model.LiveRoomAccessResponse
import sc.pirate.app.api.model.LiveRoomSetlistItem
import sc.pirate.app.shared.resolvePublicMediaSrc

enum class LiveRoomAccessState {
    Allowed,
    GateRequired,
    PurchaseRequired,
    Waiting,
    MissingListing,
    Ended,
}

sealed interface LiveRoomUiState {
    data class CanWatch(val cta: String = "Watch live") : LiveRoomUiState
    data class CanWatchReplay(val cta: String = "Watch replay") : LiveRoomUiState
    data class NeedsAccess(val cta: String = "Verify access") : LiveRoomUiState
    data class NeedsTicket(val cta: String) : LiveRoomUiState
    data object HasTicket : LiveRoomUiState
    data class CanRsvp(val cta: String = "RSVP") : LiveRoomUiState
    data object Rsvped : LiveRoomUiState
    data class NeedsVerification(val cta: String = "Verify to attend") : LiveRoomUiState
    data object TicketsUnavailable : LiveRoomUiState
    data object ReplayProcessing : LiveRoomUiState
    data object Scheduled : LiveRoomUiState
    data object Ended : LiveRoomUiState
    data object Canceled : LiveRoomUiState
}

enum class LiveRoomProducerRole {
    Host,
    Guest,
}

data class LiveRoomPresentation(
    val title: String,
    val description: String?,
    val status: String,
    val accessMode: String,
    val visibility: String?,
    val accessState: LiveRoomAccessState?,
    val replayStatus: String,
    val coverSrc: String?,
    val priceLabel: String?,
    val hasEntitlement: Boolean,
    val producerRole: LiveRoomProducerRole?,
    val guestInviteStatus: String?,
    val timeLabel: String?,
    val statusLabel: String?,
    val accessLabel: String?,
    val descriptionLabel: String?,
    val uiState: LiveRoomUiState,
    val participantLabel: String?,
    val setlistPreview: List<LiveRoomSetlistItem>,
    val canInlineAttachViewer: Boolean,
)

data class LiveRoomPresentationInput(
    val fallbackTitle: String,
    val access: LiveRoomAccessResponse?,
    val listing: CommunityListing?,
    val purchase: CommunityPurchase?,
    val publicStatus: String?,
    val publicAccessMode: String?,
    val fallbackCoverRef: String?,
    val viewerUserId: String?,
    val postAuthorUserId: String?,
    val liveRoomId: String?,
    val rsvpState: String? = null,
    val canRsvp: Boolean = false,
    val ageProofRequired: Boolean = false,
)

fun buildLiveRoomPresentation(input: LiveRoomPresentationInput): LiveRoomPresentation {
    val room = input.access?.room
    val liveAccess = input.access?.access
    val title = room?.title?.takeIf { it.isNotBlank() } ?: input.fallbackTitle
    val status = room?.status ?: input.publicStatus ?: "scheduled"
    val accessMode = liveAccess?.accessMode ?: room?.accessMode ?: input.publicAccessMode ?: input.listing?.let { "paid" } ?: "free"
    val visibility = room?.visibility ?: liveAccess?.visibility
    val viewerOwnsPost = sameLiveRoomUserId(input.viewerUserId, input.postAuthorUserId)
    val hasEntitlement = accessMode != "paid" || liveAccess?.allowed == true || input.purchase != null || viewerOwnsPost
    val accessState = deriveLiveRoomAccessState(
        decisionReason = liveAccess?.decisionReason,
        allowed = liveAccess?.allowed,
        hasListing = input.listing != null || liveAccess?.listing != null,
    )
    val priceLabel = input.listing?.priceCents?.takeIf { it > 0 }?.let(::formatUsdCents)
    val producerRole = when {
        sameLiveRoomUserId(input.viewerUserId, room?.hostUser) ||
            (input.liveRoomId != null && sameLiveRoomUserId(input.viewerUserId, input.postAuthorUserId)) -> LiveRoomProducerRole.Host
        sameLiveRoomUserId(input.viewerUserId, room?.guestUser) -> LiveRoomProducerRole.Guest
        else -> null
    }
    val replayStatus = when (room?.replayStatus) {
        "published", "ready" -> "ready"
        "review_pending", "processing" -> "processing"
        "failed" -> "failed"
        else -> "none"
    }
    val uiState = deriveLiveRoomUi(
        status = status,
        accessMode = accessMode,
        accessState = accessState,
        replayStatus = replayStatus,
        hasEntitlement = hasEntitlement,
        producerRole = producerRole,
        priceLabel = priceLabel,
        rsvpState = input.rsvpState,
        canRsvp = input.canRsvp,
        ageProofRequired = input.ageProofRequired,
    )
    val time = timeLabel(
        status = status,
        startsAt = room?.eventStartAt,
        endedAt = room?.endedAt,
    )
    val statusLabel = when {
        status == "live" && producerRole != null -> "Live now"
        status == "live" -> null
        status == "ended" -> null
        status == "canceled" -> "Canceled"
        else -> time
    }
    val accessLabel = when (uiState) {
        LiveRoomUiState.HasTicket,
        LiveRoomUiState.Rsvped -> "You're going"
        LiveRoomUiState.ReplayProcessing -> "Replay processing"
        LiveRoomUiState.TicketsUnavailable -> "Tickets unavailable"
        is LiveRoomUiState.NeedsTicket -> uiState.cta
        is LiveRoomUiState.NeedsAccess -> "Gated access"
        else -> if (accessMode == "free" && status != "ended" && status != "canceled") "Free" else null
    }
    val descriptionLabel = when {
        producerRole == LiveRoomProducerRole.Guest && input.access?.access?.guestInviteStatus == "pending" ->
            "Accept the producer invite before broadcasting."
        producerRole == LiveRoomProducerRole.Guest && input.access?.access?.guestInviteStatus == "revoked" ->
            "This producer invite has been revoked."
        status == "ended" && replayStatus == "ready" -> "Replay is ready."
        status == "ended" -> "This room has ended."
        status == "canceled" -> "This room was canceled."
        uiState is LiveRoomUiState.NeedsVerification -> "18+ proof required before you can watch."
        uiState is LiveRoomUiState.NeedsTicket -> priceLabel?.let { "$it ticket required to watch." } ?: "Ticket required to watch."
        uiState is LiveRoomUiState.NeedsAccess -> "Community access is required before you can watch."
        status == "live" && hasEntitlement -> "Watch the concert from this page."
        else -> room?.description
    }

    return LiveRoomPresentation(
        title = title,
        description = room?.description,
        status = status,
        accessMode = accessMode,
        visibility = visibility,
        accessState = accessState,
        replayStatus = replayStatus,
        coverSrc = if (input.ageProofRequired) {
            null
        } else {
            resolvePublicMediaSrc(room?.coverRef ?: input.fallbackCoverRef)
        },
        priceLabel = priceLabel,
        hasEntitlement = hasEntitlement,
        producerRole = producerRole,
        guestInviteStatus = liveAccess?.guestInviteStatus,
        timeLabel = time,
        statusLabel = statusLabel,
        accessLabel = accessLabel,
        descriptionLabel = descriptionLabel,
        uiState = uiState,
        participantLabel = participantsLabel(room?.hostUser, room?.guestUser),
        setlistPreview = room?.setlist?.items.orEmpty().take(3),
        canInlineAttachViewer = !input.ageProofRequired &&
            status == "live" &&
            accessMode == "free" &&
            visibility == "public" &&
            liveAccess?.allowed == true &&
            producerRole == null,
    )
}

fun sameLiveRoomUserId(left: String?, right: String?): Boolean {
    if (left.isNullOrBlank() || right.isNullOrBlank()) return false
    fun normalize(value: String) = value.replace(Regex("^(usr_)+"), "")
    return left == right || normalize(left) == normalize(right)
}

private fun deriveLiveRoomAccessState(
    decisionReason: String?,
    allowed: Boolean?,
    hasListing: Boolean,
): LiveRoomAccessState? =
    when (decisionReason) {
        "purchase_required" -> if (hasListing) LiveRoomAccessState.PurchaseRequired else LiveRoomAccessState.MissingListing
        "membership_required" -> LiveRoomAccessState.GateRequired
        "ended", "canceled" -> LiveRoomAccessState.Ended
        "not_live" -> LiveRoomAccessState.Waiting
        else -> if (allowed == true) LiveRoomAccessState.Allowed else null
    }

private fun deriveLiveRoomUi(
    status: String,
    accessMode: String,
    accessState: LiveRoomAccessState?,
    replayStatus: String,
    hasEntitlement: Boolean,
    producerRole: LiveRoomProducerRole?,
    priceLabel: String?,
    rsvpState: String?,
    canRsvp: Boolean,
    ageProofRequired: Boolean,
): LiveRoomUiState {
    if (status == "canceled") return LiveRoomUiState.Canceled
    if (status == "ended" || accessState == LiveRoomAccessState.Ended) {
        if (replayStatus == "ready") return LiveRoomUiState.CanWatchReplay()
        if (replayStatus == "processing") return LiveRoomUiState.ReplayProcessing
        return LiveRoomUiState.Ended
    }
    if (ageProofRequired) return LiveRoomUiState.NeedsVerification()
    if (accessState == LiveRoomAccessState.MissingListing) return LiveRoomUiState.TicketsUnavailable
    if (accessState == LiveRoomAccessState.GateRequired) return LiveRoomUiState.NeedsAccess()
    if (accessState == LiveRoomAccessState.PurchaseRequired || (accessMode == "paid" && !hasEntitlement)) {
        return LiveRoomUiState.NeedsTicket(priceLabel?.let { "Get ticket $it" } ?: "Get ticket")
    }
    if (accessMode == "paid" && hasEntitlement) {
        if (status == "live") return LiveRoomUiState.CanWatch()
        return LiveRoomUiState.HasTicket
    }
    if (
        status == "scheduled" &&
        accessMode == "free" &&
        producerRole == null &&
        (accessState == LiveRoomAccessState.Waiting || accessState == LiveRoomAccessState.Allowed || accessState == null)
    ) {
        if (rsvpState == "going") return LiveRoomUiState.Rsvped
        if (canRsvp) return LiveRoomUiState.CanRsvp()
    }
    if (status == "live" && accessState == null && producerRole == null) return LiveRoomUiState.Scheduled
    if (status == "live") return LiveRoomUiState.CanWatch()
    return LiveRoomUiState.Scheduled
}

private fun timeLabel(status: String, startsAt: Long?, endedAt: Long?): String? =
    when (status) {
        "ended" -> endedAt?.let { "Ended ${relativeEpochSecondsLabel(it)} ago" }
        "canceled" -> "Canceled"
        "live" -> null
        else -> startsAt?.let { "Starts ${relativeEpochSecondsLabel(it)}" }
    }

private fun relativeEpochSecondsLabel(epochSeconds: Long): String {
    val target = Instant.ofEpochSecond(epochSeconds)
    val now = Instant.now()
    val future = target.isAfter(now)
    val duration = Duration.between(if (future) now else target, if (future) target else now)
    val value = when {
        duration.toDays() >= 1 -> "${duration.toDays()}d"
        duration.toHours() >= 1 -> "${duration.toHours()}h"
        duration.toMinutes() >= 1 -> "${duration.toMinutes()}m"
        else -> "now"
    }
    return if (future && value != "now") "in $value" else value
}

private fun participantsLabel(hostUser: String?, guestUser: String?): String? {
    val guest = guestUser?.takeIf { it.isNotBlank() } ?: return null
    val host = hostUser?.takeIf { it.isNotBlank() } ?: "Host"
    return "${shortUserLabel(host)} with ${shortUserLabel(guest)}"
}

private fun shortUserLabel(userId: String): String =
    userId.removePrefix("usr_").take(8).let { if (it.isBlank()) "user" else "u/$it" }

private fun formatUsdCents(cents: Int): String = "$" + String.format(Locale.US, "%.2f", cents / 100.0)
