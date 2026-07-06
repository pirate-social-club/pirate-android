package sc.pirate.app.post

import sc.pirate.app.api.model.CreateCommunityListingRequest
import sc.pirate.app.api.model.CreatePostRequest
import sc.pirate.app.api.model.CreateLiveRoomRequest
import sc.pirate.app.api.model.LiveRoomPerformerAllocationInput
import sc.pirate.app.api.model.LiveRoomSetlistInput
import sc.pirate.app.api.model.LiveRoomSetlistItemInput
import sc.pirate.app.api.model.PostMediaRef
import sc.pirate.app.api.model.SongArtifactBundle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
    Image("image"),
    Video("video"),
    Link("link"),
    Song("song"),
    Live("live"),
}

enum class SongMode(val apiValue: String) {
    Original("original"),
    Remix("remix"),
}

enum class AssetLicensePreset(val apiValue: String) {
    NonCommercial("non-commercial"),
    CommercialUse("commercial-use"),
    CommercialRemix("commercial-remix"),
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

data class SongComposerState(
    val songTitle: String = "",
    val lyrics: String = "",
    val geniusAnnotationsUrl: String = "",
    val previewStartSeconds: String = "",
    val primaryAudioLabel: String = "",
    val coverLabel: String = "",
    val canvasVideoLabel: String = "",
    val instrumentalAudioLabel: String = "",
    val vocalAudioLabel: String = "",
    val songMode: SongMode = SongMode.Original,
    val licensePreset: AssetLicensePreset = AssetLicensePreset.NonCommercial,
    val commercialRevSharePct: String = "",
    val paidSongPriceUsd: String = "",
    val regionalPricingEnabled: Boolean = false,
    val pendingBundleId: String? = null,
    val upstreamAssetRefs: List<String> = emptyList(),
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
    song: SongComposerState = SongComposerState(),
    hasMedia: Boolean = true,
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

        PostComposerMode.Image -> {
            if (title.isBlank()) {
                PostComposerDraftValidation(
                    canSubmit = false,
                    errorMessage = "Add a title before posting this image.",
                )
            } else if (!hasMedia) {
                PostComposerDraftValidation(
                    canSubmit = false,
                    errorMessage = "Choose an image before posting.",
                )
            } else {
                PostComposerDraftValidation(canSubmit = true)
            }
        }

        PostComposerMode.Video -> {
            if (title.isBlank()) {
                PostComposerDraftValidation(
                    canSubmit = false,
                    errorMessage = "Add a title before posting this video.",
                )
            } else if (!hasMedia) {
                PostComposerDraftValidation(
                    canSubmit = false,
                    errorMessage = "Choose a video before posting.",
                )
            } else {
                PostComposerDraftValidation(canSubmit = true)
            }
        }

        PostComposerMode.Live -> validateLiveComposerDraft(title, live)
        PostComposerMode.Song -> validateSongComposerDraft(song)
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

fun validateSongComposerDraft(song: SongComposerState): PostComposerDraftValidation {
    if (song.songTitle.isBlank()) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Enter a song title before publishing this song.",
        )
    }
    val hasUploadedAudio = song.primaryAudioLabel.isNotBlank()
    val hasPendingBundle = !song.pendingBundleId.isNullOrBlank()
    if (!hasUploadedAudio && !hasPendingBundle) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Add primary audio before publishing this song.",
        )
    }
    if (song.songMode == SongMode.Remix && song.upstreamAssetRefs.isEmpty()) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Attach a source track before publishing this remix.",
        )
    }
    if (song.licensePreset == AssetLicensePreset.CommercialRemix) {
        val sharePct = song.commercialRevSharePct.trim().toIntOrNull()
        if (sharePct == null || sharePct < 0 || sharePct > 100) {
            return PostComposerDraftValidation(
                canSubmit = false,
                errorMessage = "Choose a valid remix revenue share before publishing this song.",
            )
        }
    } else if (song.commercialRevSharePct.isNotBlank()) {
        return PostComposerDraftValidation(
            canSubmit = false,
            errorMessage = "Revenue share is only available for commercial remix licenses.",
        )
    }
    if (song.paidSongPriceUsd.isNotBlank()) {
        if (usdToCents(song.paidSongPriceUsd) == null) {
            return PostComposerDraftValidation(
                canSubmit = false,
                errorMessage = "Enter a valid unlock price before publishing this song.",
            )
        }
        if (parseSongPreviewStartMs(song.previewStartSeconds) == null) {
            return PostComposerDraftValidation(
                canSubmit = false,
                errorMessage = "Choose where the 30 second preview starts.",
            )
        }
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

fun buildSongPostRequest(
    bundleId: String,
    caption: String,
    idempotencyKey: String,
    song: SongComposerState,
    title: String,
    visibility: String = "public",
): CreatePostRequest {
    val isLocked = song.paidSongPriceUsd.isNotBlank()
    return CreatePostRequest(
        idempotencyKey = idempotencyKey,
        title = title.trim().ifBlank { null },
        caption = caption.trim().ifBlank { null },
        postType = PostComposerMode.Song.apiValue,
        identityMode = "public",
        translationPolicy = "machine_allowed",
        visibility = visibility,
        songArtifactBundle = bundleId,
        songMode = song.songMode.apiValue,
        rightsBasis = if (song.songMode == SongMode.Original) "original" else "derivative",
        accessMode = if (isLocked) "locked" else "public",
        licensePreset = song.licensePreset.apiValue,
        commercialRevSharePct = if (song.licensePreset == AssetLicensePreset.CommercialRemix) {
            song.commercialRevSharePct.trim().toIntOrNull()
        } else {
            null
        },
        upstreamAssetRefs = if (song.songMode == SongMode.Remix) song.upstreamAssetRefs else null,
    )
}

fun buildVideoPostRequest(
    anonymousScope: String? = null,
    caption: String,
    identityMode: String? = null,
    idempotencyKey: String,
    mediaRefs: List<PostMediaRef>,
    title: String,
    upstreamAssetRefs: List<String> = emptyList(),
    visibility: String = "public",
): CreatePostRequest {
    val refs = upstreamAssetRefs.mapNotNull { it.trim().takeIf { value -> value.isNotBlank() } }.distinct()
    return CreatePostRequest(
        idempotencyKey = idempotencyKey,
        title = title.trim().ifBlank { null },
        caption = caption.trim().ifBlank { null },
        postType = PostComposerMode.Video.apiValue,
        mediaRefs = mediaRefs,
        identityMode = identityMode,
        anonymousScope = anonymousScope,
        translationPolicy = "machine_allowed",
        visibility = visibility,
        accessMode = "public",
        rightsBasis = if (refs.isEmpty()) null else "derivative",
        upstreamAssetRefs = refs.ifEmpty { null },
    )
}

fun buildSongListingRequest(
    assetId: String,
    paidSongPriceUsd: String,
    pricingPolicyRegionalPricingEnabled: Boolean = false,
    regionalPricingEnabled: Boolean = false,
): CreateCommunityListingRequest? {
    val priceCents = usdToCents(paidSongPriceUsd) ?: return null
    return CreateCommunityListingRequest(
        asset = assetId,
        priceCents = priceCents,
        regionalPricingEnabled = pricingPolicyRegionalPricingEnabled && regionalPricingEnabled,
        status = "active",
    )
}

fun parseSongPreviewStartMs(value: String): Long? {
    val parsed = value.trim().toLongOrNull() ?: return null
    if (parsed < 0) return null
    return parsed * 1000L
}

fun resolveSongBundleAnalysisState(bundle: SongArtifactBundle): String? {
    val moderationResult = bundle.moderationResult ?: return null
    val direct = (moderationResult["analysis_state"] as? JsonPrimitive)?.contentOrNull
    if (direct != null) return direct
    val nestedResult = moderationResult["moderation_result"] as? JsonObject ?: return null
    return (nestedResult["analysis_state"] as? JsonPrimitive)?.contentOrNull
}

fun songBundleRequiresSourceReference(bundle: SongArtifactBundle): Boolean =
    resolveSongBundleAnalysisState(bundle) == "allow_with_required_reference"

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
