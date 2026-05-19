package sc.pirate.app.post

import sc.pirate.app.api.model.CreateCommunityListingRequest
import sc.pirate.app.api.model.CreateLiveRoomRequest
import sc.pirate.app.api.model.LiveRoomPerformerAllocationInput
import sc.pirate.app.api.model.LiveRoomSetlistInput
import sc.pirate.app.api.model.LiveRoomSetlistItemInput
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val POST_COMPOSER_TITLE_MAX_LENGTH = 300

enum class PostComposerMode(val apiValue: String) {
    Text("text"),
    Link("link"),
    Live("live"),
}

enum class LiveRoomKind(val apiValue: String) {
    Solo("solo"),
    Duet("duet"),
}

enum class LiveAccessMode(val apiValue: String) {
    Free("free"),
    Gated("gated"),
    Paid("paid"),
}

enum class LiveVisibility(val apiValue: String) {
    Public("public"),
    Unlisted("unlisted"),
}

enum class LiveSetlistItemKind(val apiValue: String) {
    Original("original"),
    Cover("cover"),
    Remix("remix"),
    DjPlayback("dj_playback"),
    Unknown("unknown"),
}

data class LivePerformerAllocationState(
    val userId: String = "",
    val role: String,
    val sharePct: Int,
)

data class LiveSetlistItemState(
    val titleText: String = "",
    val artistText: String = "",
    val declaredTrackId: String = "",
    val performanceKind: LiveSetlistItemKind = LiveSetlistItemKind.Unknown,
)

data class LiveCoverUploadState(
    val mediaRef: String,
    val label: String,
)

data class LiveComposerState(
    val roomKind: LiveRoomKind = LiveRoomKind.Solo,
    val accessMode: LiveAccessMode = LiveAccessMode.Free,
    val visibility: LiveVisibility = LiveVisibility.Public,
    val scheduleForLater: Boolean = false,
    val scheduleAt: String = "",
    val guestUserId: String = "",
    val storeUrl: String = "",
    val storeLabel: String = "",
    val coverUpload: LiveCoverUploadState? = null,
    val coverLabel: String = "",
    val setlistItems: List<LiveSetlistItemState> = emptyList(),
    val setlistStatus: String = "ready",
    val performerAllocations: List<LivePerformerAllocationState> = listOf(
        LivePerformerAllocationState(role = "host", sharePct = 100),
    ),
    val paidPriceUsd: String = "",
    val regionalPricingEnabled: Boolean = false,
)

enum class PostComposerStep {
    Write,
    Settings,
    Publish,
}

data class PostComposerDraftValidation(
    val canSubmit: Boolean,
    val errorMessage: String? = null,
)

fun normalizePostComposerTitleInput(value: String): String {
    return value.take(POST_COMPOSER_TITLE_MAX_LENGTH)
}

fun getNextPostComposerStep(
    current: PostComposerStep,
    draftValidation: PostComposerDraftValidation,
): PostComposerStep {
    return when (current) {
        PostComposerStep.Write -> if (draftValidation.canSubmit) PostComposerStep.Settings else PostComposerStep.Write
        PostComposerStep.Settings -> PostComposerStep.Publish
        PostComposerStep.Publish -> PostComposerStep.Publish
    }
}

fun getPreviousPostComposerStep(current: PostComposerStep): PostComposerStep? {
    return when (current) {
        PostComposerStep.Write -> null
        PostComposerStep.Settings -> PostComposerStep.Write
        PostComposerStep.Publish -> PostComposerStep.Settings
    }
}

fun canAdvancePostComposerStep(
    current: PostComposerStep,
    draftValidation: PostComposerDraftValidation,
): Boolean {
    return when (current) {
        PostComposerStep.Write -> draftValidation.canSubmit
        PostComposerStep.Settings -> true
        PostComposerStep.Publish -> draftValidation.canSubmit
    }
}

fun validatePostComposerDraft(
    mode: PostComposerMode,
    title: String,
    linkUrl: String,
    live: LiveComposerState = LiveComposerState(),
): PostComposerDraftValidation {
    return when (mode) {
        PostComposerMode.Text -> {
            if (title.isBlank()) {
                PostComposerDraftValidation(
                    canSubmit = false,
                    errorMessage = "Add a title before posting.",
                )
            } else {
                PostComposerDraftValidation(canSubmit = true)
            }
        }

        PostComposerMode.Link -> {
            if (linkUrl.isBlank()) {
                PostComposerDraftValidation(
                    canSubmit = false,
                    errorMessage = "Enter a link before posting.",
                )
            } else if (normalizeHttpUrl(linkUrl) == null) {
                PostComposerDraftValidation(
                    canSubmit = false,
                    errorMessage = "Enter a valid http or https link.",
                )
            } else {
                PostComposerDraftValidation(canSubmit = true)
            }
        }

        PostComposerMode.Live -> validateLiveComposerDraft(title, live)
    }
}

fun validateLiveComposerDraft(
    title: String,
    live: LiveComposerState,
): PostComposerDraftValidation {
    if (title.isBlank()) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Add a live room title before publishing.",
        )
    }
    if (live.scheduleForLater && parseLiveScheduleEpochSeconds(live.scheduleAt) == null) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Choose a valid live start time.",
        )
    }
    if (live.roomKind == LiveRoomKind.Duet && live.guestUserId.isBlank()) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Add a guest performer for duet live rooms.",
        )
    }
    if (live.accessMode == LiveAccessMode.Paid && usdToCents(live.paidPriceUsd) == null) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Enter a valid ticket price.",
        )
    }
    return PostComposerDraftValidation(canSubmit = true)
}

