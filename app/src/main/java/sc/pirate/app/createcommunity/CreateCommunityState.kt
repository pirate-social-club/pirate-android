package sc.pirate.app.createcommunity

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sc.pirate.app.api.model.GateAtom
import sc.pirate.app.api.model.GateExpression
import sc.pirate.app.api.model.GatePolicy
import java.util.Locale

enum class CreateCommunityStep {
    Basics,
    Access,
    Review,
}

enum class CommunityMembershipMode(val value: String) {
    Request("request"),
    Gated("gated"),
}

enum class CommunityGateMatchMode(val value: String) {
    All("all"),
    Any("any"),
}

enum class CommunityDefaultAgeGatePolicy(val value: String) {
    None("none"),
    EighteenPlus("18_plus"),
}

enum class AnonymousIdentityScope(val value: String) {
    CommunityStable("community_stable"),
    ThreadStable("thread_stable"),
}

data class CommunityDatabaseRegion(
    val value: String,
    val label: String,
)

val createCommunityDatabaseRegions = listOf(
    CommunityDatabaseRegion("aws-us-east-1", "🇺🇸 US East"),
    CommunityDatabaseRegion("aws-us-east-2", "🇺🇸 US Central"),
    CommunityDatabaseRegion("aws-us-west-2", "🇺🇸 US West"),
    CommunityDatabaseRegion("aws-eu-west-1", "🇮🇪 Ireland (EU)"),
    CommunityDatabaseRegion("aws-ap-south-1", "🇮🇳 India"),
    CommunityDatabaseRegion("aws-ap-northeast-1", "🇯🇵 Japan"),
)

fun createCommunityDatabaseRegionLabel(value: String): String =
    createCommunityDatabaseRegions.firstOrNull { it.value == value }?.label ?: "US East"

sealed interface IdentityGateDraft {
    val gateType: String
}

data object AltchaPowGateDraft : IdentityGateDraft {
    override val gateType: String = "altcha_pow"
}

data class UniqueHumanGateDraft(
    val provider: String = "very",
) : IdentityGateDraft {
    override val gateType: String = "unique_human"
}

data class NationalityGateDraft(
    val provider: String = "self",
    val requiredValues: List<String> = emptyList(),
) : IdentityGateDraft {
    override val gateType: String = "nationality"
}

data class MinimumAgeGateDraft(
    val provider: String = "self",
    val minimumAge: Int = 30,
) : IdentityGateDraft {
    override val gateType: String = "minimum_age"
}

data class GenderGateDraft(
    val provider: String = "self",
    val requiredValue: String = "F",
) : IdentityGateDraft {
    override val gateType: String = "gender"
}

data class WalletScoreGateDraft(
    val provider: String = "passport",
    val minimumScore: Int = 20,
) : IdentityGateDraft {
    override val gateType: String = "wallet_score"
}

data class Erc721HoldingGateDraft(
    val chainNamespace: String = "eip155:1",
    val contractAddress: String = "",
) : IdentityGateDraft {
    override val gateType: String = "erc721_holding"
}

data class Erc721InventoryMatchGateDraft(
    val chainNamespace: String = "eip155:137",
    val contractAddress: String = "0x251BE3A17Af4892035C37ebf5890F4a4D889dcAD",
    val inventoryProvider: String = "courtyard",
    val minQuantity: Int = 1,
    val assetFilter: Map<String, String> = mapOf("category" to "trading_card"),
) : IdentityGateDraft {
    override val gateType: String = "erc721_inventory_match"
}

val defaultGatedGateDrafts: List<IdentityGateDraft> = listOf(AltchaPowGateDraft)

private val powExclusiveGateTypes = setOf(
    "unique_human",
    "nationality",
    "minimum_age",
    "wallet_score",
    "gender",
)

fun normalizeGateDraftsForMatchMode(
    drafts: List<IdentityGateDraft>,
    gateMatchMode: CommunityGateMatchMode,
): List<IdentityGateDraft> {
    if (gateMatchMode == CommunityGateMatchMode.Any) return drafts
    val hasPow = drafts.any { it.gateType == "altcha_pow" }
    val hasPowExclusiveGate = drafts.any { it.gateType in powExclusiveGateTypes }
    if (!hasPow || !hasPowExclusiveGate) return drafts
    return drafts.filterNot { it.gateType == "altcha_pow" }
}

