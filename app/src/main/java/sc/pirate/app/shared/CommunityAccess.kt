package sc.pirate.app.shared

import java.util.Locale
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.MembershipGateSummary

fun gateSummaryText(gate: MembershipGateSummary): String =
    when (gate.gateType) {
        "altcha_pow" -> "Proof-of-work check"
        "unique_human" -> {
            val providers = gate.acceptedProviders
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = " via ") { verificationProviderLabel(it) }
                .orEmpty()
            "Unique human proof$providers"
        }
        "age_over_18" -> "Age 18+"
        "minimum_age" -> "Age ${gate.requiredMinimumAge ?: gate.requiredValue ?: "required"}+"
        "nationality" -> "Nationality: ${formatCountryRequirement(gate)}"
        "gender" -> "Document marker: ${gate.requiredValue ?: "required"}"
        "wallet_score" -> "Wallet score: ${gate.minimumScore ?: gate.requiredValue ?: "required"}+"
        "erc721_holding" -> "NFT gate: ${gate.assetFilterLabel ?: gate.contractAddress ?: "configured"}"
        "erc721_inventory_match" -> "Inventory gate: ${gate.assetFilterLabel ?: gate.assetCategory ?: "required"}"
        else -> gate.gateType.replace('_', ' ')
    }

fun verificationProviderLabel(provider: String?): String =
    when (provider) {
        "self" -> "Self"
        "very" -> "Very"
        "passport" -> "Passport"
        else -> provider?.replace('_', ' ') ?: "verification"
    }

fun formatCountryRequirement(gate: MembershipGateSummary): String {
    val countries = gate.requiredValues?.takeIf { it.isNotEmpty() }
        ?: gate.requiredValue?.takeIf { it.isNotBlank() }?.let(::listOf)
        ?: return "required"
    return countries.joinToString(", ") { countryCodeToName(it) }
}

private fun countryCodeToName(value: String): String {
    val code = value.trim()
    if (code.length != 2) return code.ifBlank { "required" }
    return runCatching {
        Locale.Builder()
            .setRegion(code.uppercase(Locale.ROOT))
            .build()
            .getDisplayCountry(Locale.getDefault())
            .takeIf { it.isNotBlank() }
    }.getOrNull() ?: code
}

fun requiresProofOfWork(eligibility: JoinEligibility?): Boolean =
    eligibility?.missingCapabilities?.contains("altcha_pow") == true ||
        eligibility?.membershipGateSummaries?.any { it.gateType == "altcha_pow" } == true

fun communityAltchaAction(communityId: String): String {
    val trimmed = communityId.trim()
    val publicCommunityId = if (trimmed.startsWith("com_")) trimmed else "com_$trimmed"
    return "community:$publicCommunityId"
}

fun isSelfCapability(capability: String): Boolean =
    capability == "unique_human" ||
        capability == "age_over_18" ||
        capability == "minimum_age" ||
        capability == "nationality" ||
        capability == "gender"
