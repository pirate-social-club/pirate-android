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
                eligibility(gates = listOf(validGate("altcha_pow"))),
            ),
        )
        assertTrue(
            requiresProofOfWork(
                eligibility(
                    missingCapabilities = listOf("altcha_pow"),
                    gates = listOf(validGate("altcha_pow")),
                ),
            ),
        )
        assertFalse(requiresProofOfWork(eligibility()))
        assertFalse(
            requiresProofOfWork(
                eligibility(
                    missingCapabilities = listOf("unique_human"),
                    gates = listOf(validGate("unique_human")),
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
            gateSummaryText(validGate("altcha_pow")),
        )
        assertEquals(
            "Unique human proof via Self, Very",
            gateSummaryText(
                validGate(
                    gateType = "unique_human",
                    acceptedProviders = listOf("self", "very"),
                ),
            ),
        )
        assertEquals(
            "Age 18+",
            gateSummaryText(validGate("age_over_18")),
        )
        assertEquals(
            "Age 21+",
            gateSummaryText(
                validGate(
                    gateType = "minimum_age",
                    requiredMinimumAge = 21,
                ),
            ),
        )
        assertEquals(
            "Nationality: global",
            gateSummaryText(
                validGate(
                    gateType = "nationality",
                    requiredValue = "global",
                ),
            ),
        )
        assertEquals(
            "unknown gate",
            gateSummaryText(validGate("unknown_gate")),
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
            formatCountryRequirement(validGate("nationality")),
        )
        assertEquals(
            "required",
            formatCountryRequirement(
                validGate(
                    gateType = "nationality",
                    requiredValue = " ",
                ),
            ),
        )
        // Country lookup only applies to two-character codes; other values pass through.
        assertEquals(
            "global",
            formatCountryRequirement(
                validGate(
                    gateType = "nationality",
                    requiredValue = "global",
                ),
            ),
        )
        assertEquals(
            "global, regional",
            formatCountryRequirement(
                validGate(
                    gateType = "nationality",
                    requiredValues = listOf("global", "regional"),
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

    private fun validGate(
        gateType: String,
        acceptedProviders: List<String>? = null,
        requiredValue: String? = null,
        requiredValues: List<String>? = null,
        requiredMinimumAge: Int? = null,
    ): MembershipGateSummary =
        MembershipGateSummary(
            gateType = gateType,
            acceptedProviders = acceptedProviders,
            requiredValue = requiredValue,
            requiredValues = requiredValues,
            requiredMinimumAge = requiredMinimumAge,
        )
}