fun upsertGateDraftForMatchMode(
    drafts: List<IdentityGateDraft>,
    nextDraft: IdentityGateDraft,
    gateMatchMode: CommunityGateMatchMode,
): List<IdentityGateDraft> {
    if (gateMatchMode == CommunityGateMatchMode.Any) {
        return drafts.filterNot { it.gateType == nextDraft.gateType } + nextDraft
    }
    if (nextDraft.gateType == "altcha_pow") {
        return drafts
            .filterNot { it.gateType in powExclusiveGateTypes || it.gateType == "altcha_pow" } + nextDraft
    }
    val withoutConflicts = if (nextDraft.gateType in powExclusiveGateTypes) {
        drafts.filterNot { it.gateType == "altcha_pow" }
    } else {
        drafts
    }
    return withoutConflicts.filterNot { it.gateType == nextDraft.gateType } + nextDraft
}

fun removeGateDraft(
    drafts: List<IdentityGateDraft>,
    gateType: String,
): List<IdentityGateDraft> = drafts.filterNot { it.gateType == gateType }

fun shouldResetMatchModeAfterRemovingPowFallback(
    drafts: List<IdentityGateDraft>,
    gateMatchMode: CommunityGateMatchMode,
): Boolean =
    gateMatchMode == CommunityGateMatchMode.Any &&
        removeGateDraft(drafts, "altcha_pow").size <= 1

fun invalidGateDraftReason(draft: IdentityGateDraft): String? =
    when (draft) {
        is Erc721HoldingGateDraft ->
            if (isEthereumAddress(draft.contractAddress.trim())) null else "invalid_erc721_contract"
        is Erc721InventoryMatchGateDraft ->
            if (
                isEthereumAddress(draft.contractAddress.trim()) &&
                draft.minQuantity >= 1 &&
                draft.assetFilter["category"].isNullOrBlank().not()
            ) {
                null
            } else {
                "invalid_courtyard_inventory"
            }
        is WalletScoreGateDraft ->
            if (draft.minimumScore in 0..100) null else "invalid_wallet_score"
        else -> null
    }

fun isValidGateDraft(draft: IdentityGateDraft): Boolean = invalidGateDraftReason(draft) == null

fun effectiveDefaultAgeGatePolicy(
    membershipMode: CommunityMembershipMode,
    defaultAgeGatePolicy: CommunityDefaultAgeGatePolicy,
    gateDrafts: List<IdentityGateDraft>,
): CommunityDefaultAgeGatePolicy {
    val minimumAgeGate = gateDrafts.filterIsInstance<MinimumAgeGateDraft>().firstOrNull()
    val hasAdultMinimumAgeGate =
        membershipMode == CommunityMembershipMode.Gated &&
            minimumAgeGate != null &&
            minimumAgeGate.minimumAge in 18..125
    return if (hasAdultMinimumAgeGate) {
        CommunityDefaultAgeGatePolicy.EighteenPlus
    } else {
        defaultAgeGatePolicy
    }
}

fun canAdvanceCreateCommunityStep(
    step: CreateCommunityStep,
    displayName: String,
    membershipMode: CommunityMembershipMode,
    gateDrafts: List<IdentityGateDraft>,
): Boolean =
    when (step) {
        CreateCommunityStep.Basics -> displayName.trim().isNotBlank()
        CreateCommunityStep.Access ->
            membershipMode != CommunityMembershipMode.Gated ||
                (gateDrafts.isNotEmpty() && gateDrafts.all(::isValidGateDraft))
        CreateCommunityStep.Review ->
            displayName.trim().isNotBlank() &&
                (
                    membershipMode != CommunityMembershipMode.Gated ||
                        (gateDrafts.isNotEmpty() && gateDrafts.all(::isValidGateDraft))
                    )
    }

fun serializeIdentityGateDrafts(
    gateDrafts: List<IdentityGateDraft>,
    gateMatchMode: CommunityGateMatchMode,
): GatePolicy? {
    val expressions = gateDrafts.mapNotNull(::draftToExpression)
    if (expressions.isEmpty()) return null
    return GatePolicy(
        version = 1,
        expression = GateExpression(
            op = if (gateMatchMode == CommunityGateMatchMode.Any) "or" else "and",
            children = expressions,
        ),
    )
}

