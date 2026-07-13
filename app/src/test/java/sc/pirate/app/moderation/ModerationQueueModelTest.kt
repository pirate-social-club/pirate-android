package sc.pirate.app.moderation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.CreateModerationActionRequest
import sc.pirate.app.api.model.ModerationCaseListResponse

class ModerationQueueModelTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun moderationQueue_decodesLiveInternalWireContract() {
        val response = json.decodeFromString<ModerationCaseListResponse>(
            """{
                "items":[{
                    "moderation_case_id":"mcase_123",
                    "community_id":"community_456",
                    "post_id":"post_789",
                    "comment_id":null,
                    "status":"open",
                    "queue_scope":"community",
                    "priority":"high",
                    "opened_by":"mixed",
                    "created_at":"2026-07-13T08:00:00.000Z",
                    "updated_at":"2026-07-13T08:10:00.000Z",
                    "resolved_at":null,
                    "post":{
                        "post_id":"post_789",
                        "post_type":"text",
                        "status":"draft",
                        "title":"Held post",
                        "body":"Needs review",
                        "caption":null,
                        "media_refs_json":null,
                        "author_handle":"captain.pirate"
                    }
                }],
                "next_cursor":null
            }""".trimIndent(),
        )

        val item = response.items.single()
        assertEquals("mcase_123", item.moderationCaseId)
        assertEquals("mixed", item.openedBy)
        assertEquals("Held post", item.post?.title)
        assertEquals("draft", item.post?.status)
        assertNull(response.nextCursor)
    }

    @Test
    fun moderationActions_encodeExactMutationContract() {
        val payload = json.encodeToString(CreateModerationActionRequest(actionType = "remove"))

        assertEquals("{\"action_type\":\"remove\"}", payload)
    }

    @Test
    fun resolvedCaseRemovalAndPresentation_areStable() {
        val response = json.decodeFromString<ModerationCaseListResponse>(
            """{"items":[
                {"moderation_case_id":"one","community_id":"c","status":"open","queue_scope":"community","priority":"low","opened_by":"user_report","created_at":"2026-07-13T08:00:00Z"},
                {"moderation_case_id":"two","community_id":"c","status":"open","queue_scope":"community","priority":"high","opened_by":"platform_analysis","created_at":"2026-07-13T09:00:00Z"}
            ],"next_cursor":null}""",
        )

        assertEquals(listOf("two"), removeResolvedModerationCase(response.items, "one").map { it.moderationCaseId })
        assertEquals("Reported by a member", moderationOpenedByLabel("user_report"))
        assertEquals("High priority", moderationPriorityLabel("high"))
        assertEquals(
            "2h",
            formatModerationCaseAge(
                "2026-07-13T08:00:00Z",
                nowSeconds = Instant.parse("2026-07-13T08:00:00Z").epochSecond + 7_200,
            ),
        )
        assertTrue(moderationActionLabel("restore").contains("Approve"))
    }
}
