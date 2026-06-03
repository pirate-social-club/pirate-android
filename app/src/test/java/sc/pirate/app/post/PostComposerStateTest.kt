package sc.pirate.app.post

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sc.pirate.app.api.model.PostMediaRef
import sc.pirate.app.api.model.SongArtifactBundle

class PostComposerStateTest {
    @Test
    fun defaultPostComposerState_doesNotWaitForEligibilityWithoutCommunity() {
        val state = PostComposerUiState()

        assertNull(state.selectedCommunityId)
        assertFalse(state.loadingEligibility)
    }

    @Test
    fun getNextPostComposerStep_doesNotLeaveWriteWhenDraftIsInvalid() {
        assertEquals(
            PostComposerStep.Write,
            getNextPostComposerStep(
                current = PostComposerStep.Write,
                draftValidation = PostComposerDraftValidation(canSubmit = false),
            ),
        )
    }

    @Test
    fun getNextPostComposerStep_advancesWriteToSettingsAndSettingsToPublish() {
        val validDraft = PostComposerDraftValidation(canSubmit = true)

        assertEquals(
            PostComposerStep.Settings,
            getNextPostComposerStep(PostComposerStep.Write, validDraft),
        )
        assertEquals(
            PostComposerStep.Publish,
            getNextPostComposerStep(PostComposerStep.Settings, validDraft),
        )
        assertEquals(
            PostComposerStep.Publish,
            getNextPostComposerStep(PostComposerStep.Publish, validDraft),
        )
    }

    @Test
    fun getPreviousPostComposerStep_returnsStepperBackTargets() {
        assertNull(getPreviousPostComposerStep(PostComposerStep.Write))
        assertEquals(PostComposerStep.Write, getPreviousPostComposerStep(PostComposerStep.Settings))
        assertEquals(PostComposerStep.Settings, getPreviousPostComposerStep(PostComposerStep.Publish))
    }

    @Test
    fun canAdvancePostComposerStep_onlyRequiresDraftValidityOnWriteAndPublish() {
        val invalidDraft = PostComposerDraftValidation(canSubmit = false)
        val validDraft = PostComposerDraftValidation(canSubmit = true)

        assertFalse(canAdvancePostComposerStep(PostComposerStep.Write, invalidDraft))
        assertTrue(canAdvancePostComposerStep(PostComposerStep.Write, validDraft))
        assertTrue(canAdvancePostComposerStep(PostComposerStep.Settings, invalidDraft))
        assertFalse(canAdvancePostComposerStep(PostComposerStep.Publish, invalidDraft))
        assertTrue(canAdvancePostComposerStep(PostComposerStep.Publish, validDraft))
    }

    @Test
    fun normalizePostComposerTitleInput_capsTitleAtWebLimit() {
        val title = "a".repeat(POST_COMPOSER_TITLE_MAX_LENGTH + 1)

        assertEquals(
            POST_COMPOSER_TITLE_MAX_LENGTH,
            normalizePostComposerTitleInput(title).length,
        )
    }

    @Test
    fun normalizeHttpUrl_acceptsExplicitHttpUrls() {
        assertEquals("https://example.com/story", normalizeHttpUrl(" https://example.com/story "))
        assertEquals("http://example.com/story", normalizeHttpUrl("http://example.com/story"))
    }

    @Test
    fun normalizeHttpUrl_addsHttpsToSchemelessWebUrls() {
        assertEquals("https://example.com/story", normalizeHttpUrl("example.com/story"))
        assertEquals("https://localhost:5173/submit", normalizeHttpUrl("localhost:5173/submit"))
        assertEquals("https://127.0.0.1:5173/submit", normalizeHttpUrl("127.0.0.1:5173/submit"))
    }

    @Test
    fun normalizeHttpUrl_rejectsUnsupportedOrAmbiguousUrls() {
        assertNull(normalizeHttpUrl(""))
        assertNull(normalizeHttpUrl("sdkljfn"))
        assertNull(normalizeHttpUrl("mailto:test@example.com"))
        assertNull(normalizeHttpUrl("example .com"))
        assertNull(normalizeHttpUrl("pirate:thing"))
    }