fun formatGateRequirementList(
    gateDrafts: List<IdentityGateDraft>,
    gateMatchMode: CommunityGateMatchMode,
): String? {
    if (gateDrafts.isEmpty()) return null
    val labels = gateDrafts.map(::formatGateRequirement)
    if (labels.size == 1) return labels.first()
    val separator = if (gateMatchMode == CommunityGateMatchMode.Any) " or " else " and "
    return labels.joinToString(separator)
}

fun formatMembershipLabel(membershipMode: CommunityMembershipMode): String =
    when (membershipMode) {
        CommunityMembershipMode.Request -> "Approval required"
        CommunityMembershipMode.Gated -> "Automatic after passing gates"
    }

fun formatAnonymousScopeLabel(scope: AnonymousIdentityScope): String =
    when (scope) {
        AnonymousIdentityScope.CommunityStable -> "Community-stable"
        AnonymousIdentityScope.ThreadStable -> "Thread-stable"
    }

private fun draftToExpression(draft: IdentityGateDraft): GateExpression? =
    GateExpression(op = "gate", gate = draftToAtom(draft))

private fun draftToAtom(draft: IdentityGateDraft): GateAtom? =
    when (draft) {
        AltchaPowGateDraft -> GateAtom(type = "altcha_pow")
        is UniqueHumanGateDraft -> GateAtom(type = "unique_human", provider = draft.provider)
        is MinimumAgeGateDraft -> GateAtom(
            type = "minimum_age",
            provider = "self",
            minimumAge = draft.minimumAge,
        )
        is WalletScoreGateDraft -> GateAtom(
            type = "wallet_score",
            provider = "passport",
            minimumScore = draft.minimumScore,
        )
        is NationalityGateDraft -> GateAtom(
            type = "nationality",
            provider = "self",
            allowed = draft.requiredValues,
        )
        is GenderGateDraft -> GateAtom(
            type = "gender",
            provider = "self",
            allowed = listOf(draft.requiredValue),
        )
        is Erc721HoldingGateDraft -> GateAtom(
            type = "erc721_holding",
            chainNamespace = draft.chainNamespace,
            contractAddress = draft.contractAddress.trim(),
        )
        is Erc721InventoryMatchGateDraft -> GateAtom(
            type = "erc721_inventory_match",
            provider = draft.inventoryProvider,
            chainNamespace = draft.chainNamespace,
            contractAddress = draft.contractAddress.trim(),
            minQuantity = draft.minQuantity,
            match = JsonObject(
                draft.assetFilter
                    .filterValues { it.isNotBlank() }
                    .mapValues { JsonPrimitive(it.value) },
            ),
        )
    }

private fun formatGateRequirement(draft: IdentityGateDraft): String =
    when (draft) {
        AltchaPowGateDraft -> "Proof-of-work check"
        is UniqueHumanGateDraft -> "Palm scan"
        is NationalityGateDraft -> {
            val countries = draft.requiredValues
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { countryCodeToName(it) }
                ?: "required"
            "Nationality: $countries"
        }
        is MinimumAgeGateDraft -> "Age ${draft.minimumAge}+"
        is GenderGateDraft -> "Document marker: ${draft.requiredValue}"
        is WalletScoreGateDraft -> "Passport score ${draft.minimumScore}+"
        is Erc721HoldingGateDraft -> {
            val contract = draft.contractAddress.trim().ifBlank { "configured" }
            "Ethereum NFT collection: $contract"
        }
        is Erc721InventoryMatchGateDraft -> {
            val category = draft.assetFilter["category"]?.replace('_', ' ') ?: "required"
            "Courtyard inventory: $category"
        }
    }

private fun isEthereumAddress(value: String): Boolean =
    Regex("^0x[0-9a-fA-F]{40}$").matches(value)

private fun countryCodeToName(value: String): String {
    val code = value.trim()
    val alpha2 = when {
        code.length == 2 -> code.uppercase(Locale.ROOT)
        code.length == 3 -> Locale.getISOCountries()
            .firstOrNull { region ->
                runCatching {
                    Locale("", region).getISO3Country().equals(code, ignoreCase = true)
                }.getOrDefault(false)
            }
        else -> null
    } ?: return code.ifBlank { "required" }
    return runCatching {
        Locale.Builder()
            .setRegion(alpha2)
            .build()
            .getDisplayCountry(Locale.getDefault())
            .takeIf { it.isNotBlank() }
    }.getOrNull() ?: code
}
