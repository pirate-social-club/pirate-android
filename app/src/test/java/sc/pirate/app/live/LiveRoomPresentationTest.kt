package sc.pirate.app.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.LiveRoom
import sc.pirate.app.api.model.LiveRoomAccess
import sc.pirate.app.api.model.LiveRoomAccessResponse

class LiveRoomPresentationTest {
    @Test
    fun scheduledFreeRoomWithoutStartTimeDoesNotInventStatusLabel() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "scheduled", accessMode = "free"),
            access = LiveRoomAccess(allowed = true, decisionReason = "not_live"),
        )

        assertNull(presentation.statusLabel)
        assertEquals(LiveRoomUiState.Scheduled, presentation.uiState)
    }

    @Test
    fun postAuthorHasPaidEntitlementWithoutPurchase() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "scheduled", accessMode = "paid"),
            access = LiveRoomAccess(allowed = false, decisionReason = null, accessMode = "paid"),
            viewerUserId = "usr_author",
            postAuthorUserId = "author",
        )

        assertTrue(presentation.hasEntitlement)
        assertEquals(LiveRoomUiState.HasTicket, presentation.uiState)
    }

    @Test
    fun publicFreeLiveAllowedNonProducerCanInlineAttach() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "live", accessMode = "free", visibility = "public", hostUser = "usr_host"),
            access = LiveRoomAccess(allowed = true, decisionReason = "allowed", accessMode = "free", visibility = "public"),
            viewerUserId = "usr_viewer",
        )

        assertTrue(presentation.canInlineAttachViewer)
        assertEquals(LiveRoomUiState.CanWatch(), presentation.uiState)
    }

    @Test
    fun ageProofRequiredBlocksInlineAttachAndCover() {
        val presentation = buildPresentation(
            room = LiveRoom(
                status = "live",
                accessMode = "free",
                visibility = "public",
                hostUser = "usr_host",
                coverRef = "ipfs://adult-cover",
            ),
            access = LiveRoomAccess(allowed = true, decisionReason = "allowed", accessMode = "free", visibility = "public"),
            viewerUserId = "usr_viewer",
            ageProofRequired = true,
        )

        assertFalse(presentation.canInlineAttachViewer)
        assertNull(presentation.coverSrc)
        assertEquals(LiveRoomUiState.NeedsVerification(), presentation.uiState)
    }

    @Test
    fun producerDoesNotInlineAttachViewer() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "live", accessMode = "free", visibility = "public", hostUser = "usr_host"),
            access = LiveRoomAccess(allowed = true, decisionReason = "allowed", accessMode = "free", visibility = "public"),
            viewerUserId = "usr_host",
        )

        assertFalse(presentation.canInlineAttachViewer)
        assertEquals(LiveRoomProducerRole.Host, presentation.producerRole)
    }

    @Test
    fun endedReadyReplayCanWatchReplay() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "ended", accessMode = "free", replayStatus = "ready"),
            access = LiveRoomAccess(allowed = true, decisionReason = "ended"),
        )

        assertEquals(LiveRoomUiState.CanWatchReplay(), presentation.uiState)
    }

    @Test
    fun endedPublishedReplayCanWatchReplay() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "ended", accessMode = "free", replayStatus = "published"),
            access = LiveRoomAccess(allowed = true, decisionReason = "ended"),
        )

        assertEquals(LiveRoomUiState.CanWatchReplay(), presentation.uiState)
    }

    @Test
    fun reviewPendingReplayShowsProcessingToViewers() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "ended", accessMode = "free", replayStatus = "review_pending"),
            access = LiveRoomAccess(allowed = true, decisionReason = "ended"),
        )

        assertEquals(LiveRoomUiState.ReplayProcessing, presentation.uiState)
    }

    @Test
    fun purchaseRequiredWithoutListingIsTicketsUnavailable() {
        val presentation = buildPresentation(
            room = LiveRoom(status = "live", accessMode = "paid"),
            access = LiveRoomAccess(allowed = false, decisionReason = "purchase_required", accessMode = "paid"),
        )

        assertEquals(LiveRoomAccessState.MissingListing, presentation.accessState)
        assertEquals(LiveRoomUiState.TicketsUnavailable, presentation.uiState)
    }

    private fun buildPresentation(
        room: LiveRoom,
        access: LiveRoomAccess,
        viewerUserId: String? = null,
        postAuthorUserId: String? = "usr_author",
        ageProofRequired: Boolean = false,
    ): LiveRoomPresentation =
        buildLiveRoomPresentation(
            LiveRoomPresentationInput(
                fallbackTitle = "Live room",
                access = LiveRoomAccessResponse(room = room, access = access),
                listing = null,
                purchase = null,
                publicStatus = null,
                publicAccessMode = null,
                fallbackCoverRef = null,
                viewerUserId = viewerUserId,
                postAuthorUserId = postAuthorUserId,
                liveRoomId = null,
                ageProofRequired = ageProofRequired,
            ),
        )
}
