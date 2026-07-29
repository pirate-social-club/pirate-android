package sc.pirate.app.moderation

import sc.pirate.app.api.model.CommunityRule
import sc.pirate.app.api.model.UpdateCommunityRuleInput
import sc.pirate.app.api.model.UpdateCommunityRulesRequest

data class CommunityRuleDraft(
    val id: String,
    val existingRuleId: String? = null,
    val title: String = "",
    val body: String = "",
    val reportReason: String = "",
)

data class CommunityRulesUiState(
    val communityId: String? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val rules: List<CommunityRuleDraft> = emptyList(),
    val savedRules: List<CommunityRuleDraft> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

internal fun communityRuleDrafts(rules: List<CommunityRule>): List<CommunityRuleDraft> =
    rules
        .withIndex()
        .sortedBy { it.value.position ?: it.index }
        .map { indexed ->
            val rule = indexed.value
            val stableId = rule.ruleId.takeIf { it.isNotBlank() } ?: "rule-${indexed.index}"
            CommunityRuleDraft(
                id = stableId,
                existingRuleId = rule.ruleId.takeIf { it.isNotBlank() },
                title = rule.title,
                body = rule.body.orEmpty(),
                reportReason = rule.reportReason?.trim()?.takeIf { it.isNotBlank() } ?: rule.title,
            )
        }

internal fun communityRulesValidationError(rules: List<CommunityRuleDraft>): String? {
    if (rules.isEmpty()) return "Add at least one rule."
    rules.forEachIndexed { index, rule ->
        if (rule.title.isBlank()) return "Rule ${index + 1} needs a title."
        if (rule.title.length > 100) return "Rule ${index + 1} title must be 100 characters or fewer."
        if (rule.body.length > 500) return "Rule ${index + 1} description must be 500 characters or fewer."
        if (rule.reportReason.length > 100) return "Rule ${index + 1} report reason must be 100 characters or fewer."
    }
    return null
}

internal fun buildCommunityRulesUpdate(rules: List<CommunityRuleDraft>): UpdateCommunityRulesRequest =
    UpdateCommunityRulesRequest(
        rules = rules.mapIndexed { index, rule ->
            UpdateCommunityRuleInput(
                // Preview IDs are public `rule_<database-id>` values. The write API
                // accepts the underlying database ID and wraps it again on reads.
                ruleId = rule.existingRuleId
                    ?.removePrefix("rule_")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                title = rule.title.trim(),
                body = rule.body.trim(),
                reportReason = rule.reportReason.trim().ifBlank { rule.title.trim() },
                position = index,
                status = "active",
            )
        },
    )

internal fun moveCommunityRule(
    rules: List<CommunityRuleDraft>,
    fromIndex: Int,
    toIndex: Int,
): List<CommunityRuleDraft> {
    if (fromIndex !in rules.indices || toIndex !in rules.indices || fromIndex == toIndex) return rules
    return rules.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}

internal fun CommunityRulesUiState.hasChanges(): Boolean = rules != savedRules
