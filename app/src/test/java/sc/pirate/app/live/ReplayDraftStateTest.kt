package sc.pirate.app.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sc.pirate.app.api.model.LiveRoomReplayAllocation
import sc.pirate.app.api.model.LiveRoomReplayAsset
import sc.pirate.app.api.model.LiveRoomReplayDraft

class ReplayDraftStateTest {
    private fun readyDraft(
        allocations: List<LiveRoomReplayAllocation> = listOf(
            LiveRoomReplayAllocation(participantUser = "usr_host", role = "host", shareBps = 10_000),
        ),
    ) = LiveRoomReplayDraft(
        recordingEnabled = true,
        replayStatus = "review_pending",
        status = "ready",
        replayAsset = LiveRoomReplayAsset(
            title = "Live set",
            allocations = allocations,
        ),
    )

    @Test
    fun readyReplayWithCompleteSplit_canPublishFree() {
        val validation = validateReplayDraftForFreePublish(
            draft = readyDraft(),
            title = "Friday set",
            caption = "Recorded live.",
        )

        assertTrue(validation.canPublish)
    }

    @Test
    fun replayPublish_requiresReadyDraftAndCompleteSplit() {
        assertFalse(
            validateReplayDraftForFreePublish(
                readyDraft().copy(status = "processing", replayStatus = "processing"),
                "Title",
                "",
            ).canPublish,
        )
        assertFalse(
            validateReplayDraftForFreePublish(
                readyDraft(
                    listOf(LiveRoomReplayAllocation(participantUser = "usr_host", shareBps = 9_000)),
                ),
                "Title",
                "",
            ).canPublish,
        )
    }

    @Test
    fun freeReplayUpdate_trimsTextAndPinsFreeAccess() {
        val request = buildFreeReplayDraftUpdate("  Friday set  ", "  Recorded live.  ")

        assertEquals("Friday set", request.title)
        assertEquals("Recorded live.", request.caption)
        assertEquals("free", request.accessMode)
        val payload = Json { encodeDefaults = false }.encodeToString(request)
        assertTrue(payload.contains("\"caption\":null"))
        assertTrue(payload.contains("\"access_mode\":\"free\""))
    }
}