fun buildLiveRoomRequest(
    coverRef: String? = null,
    description: String,
    hostUserId: String,
    live: LiveComposerState,
    resolvedGuestUserId: String? = null,
    title: String,
): CreateLiveRoomRequest {
    val guestUserId = if (live.roomKind == LiveRoomKind.Duet) {
        resolvedGuestUserId?.trim()?.takeIf { it.isNotBlank() }
            ?: live.guestUserId.trim().takeIf { it.isNotBlank() }
    } else {
        null
    }
    return CreateLiveRoomRequest(
        title = title.trim(),
        description = description.trim().ifBlank { null },
        roomKind = live.roomKind.apiValue,
        accessMode = live.accessMode.apiValue,
        visibility = live.visibility.apiValue,
        guestUser = guestUserId,
        eventStartAt = if (live.scheduleForLater) parseLiveScheduleEpochSeconds(live.scheduleAt) else null,
        coverRef = coverRef?.trim()?.takeIf { it.isNotBlank() },
        storeUrl = live.storeUrl.trim().takeIf { it.isNotBlank() },
        storeLabel = live.storeLabel.trim().takeIf { it.isNotBlank() },
        performerAllocations = livePerformerAllocations(live, hostUserId, guestUserId),
        setlist = LiveRoomSetlistInput(
            status = live.setlistStatus,
            items = live.setlistItems.mapNotNull(::liveSetlistItemInput),
        ),
    )
}

fun buildLiveRoomListingRequest(
    liveRoomId: String? = null,
    paidLiveRoomPriceUsd: String,
    pricingPolicyRegionalPricingEnabled: Boolean = false,
    regionalPricingEnabled: Boolean = false,
): CreateCommunityListingRequest? {
    val priceCents = usdToCents(paidLiveRoomPriceUsd) ?: return null
    return CreateCommunityListingRequest(
        liveRoom = liveRoomId,
        priceCents = priceCents,
        regionalPricingEnabled = pricingPolicyRegionalPricingEnabled && regionalPricingEnabled,
        status = "active",
    )
}

fun parseLiveScheduleEpochSeconds(value: String): Long? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    return runCatching { Instant.parse(trimmed).epochSecond }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toEpochSecond()
        }.getOrNull()
}

fun usdToCents(value: String): Int? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val amount = runCatching { BigDecimal(trimmed) }.getOrNull() ?: return null
    if (amount <= BigDecimal.ZERO) return null
    return amount
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .takeIf { it <= BigDecimal(Int.MAX_VALUE) }
        ?.toInt()
}

fun normalizeLiveRoomGuestHandle(value: String): String =
    value.trim().replace(Regex("^@+"), "").replace(Regex("^/?u/"), "")

fun isPirateUserId(value: String): Boolean = value.trim().startsWith("usr_")

private fun livePerformerAllocations(
    live: LiveComposerState,
    hostUserId: String,
    guestUserId: String?,
): List<LiveRoomPerformerAllocationInput> {
    if (live.accessMode != LiveAccessMode.Paid) return emptyList()
    return live.performerAllocations.map { allocation ->
        LiveRoomPerformerAllocationInput(
            user = if (allocation.role == "host") hostUserId else guestUserId,
            role = allocation.role,
            shareBps = allocation.sharePct * 100,
        )
    }
}

private fun liveSetlistItemInput(item: LiveSetlistItemState): LiveRoomSetlistItemInput? {
    val title = item.titleText.trim()
    if (title.isBlank()) return null
    val declaredTrackId = item.declaredTrackId.trim()
    return LiveRoomSetlistItemInput(
        songArtifactBundle = declaredTrackId.takeIf { it.startsWith("sab_") },
        sourceAssetRef = declaredTrackId.takeIf { it.startsWith("story:asset:") },
        title = title,
        artist = item.artistText.trim().takeIf { it.isNotBlank() },
        rightsBasis = when (item.performanceKind) {
            LiveSetlistItemKind.Original -> "original"
            LiveSetlistItemKind.Cover -> "cover"
            else -> "unknown"
        },
        rightsStatus = "pending",
    )
}

fun isValidHttpUrl(value: String): Boolean = normalizeHttpUrl(value) != null

fun normalizeHttpUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null

    fun parse(candidate: String): String? {
        return try {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase()
            if ((scheme == "http" || scheme == "https") && uri.host != null) {
                uri.toURL().toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    parse(trimmed)?.let { return it }

    if (trimmed.any { it.isWhitespace() }) return null

    val pathStart = listOf(
        trimmed.indexOf('/'),
        trimmed.indexOf('?'),
        trimmed.indexOf('#'),
    ).filter { it >= 0 }.minOrNull() ?: -1
    val authorityCandidate = if (pathStart == -1) trimmed else trimmed.substring(0, pathStart)
    val colonIndex = authorityCandidate.indexOf(':')
    if (colonIndex > 0) {
        val hostCandidate = authorityCandidate.substring(0, colonIndex).lowercase()
        val portLikeHost = hostCandidate.contains(".")
            || hostCandidate == "localhost"
            || ipv4Regex.matches(hostCandidate)
            || hostCandidate.startsWith("[")
        if (!portLikeHost) return null
    }

    val normalizedTrimmed = trimmed.lowercase()
    val schemelessWebUrl = trimmed.contains(".")
        || normalizedTrimmed.startsWith("localhost")
        || ipv4WithOptionalPortRegex.matches(trimmed)
        || ipv6WithOptionalPortRegex.matches(trimmed)

    if (!schemelessWebUrl) return null

    return parse("https://$trimmed")
}

private val ipv4Regex = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
private val ipv4WithOptionalPortRegex = Regex("""\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?(?:[/?#].*)?""")
private val ipv6WithOptionalPortRegex = Regex("""\[[\da-fA-F:]+](?::\d+)?(?:[/?#].*)?""")