    @Test
    fun validatePostComposerDraft_requiresTitleForTextPosts() {
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Text,
                title = " ",
                linkUrl = "",
            ).canSubmit,
        )
        assertTrue(
            validatePostComposerDraft(
                mode = PostComposerMode.Text,
                title = "A post",
                linkUrl = "",
            ).canSubmit,
        )
    }

    @Test
    fun validatePostComposerDraft_requiresValidHttpUrlForLinkPosts() {
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Link,
                title = "",
                linkUrl = "example",
            ).canSubmit,
        )
        assertTrue(
            validatePostComposerDraft(
                mode = PostComposerMode.Link,
                title = "",
                linkUrl = "example.com/story",
            ).canSubmit,
        )
    }

    @Test
    fun validatePostComposerDraft_requiresLiveTitleAndPaidPrice() {
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Live,
                title = "",
                linkUrl = "",
                live = LiveComposerState(),
            ).canSubmit,
        )
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Live,
                title = "Friday set",
                linkUrl = "",
                live = LiveComposerState(accessMode = LiveAccessMode.Paid),
            ).canSubmit,
        )
        assertTrue(
            validatePostComposerDraft(
                mode = PostComposerMode.Live,
                title = "Friday set",
                linkUrl = "",
                live = LiveComposerState(accessMode = LiveAccessMode.Paid, paidPriceUsd = "5.00"),
            ).canSubmit,
        )
    }

    @Test
    fun validatePostComposerDraft_requiresSongTitleAndPrimaryAudio() {
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "Post title",
                linkUrl = "",
                song = SongComposerState(primaryAudioLabel = "song.wav"),
            ).canSubmit,
        )
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "Post title",
                linkUrl = "",
                song = SongComposerState(songTitle = "Track"),
            ).canSubmit,
        )
        assertTrue(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "",
                linkUrl = "",
                song = SongComposerState(songTitle = "Track", primaryAudioLabel = "song.wav"),
            ).canSubmit,
        )
    }

    @Test
    fun validatePostComposerDraft_validatesSongLicenseAndPaidPreview() {
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "",
                linkUrl = "",
                song = SongComposerState(
                    songTitle = "Track",
                    primaryAudioLabel = "song.wav",
                    licensePreset = AssetLicensePreset.CommercialRemix,
                    commercialRevSharePct = "101",
                ),
            ).canSubmit,
        )
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "",
                linkUrl = "",
                song = SongComposerState(
                    songTitle = "Track",
                    primaryAudioLabel = "song.wav",
                    paidSongPriceUsd = "5.00",
                    previewStartSeconds = "",
                ),
            ).canSubmit,
        )
        assertTrue(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "",
                linkUrl = "",
                song = SongComposerState(
                    songTitle = "Track",
                    primaryAudioLabel = "song.wav",
                    licensePreset = AssetLicensePreset.CommercialRemix,
                    commercialRevSharePct = "25",
                    paidSongPriceUsd = "5.00",
                    previewStartSeconds = "12",
                ),
            ).canSubmit,
        )
    }

    @Test
    fun validatePostComposerDraft_requiresSourceRefsForRemixes() {
        assertFalse(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "",
                linkUrl = "",
                song = SongComposerState(
                    songTitle = "Remix",
                    primaryAudioLabel = "song.wav",
                    songMode = SongMode.Remix,
                ),
            ).canSubmit,
        )
        assertTrue(
            validatePostComposerDraft(
                mode = PostComposerMode.Song,
                title = "",
                linkUrl = "",
                song = SongComposerState(
                    songTitle = "Remix",
                    primaryAudioLabel = "song.wav",
                    songMode = SongMode.Remix,
                    upstreamAssetRefs = listOf("story:asset:123"),
                ),
            ).canSubmit,
        )
    }

    @Test
    fun buildSongPostRequest_mapsOriginalPaidSongToApiPayload() {
        val request = buildSongPostRequest(
            bundleId = "sab_track",
            caption = "Listen now",
            idempotencyKey = "idem-song",
            song = SongComposerState(
                songTitle = "Track",
                primaryAudioLabel = "song.wav",
                licensePreset = AssetLicensePreset.CommercialRemix,
                commercialRevSharePct = "30",
                paidSongPriceUsd = "5.00",
                previewStartSeconds = "9",
            ),
            title = "Post title",
        )

        assertEquals("idem-song", request.idempotencyKey)
        assertEquals("song", request.postType)
        assertEquals("Post title", request.title)
        assertEquals("Listen now", request.caption)
        assertEquals("public", request.identityMode)
        assertEquals("machine_allowed", request.translationPolicy)
        assertEquals("sab_track", request.songArtifactBundle)
        assertEquals("original", request.songMode)
        assertEquals("original", request.rightsBasis)
        assertEquals("locked", request.accessMode)
        assertEquals("commercial-remix", request.licensePreset)
        assertEquals(30, request.commercialRevSharePct)
        assertNull(request.upstreamAssetRefs)
    }

    @Test
    fun buildSongPostRequest_mapsRemixSourceRefsOnlyForRemixes() {
        val request = buildSongPostRequest(
            bundleId = "sab_track",
            caption = "",
            idempotencyKey = "idem-song",
            song = SongComposerState(
                songTitle = "Remix",
                primaryAudioLabel = "song.wav",
                songMode = SongMode.Remix,
                upstreamAssetRefs = listOf("story:asset:123"),
            ),
            title = "Remix post",
            visibility = "members_only",
        )

        assertEquals("remix", request.songMode)
        assertEquals("derivative", request.rightsBasis)
        assertEquals("public", request.accessMode)
        assertEquals("members_only", request.visibility)
        assertEquals(listOf("story:asset:123"), request.upstreamAssetRefs)
        assertNull(request.caption)
    }

    @Test
    fun buildVideoPostRequest_keepsOriginalVideoFreeOfDerivativeFields() {
        val request = buildVideoPostRequest(
            anonymousScope = null,
            caption = "A quick clip",
            idempotencyKey = "idem-video",
            identityMode = "public",
            mediaRef = PostMediaRef(storageRef = "media/video.mp4", mimeType = "video/mp4"),
            title = "Clip",
            video = VideoComposerState(),
        )

        assertEquals("idem-video", request.idempotencyKey)
        assertEquals("video", request.postType)
        assertEquals("Clip", request.title)
        assertEquals("A quick clip", request.caption)
        assertEquals("public", request.identityMode)
        assertEquals("machine_allowed", request.translationPolicy)
        assertEquals("public", request.visibility)
        assertEquals("media/video.mp4", request.mediaRefs?.single()?.storageRef)
        assertNull(request.songMode)
        assertNull(request.rightsBasis)
        assertNull(request.upstreamAssetRefs)
        assertNull(request.accessMode)
        assertNull(request.licensePreset)
    }

    @Test
    fun buildVideoPostRequest_mapsUsesSongRefsToDerivativePayload() {
        val request = buildVideoPostRequest(
            anonymousScope = "community_stable",
            caption = "Dancing to this one",
            idempotencyKey = "idem-video",
            identityMode = "anonymous",
            mediaRef = PostMediaRef(storageRef = "media/dance.mp4", mimeType = "video/mp4"),
            title = "Dance",
            video = VideoComposerState(
                sourceMode = VideoSourceMode.UsesSong,
                upstreamAssetRefs = listOf(" story:asset:asset_song ", "", "story:asset:asset_song"),
            ),
        )

        assertEquals("video", request.postType)
        assertEquals("anonymous", request.identityMode)
        assertEquals("community_stable", request.anonymousScope)
        assertEquals("derivative", request.rightsBasis)
        assertEquals(listOf("story:asset:asset_song"), request.upstreamAssetRefs)
        assertNull(request.songMode)
        assertNull(request.accessMode)
        assertNull(request.licensePreset)
    }

    @Test
    fun buildSongListingRequest_returnsActiveAssetListing() {
        val request = buildSongListingRequest(
            assetId = "asset_song",
            paidSongPriceUsd = "4.99",
            pricingPolicyRegionalPricingEnabled = true,
            regionalPricingEnabled = true,
        )

        assertEquals("asset_song", request?.asset)
        assertEquals(499, request?.priceCents)
        assertEquals(true, request?.regionalPricingEnabled)
        assertEquals("active", request?.status)
    }

    @Test
    fun songBundleRequiresSourceReference_readsAnalysisState() {
        val bundle = SongArtifactBundle(
            id = "sab_track",
            moderationResult = JsonObject(
                mapOf("analysis_state" to JsonPrimitive("allow_with_required_reference")),
            ),
        )

        assertEquals("allow_with_required_reference", resolveSongBundleAnalysisState(bundle))
        assertTrue(songBundleRequiresSourceReference(bundle))
    }

    @Test
    fun buildLiveRoomRequest_mapsLiveComposerStateToApiPayload() {
        val request = buildLiveRoomRequest(
            coverRef = "media/live-cover",
            description = "A short live set.",
            hostUserId = "usr_host",
            resolvedGuestUserId = "usr_guest",
            title = "Friday set",
            live = LiveComposerState(
                roomKind = LiveRoomKind.Duet,
                accessMode = LiveAccessMode.Paid,
                visibility = LiveVisibility.Unlisted,
                scheduleForLater = true,
                scheduleAt = "2026-06-01T20:00:00Z",
                guestUserId = "@guest",
                storeUrl = "https://store.example/show",
                storeLabel = "Merch",
                setlistItems = listOf(
                    LiveSetlistItemState(
                        titleText = "Original song",
                        artistText = "Host",
                        declaredTrackId = "sab_track",
                        performanceKind = LiveSetlistItemKind.Original,
                    ),
                    LiveSetlistItemState(
                        titleText = "Licensed cover",
                        artistText = "Writer",
                        declaredTrackId = "story:asset:123",
                        performanceKind = LiveSetlistItemKind.Cover,
                    ),
                ),
                performerAllocations = listOf(
                    LivePerformerAllocationState(role = "host", sharePct = 60),
                    LivePerformerAllocationState(role = "guest", sharePct = 40),
                ),
            ),
        )

        assertEquals("Friday set", request.title)
        assertEquals("A short live set.", request.description)
        assertEquals("duet", request.roomKind)
        assertEquals("paid", request.accessMode)
        assertEquals("unlisted", request.visibility)
        assertEquals("usr_guest", request.guestUser)
        assertEquals("media/live-cover", request.coverRef)
        assertEquals("https://store.example/show", request.storeUrl)
        assertEquals("Merch", request.storeLabel)
        assertEquals(2, request.performerAllocations.size)
        assertEquals(6000, request.performerAllocations.first().shareBps)
        assertEquals("sab_track", request.setlist?.items?.get(0)?.songArtifactBundle)
        assertEquals("original", request.setlist?.items?.get(0)?.rightsBasis)
        assertEquals("story:asset:123", request.setlist?.items?.get(1)?.sourceAssetRef)
        assertEquals("cover", request.setlist?.items?.get(1)?.rightsBasis)
    }

    @Test
    fun buildLiveRoomListingRequest_returnsActiveLiveRoomListing() {
        val request = buildLiveRoomListingRequest(
            liveRoomId = null,
            paidLiveRoomPriceUsd = "7.50",
            pricingPolicyRegionalPricingEnabled = true,
            regionalPricingEnabled = true,
        )

        assertEquals(null, request?.liveRoom)
        assertEquals(750, request?.priceCents)
        assertEquals(true, request?.regionalPricingEnabled)
        assertEquals("active", request?.status)
    }

    @Test
    fun usdToCents_roundsAndRejectsInvalidAmounts() {
        assertEquals(500, usdToCents("5"))
        assertEquals(750, usdToCents("7.50"))
        assertEquals(1000, usdToCents("9.995"))
        assertNull(usdToCents(""))
        assertNull(usdToCents("0"))
        assertNull(usdToCents("-1"))
        assertNull(usdToCents("not-money"))
        assertNull(usdToCents("21474836.48"))
    }

    @Test
    fun parseLiveScheduleEpochSeconds_acceptsIsoInstantAndLocalDateTime() {
        assertEquals(1780344000L, parseLiveScheduleEpochSeconds("2026-06-01T20:00:00Z"))
        assertEquals(1780344000L, parseLiveScheduleEpochSeconds(" 2026-06-01T20:00:00Z "))
        assertNull(parseLiveScheduleEpochSeconds(""))
        assertNull(parseLiveScheduleEpochSeconds("June 1, 2026"))

        val localEpoch = parseLiveScheduleEpochSeconds("2026-06-01T20:00:00")
        assertTrue(localEpoch != null && localEpoch > 0L)
    }

    @Test
    fun normalizeLiveRoomGuestHandle_acceptsHandleForms() {
        assertEquals("guest.pirate", normalizeLiveRoomGuestHandle("@guest.pirate"))
        assertEquals("guest.pirate", normalizeLiveRoomGuestHandle("/u/guest.pirate"))
    }
}
