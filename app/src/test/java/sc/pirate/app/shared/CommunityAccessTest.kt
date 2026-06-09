package sc.pirate.app.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import sc.pirate.app.api.model.JoinEligibility
import sc.pirate.app.api.model.MembershipGateSummary

class CommunityAccessTest {
    @Test
    fun requiresProofOfWork_readsMissingCapabilitiesAndGateSummaries() {
        assertTrue(
            requiresProofOfWork(
                eligibility(missingCapabilities = listOf("altcha_pow")),
            ),
        )
        assertTrue(
            requiresProofOfWork(
                eligibility(gates = listOf(MembershipGateSummary(gateType = "altcha_pow"))),
            ),
        )
        assertTrue(
            requiresProofOfWork(
                eligibility(
                    missingCapabilities = listOf("altcha_pow"),
                    gates = listOf(MembershipGateSummary(gateType = "altcha_pow")),
                ),
            ),
        )
        assertFalse(requiresProofOfWork(eligibility()))
        assertFalse(
            requiresProofOfWork(
                eligibility(
                    missingCapabilities = listOf("unique_human"),
                    gates = listOf(MembershipGateSummary(gateType = "unique_human")),
                ),
            ),
        )
        assertFalse(requiresProofOfWork(null))
    }

    @Test
    fun communityAltchaAction_normalizesCommunityId() {
        assertEquals("community:com_audio", communityAltchaAction("audio"))
        assertEquals("community:com_audio", communityAltchaAction("com_audio"))
        assertEquals("community:com_audio", communityAltchaAction(" audio "))
        assertEquals("community:com_", communityAltchaAction(""))
    }

    @Test
    fun isSelfCapability_matchesIdentityCapabilities() {
        assertTrue(isSelfCapability("unique_human"))
        assertTrue(isSelfCapability("age_over_18"))
        assertTrue(isSelfCapability("minimum_age"))
        assertTrue(isSelfCapability("nationality"))
        assertTrue(isSelfCapability("gender"))
        assertFalse(isSelfCapability("altcha_pow"))
        assertFalse(isSelfCapability("wallet_score"))
    }

    @Test
    fun gateSummaryText_formatsKnownGateTypesAndFallback() {
        assertEquals(
            "Proof-of-work check",
            gateSummaryText(MembershipGateSummary(gateType = "altcha_pow")),
        )
        assertEquals(
            "Unique human proof via Self, Very",
            gateSummaryText(
                MembershipGateSummary(
                    gateType = "unique_human",
                    acceptedProviders = listOf("self", "very"),
                ),
            ),
        )
        assertEquals(
            "Age 18+",
            gateSummaryText(MembershipGateSummary(gateType = "age_over_18")),
        )
        assertEquals(
            "Age 21+",
            gateSummaryText(
                MembershipGateSummary(
                    gateType = "minimum_age",
                    requiredMinimumAge = 21,
                ),
            ),
        )
        assertEquals(
            "Nationality: global",
            gateSummaryText(
                MembershipGateSummary(
                    gateType = "nationality",
                    requiredValue = "global",
                ),
            ),
        )
        assertEquals(
            "unknown gate",
            gateSummaryText(MembershipGateSummary(gateType = "unknown_gate")),
        )
    }

    @Test
    fun verificationProviderLabel_formatsKnownProvidersAndFallbacks() {
        assertEquals("Self", verificationProviderLabel("self"))
        assertEquals("Very", verificationProviderLabel("very"))
        assertEquals("Passport", verificationProviderLabel("passport"))
        assertEquals("world coin", verificationProviderLabel("world_coin"))
        assertEquals("verification", verificationProviderLabel(null))
    }

    @Test
    fun formatCountryRequirement_formatsMissingAndRawValues() {
        assertEquals(
            "required",
            formatCountryRequirement(MembershipGateSummary(gateType = "nationality")),
        )
        assertEquals(
            "required",
            formatCountryRequirement(
                MembershipGateSummary(
                    gateType = "nationality",
                    requiredValue = " ",
                ),
            ),
        )
        assertEquals(
            "global",
            formatCountryRequirement(
                MembershipGateSummary(
                    gateType = "nationality",
                    requiredValue = "global",
                ),
            ),
        )
    }

    private fun eligibility(
        missingCapabilities: List<String> = emptyList(),
        gates: List<MembershipGateSummary> = emptyList(),
    ): JoinEligibility =
        JoinEligibility(
            membershipMode = "gated",
            humanVerificationLane = "none",
            joinableNow = false,
            status = "verification_required",
            membershipGateSummaries = gates,
            missingCapabilities = missingCapabilities,
        )
}
