package sc.pirate.app.moderation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.CommunityRule

class CommunityRulesStateTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Test
    fun communityRule_decodesPublicPreviewContractId() {
        val rule = json.decodeFromString<CommunityRule>(
            """{
                "id":"rule_rul_123",
                "object":"community_rule",
                "title":"Be kind",
                "body":"No harassment.",
                "report_reason":"Harassment",
                "position":2,
                "status":"active"
            }""".trimIndent(),
        )

        assertEquals("rule_rul_123", rule.ruleId)
        assertEquals("Harassment", rule.reportReason)
        assertEquals(2, rule.position)
    }

    @Test
    fun rulesUpdate_trimsFieldsUnwrapsPublicIdsAndPinsOrder() {
        val update = buildCommunityRulesUpdate(
            listOf(
                CommunityRuleDraft(
                    id = "rule_rul_123",
                    existingRuleId = "rule_rul_123",
                    title = "  Be kind  ",
                    body = "  No harassment.  ",
                    reportReason = "  ",
                ),
                CommunityRuleDraft(id = "draft-2", title = "Stay on topic"),
            ),
        )

        assertEquals("rul_123", update.rules[0].ruleId)
        assertEquals("Be kind", update.rules[0].title)
        assertEquals("No harassment.", update.rules[0].body)
        assertEquals("Be kind", update.rules[0].reportReason)
        assertEquals(0, update.rules[0].position)
        assertNull(update.rules[1].ruleId)
        assertEquals(1, update.rules[1].position)

        val payload = json.encodeToString(update)
        assertTrue(payload.contains("\"rule_id\":\"rul_123\""))
        assertTrue(payload.contains("\"status\":\"active\""))
    }

    @Test
    fun rulesValidation_enforcesWebEditorLimits() {
        assertEquals("Add at least one rule.", communityRulesValidationError(emptyList()))
        assertEquals(
            "Rule 1 needs a title.",
            communityRulesValidationError(listOf(CommunityRuleDraft(id = "draft"))),
        )
        assertEquals(
            "Rule 1 title must be 100 characters or fewer.",
            communityRulesValidationError(
                listOf(CommunityRuleDraft(id = "draft", title = "x".repeat(101))),
            ),
        )
        assertNull(
            communityRulesValidationError(
                listOf(CommunityRuleDraft(id = "draft", title = "Be kind", body = "Always.")),
            ),
        )
    }

    @Test
    fun moveRule_preservesStableDraftsAndRejectsInvalidMoves() {
        val rules = listOf(
            CommunityRuleDraft(id = "one", title = "One"),
            CommunityRuleDraft(id = "two", title = "Two"),
            CommunityRuleDraft(id = "three", title = "Three"),
        )

        assertEquals(listOf("two", "one", "three"), moveCommunityRule(rules, 1, 0).map { it.id })
        assertEquals(rules, moveCommunityRule(rules, 0, -1))
    }
}

