package sc.pirate.app.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PostReportTest {
    @Test
    fun childSafetyReportUsesHighPriorityBackendReasonWithExplicitNote() {
        val request = buildPostReportRequest(
            PostReportDraft(
                reason = PostReportReason.ChildSafety,
                note = "user says this looks like grooming",
            ),
        )

        assertEquals("sexual_content", request.reasonCode)
        assertEquals("Child safety concern: user says this looks like grooming", request.note)
    }

    @Test
    fun childSafetyReportKeepsExplicitChildSafetySignalWithoutUserNote() {
        val request = buildPostReportRequest(PostReportDraft(reason = PostReportReason.ChildSafety))

        assertEquals("sexual_content", request.reasonCode)
        assertEquals("Child safety concern", request.note)
    }

    @Test
    fun ordinaryReportsDoNotAddChildSafetyPrefix() {
        val request = buildPostReportRequest(
            PostReportDraft(
                reason = PostReportReason.Spam,
                note = "same link posted repeatedly",
            ),
        )

        assertEquals("spam", request.reasonCode)
        assertEquals("same link posted repeatedly", request.note)
    }

    @Test
    fun emptyOrdinaryReportNoteIsOmitted() {
        val request = buildPostReportRequest(PostReportDraft(reason = PostReportReason.Other, note = "   "))

        assertEquals("other", request.reasonCode)
        assertNull(request.note)
    }
}
